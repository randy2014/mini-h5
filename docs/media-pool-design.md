# 多媒体池子功能设计（Media Pool）· v5 定稿

> 状态：**方案已确认（2026-09）· 实施完成 P1-P8（代码已落地，待 CI/CD 部署到 VPS 端到端验收）**
> 关联：订阅频道 M1（subscribe_channel / user_subscribe 已上线），本功能复用其渠道与订阅外壳。
> 设计依据：历次需求评审结论（仅后台运营 / 无正文 / 草稿→发布→下架回草稿 / 已发布仅下架·删除仅草稿 / 发布源=订阅频道管理 / 图片不限量·视频≤1 / ffmpeg 转码 / 不保留原图 / H5 禁下载 / 后台可播放 / 双击放大不采用 / 无未订阅预览态 / 首页零改动·频道页统一网格列表混排小说+图文+视频 / Admin 与 H5 风格统一）。

## 1. 需求基线（确认稿）

### 产品范围
运营在后台创建多媒体内容（**图文帖=标题+批量图片** 或 **视频帖=标题+视频**，允许图文+视频同帖但**每帖 ≤1 视频**），创建后进入**草稿**；草稿可**挂载到订阅频道（来源=订阅频道管理中的已发布频道）**并发布，发布后进入**已发布列表**并在对应**频道详情页**展示。用户可在频道内查看图集与播放视频。

### 明确约束（历次沟通结论）
1. 上传/创建权限：仅后台运营；C 端只读。
2. **无正文字段**：内容 = 标题 + 素材（图片/视频）。标题必填。
3. 后台结构：
   - **草稿列表**：列表 + 新增（图文/视频）+ 编辑 + 删除 + 发布；
   - **已发布列表**：列表 + **仅下架**（无删除/编辑按钮）；下架后回草稿列表（保留所选频道）。
4. **删除仅草稿可用**：已发布内容必须先下架回草稿再删除；后端拒绝直接删除 PUBLISHED 内容。
5. 修改线上内容：先下架 → 回草稿 → 编辑 → 重新发布。
6. 图片：批量上传，服务端自动压缩（按比例 + 质量，成品 ≈1MB、不模糊），首图即封面；列表使用缩略图。
7. 视频：服务端 ffmpeg 统一转码 H.264（Android+iOS 通用播放）+ 自动抽封面帧；每帖 ≤1 视频。
8. 存储：全部存 VPS 自有 volume；**不保留原图**（压缩/转码成功后删源，md5 去重）。
9. H5 展示：**[订阅 tab] → [频道列表] → [具体频道]**。具体频道内聚合三类内容（频道页唯一入口，**首页不做任何改动**）：
   ① **小说**——来源：后台[文章管理]-「加入频道」（沿用现有 subscribe_channel_novel）；
   ② **图文帖**——来源：多媒体池子发布；
   ③ **视频帖**——来源：多媒体池子发布。
   频道卡片统计该频道**内容数量**（小说 + 图文 + 视频）。
10. 图集展示：图片以常规宫格展示，不做双击放大交互。
11. 访问控制：媒体字节经 App 鉴权流式下发（Range）；**仅订阅/试用用户可见，无"未订阅预览态"**（未订阅用户看不到媒体内容与字节）。
12. **后台可播放**：后台运营界面（Admin）支持视频预览/播放与图片查看（供审核/校对）。
13. **H5 禁止下载**：H5 端图片与视频**禁止用户下载/保存**（防盗措施，见 §5.3 与风险 R6）。
14. 内容生命周期：新建→草稿→发布(挂载频道)→已发布→下架(回草稿)→（编辑/删除）。

## 2. 已确认决策表

| # | 决策 | 内容 |
|---|---|---|
| D1 | 操作权限 | 仅后台运营，C 端只读 |
| D2 | 内容结构 | 标题 + 素材；无正文；类型自动判定 IMAGE（仅图）/ VIDEO（仅视频）/ MIXED（图+视频同帖，图片数量**无限制**，视频 ≤1 个）；无纯文本帖 |
| D3 | 生命周期 | DRAFT（草稿列表）→ PUBLISHED（已发布列表）；下架回 DRAFT 保留 channel_id |
| D4 | 已发布操作 | 仅下架；删除/编辑仅草稿可用（先下架再改/删） |
| D5 | 发布挂载 | 发布必选频道；频道数据源 = subscribe_channel 管理中状态 PUBLISHED 的频道；已下架频道不可选 |
| D6 | 视频处理 | ffmpeg 统一转码 H.264 Main+AAC+yuv420p+faststart，异步低并发；每帖 ≤1 视频 |
| D7 | 移动播放 | H.264 兼容档 + Range 流式 + playsinline；真机 Android/iOS（含微信）验收 |
| D8 | 访问控制 | App 鉴权流式下发；仅订阅/试用可见；无未订阅预览态 |
| D9 | 原图保留 | 不保留原图（省盘）；图片最大可见档=压缩成品（长边≤2400px） |
| D10 | 后台预览 | Admin 界面可播放视频/查看图片（运营审核与校对） |
| D11 | H5 防盗下载 | H5 图片与视频禁止用户下载/保存（浏览器端禁右键/长按/拖拽 + 服务端头，属尽力而为防君子，见 R6） |
| D12 | H5 展示位 | 首页零改动；频道详情页 = **统一内容网格/列表**（无 Tab、无分区标题）：展示挂载到该频道的全部已发布内容——小说（文章管理「加入频道」）+ 图文帖 + 视频帖，卡片带类型标识，点击进入各自详情/阅读；频道卡片统计内容数量 |
| D13 | **前后端风格统一** | Admin 与 H5 共用同一套品牌视觉 token（主色墨绿 #1f6f64 / 深色 #154f4b / 金色 #e6b422 / 浅底 #f3f5f1 / 圆角与字重体系一致），Admin 现存的灰蓝黑体系迁移到墨绿体系，杜绝两套风格 |

## 3. 概念模型

```
内容帖 media_post（池子单位：图文/视频内容，标题 + 有序素材）
  ├── 0..N 图（首图=封面）
  └── 0..1 视频
素材文件 media_asset（文件实体：成品 + 缩略图(+视频封面帧)，可被多帖引用）
挂载 → subscribe_channel（来源：订阅频道管理；仅 PUBLISHED 可挂载）
```

- `type` 自动判定：仅有图=IMAGE / 仅有视频=VIDEO / 图+视频=MIXED（无纯文本帖）。
- 封面自动：有视频→视频封面帧；无视频→首图；可手动指定 `cover_asset_id`。
- 状态仅 DRAFT / PUBLISHED；C 端只查 PUBLISHED 且频道 PUBLISHED 的内容。
- 删除草稿 = 删帖 + 解引用；素材无任何引用时物理删文件。

## 4. 数据模型

迁移文件：`sql/migrations/202609xx_media_pool.sql`（CREATE TABLE IF NOT EXISTS，幂等风格）

```sql
USE mini_novel;

CREATE TABLE IF NOT EXISTS media_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_type VARCHAR(8) NOT NULL COMMENT 'IMAGE / VIDEO',
  original_name VARCHAR(255) NOT NULL,
  md5 CHAR(32) NOT NULL,
  size_bytes BIGINT NOT NULL,
  width INT DEFAULT NULL,
  height INT DEFAULT NULL,
  duration_ms BIGINT DEFAULT NULL,
  main_path VARCHAR(512) NOT NULL COMMENT '成品(图片jpg/视频mp4)相对路径',
  thumb_path VARCHAR(512) NOT NULL COMMENT '缩略图相对路径',
  poster_path VARCHAR(512) DEFAULT NULL COMMENT '视频封面帧相对路径',
  status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/READY/FAILED',
  fail_reason VARCHAR(512) DEFAULT NULL,
  operator_id BIGINT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status),
  KEY idx_type_time (file_type, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多媒体文件实体';

CREATE TABLE IF NOT EXISTS media_post (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_id BIGINT DEFAULT NULL COMMENT '挂载频道(草稿期可空/保留)',
  title VARCHAR(120) NOT NULL COMMENT '标题(必填)',
  type VARCHAR(8) NOT NULL DEFAULT 'IMAGE' COMMENT 'IMAGE/VIDEO/MIXED(自动判定,无纯文本帖)',
  cover_asset_id BIGINT DEFAULT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
  operator_id BIGINT DEFAULT NULL,
  published_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_channel_status (channel_id, status, id),
  KEY idx_status (status, id),
  CONSTRAINT chk_media_post_status CHECK (status IN ('DRAFT','PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订阅频道多媒体内容';

CREATE TABLE IF NOT EXISTS media_post_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  seq INT NOT NULL COMMENT '素材顺序(0=封面/首素材)',
  UNIQUE KEY uk_post_seq (post_id, seq),
  KEY idx_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容帖-素材关联';
```

## 5. 处理管线

### 5.1 图片（同步）

```
接收 → 魔数校验 → 白名单 jpg/jpeg/png/gif/bmp（ImageIO 可解码格式）→ md5 查重
→ EXIF orientation(3/6/8) 转正 → 预算式压缩 → main(≤1MB预算,统一 JPEG 输出) + thumb(长边400)
→ 删临时源(原图不留) → media_asset(READY)
```
预算：quality 85 起二分 [75,92] ≤5 次；长边>2400 先降到 2400；仍超预算 质量下限 75 后按 0.9 逐级降长边(下限 1600)；最终 ≤1.5MB 视为通过并标记 oversize；透明 PNG 合成白底；输出统一为 JPEG。
> 格式说明：接受 **jpg/jpeg/png/gif/bmp**（JDK ImageIO 原生可解码范围）。**不支持 WebP**（JDK 无解码器，前端 accept 与后端校验需排除 webp，运营误选时提示转换）；GIF 动图取**首帧静态图**作为成品与缩略（动图动画本期不做，见风险 R6）。

### 5.2 视频（异步，并发 1~2）

```
接收 → ffprobe 探测 → 校验(≤15min / ≤300MB) → md5 查重 → tmp 落盘 → PROCESSING
→ 转码: libx264 -profile:v main -level 4.0 -crf 23 -preset veryfast -pix_fmt yuv420p
        -r 30 -g 60 -keyint_min 60 -sc_threshold 0 -acodec aac -b:a 128k -ac 2 -ar 44100
        -movflags +faststart；长边>1920 降至 1920；maxrate 720p≈1.5M/1080p≈3M；
        旋转元数据物理转正；剔除多余轨道/字幕
→ 抽帧: 25%~75% 时间窗等距 3 帧 → 选灰阶方差最大帧 → poster + poster 缩略
→ READY(删 tmp) / FAILED(保留 tmp + reason, 支持 reprocess)
```
> 状态机：视频上传后为 `PROCESSING`（转码中），完成后 `READY`；**未 READY 的素材不可被草稿发布引用**（发布接口校验全部素材 READY，见 §6.1）。

### 5.3 防盗下载（D11 · 尽力而为）

浏览器无法 100% 阻止保存（截图/抓包/开发者工具均可绕过），以下是行业内常规措施：

- **图片（H5）**：`<img>` 使用 CSS 处理 + JS 事件拦截：
  - `oncontextmenu` 阻止右键"图片另存为"；`draggable=false` 阻止拖拽保存；
  - CSS `user-select:none; -webkit-user-select:none; -webkit-touch-callout:none;`（禁止 iOS 长按保存/识别）；
  - 关键内容可叠透明层拦截长按/手势（按需，防误触时取舍）；
  - 服务端响应头 `Content-Disposition: inline`（明确内联而非附件）、`X-Content-Type-Options: nosniff`、`Cache-Control: private`（仅私有缓存，禁止公共/CDN 缓存扩散；与 §6.2 的 thumb 私有短缓存口径一致）。
- **视频（H5）**：
  - `<video controlslist="nodownload noremoteplayback" disablepictureinpicture>`（Android/桌面 Chrome 隐藏下载按钮）；
  - `oncontextmenu` 返回 false；屏蔽右键菜单"视频另存为"；
  - 不暴露直链：全部走鉴权流式接口（Range），URL 带登录态，直接复制 URL 到别处无法播放；
  - 不加下载按钮、不提供解析接口。水印/分段切片属高阶方案，本期不做。
- **说明边界**：以上防"顺手下载"，不防截屏录屏与专业抓包（R6）。

## 6. 接口

### 6.1 Admin（运营）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin/media/posts` | 创建草稿（title + assetIds[]有序）→ DRAFT |
| GET | `/admin/media/posts/drafts` | 草稿列表 |
| GET | `/admin/media/posts/published` | 已发布列表 |
| GET | `/admin/media/posts/{id}` | 详情（含有序素材） |
| PUT | `/admin/media/posts/{id}` | 编辑（**仅 DRAFT**；PUBLISHED 返回 400 请先下架；素材变更须全部为 READY，见下） |
| POST | `/admin/media/posts/{id}/publish` | 发布（body: channelId；校验：频道存在且 PUBLISHED、标题非空、≥1 素材、**全部素材 READY**、视频≤1） |
| POST | `/admin/media/posts/{id}/unpublish` | 下架 → 回 DRAFT（保留 channel_id/内容，可再编辑/重发） |
| DELETE | `/admin/media/posts/{id}` | 删除（**仅 DRAFT**；PUBLISHED 返回 400；解引用后素材无引用即物理删除） |
| POST | `/admin/media/assets/upload` | multipart 批量上传素材 → asset[]（图同步 READY，视频异步 PROCESSING） |
| GET | `/admin/media/assets` | 素材查询（复用/进度/去重） |
| DELETE | `/admin/media/assets/{id}` | 删除未被引用素材 |
| POST | `/admin/media/assets/{id}/reprocess` | 视频重转码/重抽封面 |
| GET | `/admin/media/channels` | 可挂载频道下拉（**订阅频道管理中 PUBLISHED 的频道**） |
| GET | `/admin/media/assets/{id}/stream` | **后台运营预览**：图片原样内联查看 / 视频播放（运营态，走 Admin 会话/鉴权，不依赖订阅） |

### 6.2 C 端
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/subscribe/channels` | 频道列表（现有）；**扩展返回频道内容统计**：`novelCount` + `mediaCount`（已发布图文+视频帖数），或合并 `contentCount`（频道卡片统计用，按用户要求展示频道内容数量） |
| GET | `/api/subscribe/channels/{id}/media?page=&pageSize=` | 频道**媒体**内容分页（仅池子发布的图文/视频帖，PUBLISHED 且频道 PUBLISHED，published_at 倒序）——供统一网格/列表中媒体部分合并渲染 |
| GET | `/api/subscribe/channels/{id}/novels?...` | （现有）频道小说分页（文章管理加入频道）——供统一网格/列表合并渲染 |
| GET | `/api/media/posts/{id}` | 媒体帖详情（标题 + 有序素材 + 素材封面/缩略）；未订阅或频道已下架 403 |
| GET | `/api/media/assets/{id}/file?kind=full\|thumb\|poster` | 鉴权流式字节（Range） |

- 访问判定统一复用 M1 的 `subscribeService.isAccessible(userId, channelId)`（含 VIP 试用期 7 天全开逻辑）：
  - 频道列表/频道页前置：沿用现有"未订阅仅看第一页"的小说逻辑不适用于媒体帖——**媒体帖无未订阅预览**，未订阅/试用结束后列表接口对媒体内容返回空或 403（由前端隐藏区块，见 §7）；`thumb/poster/full` 均需先过订阅判定。
- 流式支持 Range 206 / Content-Range / Accept-Ranges；不对视频 gzip / 压缩缓冲。
- 缓存：一律 **`Cache-Control: private`**（thumb/poster 可 `private, max-age=600`；full 无公共缓存）；full 按 asset uuid 命名不可变，帖下架后新请求 404（非 DRM 取舍）。

## 7. H5 设计
- 路径：底部「订阅」tab → 频道列表 → 具体频道 → **统一内容网格/列表**（无 Tab）→ 点卡片进对应详情（图文/视频进媒体详情、小说进书详情/阅读）。
- **首页零改动**：不新增 tab、不改首页布局；多媒体内容不出现于首页。
- 具体频道页（内容组织最终口径，2026-09 确认）：**单一内容视图**——把"发布/加入该频道的内容"混排展示：
  - 内容源合并展示：小说（`subscribe_channel_novel` 关联，文章管理加入）+ 图文帖 + 视频帖（池子发布）；
  - 呈现形式：**网格 或 列表**（沿用现有 SubscribeChannelPage 的"网格/列表"切换交互，卡片右上角/左下角显示类型标识：小说📖 / 图文🖼 / 视频▶）；
  - **不设 Tabs、不做分区标题**；排序策略见下；
  - 网格卡片=封面缩略（小说封面代理图 / 图文首图 / 视频封面帧）+ 标题；列表行=封面+标题+类型+元信息；
  - 点击行为：媒体帖进媒体详情；小说沿用现有阅读跳转与已读过滤（小说仍按现有"已读隐藏"逻辑过滤；媒体帖不做已读过滤）。
- 媒体内容列表（网格模式下的封面）：封面缩略图 + 标题 + 类型角标 + 媒体数/时长。
- 排序（C 端，已确认）：小说与媒体帖**统一时间倒序混排**——小说按加入频道时间（subscribe_channel_novel.created_at）、媒体按 published_at，混合成单一时间流。
- 订阅可见性（混排下）：媒体帖**无未订阅预览**——未订阅/试用过期用户的媒体卡片不渲染（列表直接不含媒体帖，字节接口 403，前端无特殊预览屏）；小说沿用现有 restricted 第一页预览逻辑。已订阅用户看到完整混排内容。
- 图文详情：图集宫格展示（首图放大布局），常规预览；图片按 §5.3 防盗（禁右键/长按/拖拽/缓存）。
- 视频详情：`<video controls playsinline webkit-playsinline x5-playsinline preload="metadata" :poster>`，点击才加载；**`controlslist="nodownload noremoteplayback"` + 禁用右键/画中画**（§5.3）；原生控件；@error 兜底文案。

## 8. 后台 UI

### 8.1 视觉一致性（D13 · 全局改造）

现状：H5 已有墨绿 token 体系；Admin 目前是灰蓝黑体系（侧栏 #1f2937 / Element Plus 默认蓝 / 背景 #f3f4f6），两套风格并存，需统一迁移：

- **设计 token 同源**：Admin 引入与 H5 一致的 CSS 变量：
  - 品牌主色 `--brand:#1f6f64`、深色 `--brand-strong:#154f4b`、渐变 `linear-gradient(135deg,#1f6f64,#2e8b7a)`、强调金 `--gold:#e6b422`
  - 页面底色 `#f3f5f1`、面板白、文字 `#1f2528`、次要 `#6f7978`、分隔 `#dfe7e2`（与 H5 `--page-bg/--text/--muted/--soft` 同值）
  - 圆角体系（卡片/按钮/控件）、字重体系（按钮加粗、标题 600-700）与 H5 对齐
- **Element Plus 主题覆盖**：主色 `--el-color-primary` 及 light/dark 派生（hover/active/plain/淡色背景）整体换为墨绿 #1f6f64 派生，勿残留默认蓝。
- **侧栏/登录页**：#1f2937 → 墨绿深色（#154f4b 系 + 品牌渐变点缀）；选中项高亮沿用浅绿 #2fceab 左条。
- **状态色**：图文/视频/混合标签、成功/警告/危险色与 H5 共用同一色值表。
- 影响面：admin-ui 全局样式会改变所有现有后台页面观感（Dashboard/表格/表单），属有意统一，回归时核对可读性。

### 8.2 多媒体池子页面

- `MediaPoolView`（顶部 Tab：草稿 / 已发布）：
  - 草稿：新建图文、新建视频、编辑、删除、发布（发布选频道弹窗）。
  - 已发布：按频道筛选、**仅下架**。
- 新建/编辑抽屉：标题（必填）+ 图片批量上传区（排序/删/首图=封面）+ 视频添加（1 个）。
- 发布弹窗：数据来自订阅频道管理（PUBLISHED）；已下架频道置灰不可选。
- **素材预览**：列表行/素材卡提供预览——图片弹层查看、视频 `<video controls>` 播放（后台运营不受 H5 禁下载约束，正常内联播放）；编辑器中视频素材同样可播放。

## 9. 部署改动
| 项 | 改动 |
|---|---|
| volume | app 加 `mini-novel-media:/data/media` |
| Dockerfile | `apt-get install -y ffmpeg`；`ENV MEDIA_ROOT=/data/media` |
| multipart | `max-file-size:300MB` / `max-request-size:320MB` |
| H5 nginx | `location /media/ { proxy_pass http://mini-novel-app:8080; }` |

## 10. 运维与风险
- R1 磁盘预算：单文件 ≤300MB、时长 ≤15min、每帖视频 ≤1（图片数量不限，但大图集会占用较多存储，运营按需控制并观察磁盘监控）；孤儿/tmp 7 天回收任务。
- R2 转码资源低并发、`-threads` 受限，避开高峰。
- R3 备份：媒体 volume 纳入备份（DB 备份同为已知 P2 遗留）。
- R4 审计：operator_id + updated_at 全程留痕。
- R5 **防盗下载边界**：浏览器端禁下载只防"顺手保存"，无法阻止截屏、录屏、抓包、开发者工具；付费内容安全主要依赖订阅鉴权与不暴露直链。若后续要求更强保护（水印/DRM/分段加密）需另行设计。
- R6 **GIF 动图**：动图仅保留首帧静态成品（动画能力本期不做）；运营需动图时提示转视频或静态图。
- R7 **"清晰度上限"**：因 D9 不保留原图，全站最大可见档 = 压缩成品（长边≤2400px / ≤1MB）。若运营要求印刷级原图细节（壁纸/设定集用途），需改 D9 保留原图（存储翻倍），属后续可选增强。

## 11. 任务排期（建议实施顺序）

| 批次 | 任务 | 内容 | 验收 |
|---|---|---|---|
| P1 | 数据层 | `media_pool.sql`（3 表）+ 新建 `mini-novel-media` 模块（entity/mapper）并**注册：根 pom `<modules>` 加模块、Dockerfile builder 增 `COPY mini-novel-media/pom.xml`、application 依赖链添加该模块、确认 MyBatis mapper 扫描覆盖新包** | 本机 `mvn package` 与 Dockerfile 构建链路通过；迁移幂等可重复执行 |
| P2 | 图片管线 | ImageCompressor（预算式压缩 + 缩略图 + 单元测试） | 样图压至 ≤1MB 不糊；缩略 <60KB |
| P3 | 视频管线 | ffprobe 校验 + ffmpeg 转码 + 抽帧 + 异步队列（Dockerfile 装 ffmpeg） | 手机 HEVC 源 → mp4 可播、有封面 |
| P4 | Admin 接口 | 素材上传 / 帖 CRUD / 发布 / 下架 / 删除仅草稿 / **素材 READY 校验** / 引用校验 | 接口校验覆盖（含 D4/D5/READY 约束） |
| P5 | C 端接口 | 频道内容列表 + 详情 + 鉴权流式 Range | 未订阅 403；Range 206 |
| P6 | 后台 UI | **全局视觉统一（D13：Element Plus 主色→墨绿 + 侧栏/背景/状态色对齐 H5）+ MediaPoolView（草稿/已发布）+ 编辑器 + 发布弹窗 + 素材预览（图片查看/视频播放）** | 后台与 H5 同套品牌色；后台可播放视频 |
| P7 | H5 | 频道页媒体聚合展示（按终版 UI：图文视频卡片流/分区 + 小说入口共存）+ 图文详情 + 视频详情 | 对齐终版 UI；视频 controlslist 禁下载/禁右键、图片禁长按右键拖拽；首页零改动 |
| P8 | 部署运维 | compose/nginx/multipart/ffmpeg + 孤儿回收 + 端到端验证 | 全链路 + 双端真机播放验收 |

> 注：本需求相对现有代码是新增独立模块，P1-P8 之间以 P1→P2→P3→(P4∥P6)→(P5∥P7)→P8 推进；P2/P3 可并行开发。

## 12. 验收标准
1. 后台新增图文/视频 → 草稿列表；发布（选频道）→ 已发布列表 + 频道内容列表可见；下架 → 回草稿并从 C 端消失。
2. 已发布内容无删除/编辑入口；后端拒绝删除/编辑 PUBLISHED。
3. 发布弹窗频道列表 = 订阅频道管理 PUBLISHED 频道；已下架频道不可选。
4. 首页与现有 H5 页面零改动；多媒体内容仅经 [订阅]→[具体频道] 展示；频道详情为统一内容网格/列表（小说📖+图文🖼+视频▶ 混排，无 Tab/分区标题），点卡片进各自详情。
5. 图片 ≤1M(±20%) 不糊；视频 H.264 双端（iOS Safari/微信、Android Chrome/X5）可播放、可拖进度、竖拍方向正确。
6. 同帖视频 ≤1、图片数量不限；同文件二次上传 md5 复用；删除草稿无孤儿残留。
7. **后台可播放**：运营在后台草稿/已发布/编辑器均可直接播放视频、查看图片。
8. **H5 禁下载**：iOS/Android 浏览器上长按/右键/拖拽无法保存图片与视频；视频无下载按钮、无画中画、右键菜单被禁；媒体 URL 无登录态不可播。
9. **风格统一（D13）**：后台所有页面与 H5 主色/底色/按钮/标签/侧栏同一品牌色系，无残留 Element Plus 默认蓝与灰蓝黑侧栏。
10. **素材就绪约束**：视频转码未完成（PROCESSING）或失败（FAILED）时，含该素材的帖不可发布/不可进入已发布；转码完成后可正常发布。
11. **未订阅/试用过期**：频道媒体区不渲染、列表为空、媒体字节 403；试用期内与已订阅一致。

## 13. 实施记录（2026-09 代码落地）

**后端（已编译 + 单测通过）**
- 新模块 `mini-novel-media`：3 entity/mapper + `MediaFileStorage`（volume 路径/防穿越）+ `ImageCompressor`（预算压缩/缩略/EXIF，9 测）+ `VideoProcessor`（ffprobe/ffmpeg 命令）+ `MediaExecutorConfig` + `MediaStreamer`（Range 206 内联流）+ `MediaConfig`；`MediaPostServiceImpl`（草稿/发布状态机/引用清理/READY 校验）。
- Admin 接口：`AdminMediaAssetController`（multipart 上传/列表/删除/reprocess/流式预览）+ `AdminMediaPostController`（帖 CRUD/发布/下架/channels 下拉）。
- C 端：`SubscribeController./channels/{id}/feed`（小说+媒体统一时间流，restricted 语义）+ `SubscribeMediaController`（媒体分页/帖详情/鉴权流式，full 需订阅+素材属于该频道已发布帖）+ `AuthController` 登录同步种同名 Cookie（<img>/<video> 无头鉴权）。
- `SubscribeChannelVo.mediaCount`（频道卡片统计）。
- 表迁移 `sql/migrations/20260915_media_pool.sql`；deploy.sh 已登记；compose 加 `mini-novel-media` volume；Dockerfile 装 ffmpeg；application.yml multipart ≤300MB；admin-ui nginx `client_max_body_size 320m`。

**前端（vite build 通过）**
- Admin `MediaPoolView.vue`（草稿/已发布/编辑器/发布弹窗/后台视频播放）；Admin 全局墨绿主题（styles.css token + Element Plus 主色 + 侧栏/登录页）+ 菜单/路由。
- H5：`SubscribePage` 频道卡片统计内容数；`SubscribeChannelPage` 重写为统一网格/列表混排（小说+图文+视频，**已读隐藏仅小说且响应式即时移除**：点击小说 markSubscribeRead → 从列表消失 + 计数，封面加载失败自动回退品牌色块占位，媒体封面无 cookie 会话时同样回退不裂图）；新增 `SubscribeMediaPostPage`（图集 + 视频播放 + 禁下载：controlslist 禁下载/禁画中画/禁右键/禁长按/禁拖拽）。

**待 VPS 部署后验收（P8 剩余项）**：CI/CD 部署 → 真机双端播放/禁下载 → 上传→发布→H5 播放→下架→越权 403 全链路核对（§12 验收 1-11）。
