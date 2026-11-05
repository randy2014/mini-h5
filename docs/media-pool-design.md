# 多媒体池子功能设计（Media Pool）· v5 定稿

> 状态：**已实施并上线**（P1–P8 代码落地；2026-09-10 完成生产部署与端到端验收，最新代码提交 `1e0f693`）
> 文档地图：[`README.md`](README.md) ｜ 需求口径见 [`media-pool-requirements.md`](media-pool-requirements.md)
> 历史说明：本文提到的 `sql/migrations/20260915_media_pool.sql`、`20260916_...` 已在 2026-09 合并进唯一的
> `sql/schema.sql`，文件本身已删除；结构内容完整保留在该脚本 §2/§3 中。
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
   ① **小说**——来源：后台[VIP文章管理]-「加入频道」（沿用现有 subscribe_channel_novel）；
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
| D2 | 内容结构 | 标题 + 素材；无正文；类型自动判定 IMAGE（仅图）/ VIDEO（仅视频）/ MIXED（图+视频同帖，**图片最多 100 张（v1.8 设上限，前端拦截）**，**视频不限量**（v1.3 解除每帖 1 个），封面取第 1 个视频）；无纯文本帖 |
| D3 | 生命周期 | DRAFT（草稿列表）→ PUBLISHED（已发布列表）；下架回 DRAFT 保留 channel_id |
| D4 | 已发布操作 | 仅下架；删除/编辑仅草稿可用（先下架再改/删） |
| D5 | 发布挂载 | 发布必选频道；频道数据源 = subscribe_channel 管理中状态 PUBLISHED 的频道；已下架频道不可选 |
| D6 | 视频处理 | ffmpeg 统一转码 H.264 Main+AAC+yuv420p+faststart，异步低并发（v1.3 起一帖可挂多个视频，逐个排队转码） |
| D7 | 移动播放 | H.264 兼容档 + Range 流式 + playsinline；真机 Android/iOS（含微信）验收 |
| D8 | 访问控制 | App 鉴权流式下发；仅订阅/试用可见；无未订阅预览态 |
| D9 | 原图保留 | 不保留原图（省盘）；图片最大可见档=压缩成品（长边≤2400px） |
| D10 | 后台预览 | Admin 界面可播放视频/查看图片（运营审核与校对） |
| D11 | H5 防盗下载 | H5 图片与视频禁止用户下载/保存（浏览器端禁右键/长按/拖拽 + 服务端头，属尽力而为防君子，见 R6） |
| D12 | H5 展示位 | 首页零改动；频道详情页 = **统一内容网格/列表**（无 Tab、无分区标题）：展示挂载到该频道的全部已发布内容——小说（VIP文章管理「加入频道」）+ 图文帖 + 视频帖，卡片带类型标识，点击进入各自详情/阅读；频道卡片统计内容数量 |
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
> 状态机：视频上传后为 `PROCESSING`（转码中），完成后 `READY`；**草稿可以先挂上 PROCESSING/FAILED 素材**
> （草稿是工作区，避免大视频转码几分钟内什么都存不了），但**发布**要求全部素材 `READY`（见 §6.1）。

### 5.3 防盗下载（D11 · 尽力而为）

浏览器无法 100% 阻止保存（截图/抓包/开发者工具均可绕过），以下是行业内常规措施：

- **图片（H5）**：`<img>` 使用 CSS 处理 + JS 事件拦截：
  - `oncontextmenu` 阻止右键"图片另存为"；`draggable=false` 阻止拖拽保存；
  - CSS `user-select:none; -webkit-user-select:none; -webkit-touch-callout:none;`（禁止 iOS 长按保存/识别）；
  - 关键内容可叠透明层拦截长按/手势（按需，防误触时取舍）；
  - 服务端响应头 `Content-Disposition: inline`（明确内联而非附件）、`X-Content-Type-Options: nosniff`、`Cache-Control: private`（仅私有缓存，禁止公共/CDN 缓存扩散；与 §6.2 的 thumb 私有短缓存口径一致）。
- **视频（H5）**：
  - **v1.9 起改用自定义控件播放器**（`src/components/MediaVideoPlayer.vue`）：**不渲染原生 `controls`**，因此下载 / 分享 / AirPlay / 画中画入口在页面层面不存在；另保留 `x-webkit-airplay="deny"`、`disableRemotePlayback`、`disablepictureinpicture`、`controlslist="nodownload noplaybackrate noremoteplayback"` 作为兜底；自定义控制条只做播放/暂停、进度、时间、全屏（仅容器全屏 API 可用时显示）；
  - 旧方案 `<video controls controlslist="nodownload noremoteplayback" disablepictureinpicture>` 只在 Chromium 生效，iOS Safari / 微信 X5 仍会带出原生菜单，故弃用；
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
| POST | `/admin/media/assets/upload` | multipart 批量上传素材 → asset[]（图同步 READY，视频异步 PROCESSING）；**保留兼容**，前端已改走下面的流式接口 |
| POST | `/admin/media/assets/upload-stream` | **流式上传（前端默认）**：`Content-Type: application/octet-stream` + `?fileName=`，body=文件原始字节，单文件一次；直写媒体卷 tmp，不经 multipart（省掉容器 /tmp 的重复落盘），上限 300MB |
| GET | `/admin/media/assets` | 素材查询（复用/进度/去重） |
| GET | `/admin/media/assets/by-md5` | **上传前查重（v1.5）**：`?md5=` → 命中返回 READY 素材（JSON），未命中 `data=null`；前端据此跳过整包上传 |
| DELETE | `/admin/media/assets/{id}` | 删除未被引用素材 |
| POST | `/admin/media/assets/{id}/reprocess` | 视频重转码/重抽封面 |
| GET | `/admin/media/channels` | 可挂载频道下拉（**订阅频道管理中 PUBLISHED 的频道**） |
| GET | `/admin/media/assets/{id}/stream` | **后台运营预览**：图片原样内联查看 / 视频播放（运营态，走 Admin 会话/鉴权，不依赖订阅） |

> **v1.1 目录批量导入（FR-45）不新增后端接口**：前端逐条调用 `upload-stream`（每个视频一次）+ `POST /admin/media/posts`（每个视频建一条草稿，`assetIds=[该素材]`），失败项单独重试；上传成功但建帖失败时调 `DELETE /admin/media/assets/{id}` 回收素材。理由：视频单文件 ≤300MB 且转码低并发（core 1 / max 2 / queue 50，`CallerRunsPolicy` 兜底），逐条串行对带宽/磁盘/转码队列最友好，且天然支持「部分成功」。
> **v1.2 视频上传后自动存草稿（FR-46）同样不新增接口**：复用 `POST /admin/media/posts` / `PUT /admin/media/posts/{id}`，草稿阶段允许挂 PROCESSING 素材（发布才强制 READY）。
> **v1.3 一帖多视频（FR-40/FR-47）仍不新增接口**：`assetIds` 顺序即 seq，前端把多个视频排在数组前面，后端 `syncTypeAndCover` 取第 1 个视频作封面；**唯一后端改动是删掉发布时 `videoCount > 1` 的拒绝**（`MediaPostServiceImpl.publish`），其余校验（素材全部 READY、标题必填、≥1 素材）不变。
> **v1.4 批量发布（FR-48）也不新增接口**：前端对勾选的每条草稿调用现成 `POST /admin/media/posts/{id}/publish`（同一个 channelId），逐条成败、互不影响；这样做不需要 post-channel 关联表，且沿用既有发布校验。批量逻辑抽在 `utils/mediaPublish.js`（`publishDraftsSequentially` / `splitPublishable`）并有单测。
> **v1.5 上传前查重（FR-49）**：新增只读接口 `GET /admin/media/assets/by-md5?md5=`（`MediaPostService.findAssetByMd5`，只认 READY、多条取最新），前端 `utils/fileFingerprint.js`（md5 + localStorage 缓存）→ `utils/uploadDedupe.js`（查重，失败回退正常上传）。服务端原有 md5 去重（上传后命中 READY 直接复用记录）保留不变，两者互补：前者省传输，后者保证存储不重复。
> **v1.8 图片目录导入 + 单帖 100 张上限（FR-51）不新增后端接口**：图片走同一个 `upload-stream`（后端按扩展名识别为图片并同步压缩），查重同样用 `by-md5`；「100 张」是**前端拦截**（`MAX_IMAGES_PER_POST`，手工上传与目录导入同口径），后端不加校验、不改表。

### 6.2 C 端
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/subscribe/channels` | 频道列表（现有）；**扩展返回频道内容统计**：`novelCount` + `mediaCount`（已发布图文+视频帖数），或合并 `contentCount`（频道卡片统计用，按用户要求展示频道内容数量）；**v1.6 新增 `todayMediaCount`**（当天已发布媒体帖数，>0 时 H5 显示「今日更新」角标） |
| GET | `/api/subscribe/channels/{id}/media?page=&pageSize=` | 频道**媒体**内容分页（仅池子发布的图文/视频帖，PUBLISHED 且频道 PUBLISHED，published_at 倒序）——供统一网格/列表中媒体部分合并渲染 |
| GET | `/api/subscribe/channels/{id}/novels?...` | （现有）频道小说分页（VIP文章管理加入频道）——供统一网格/列表合并渲染 |
| GET | `/api/media/posts/{id}` | 媒体帖详情（标题 + 有序素材 + 素材封面/缩略）；未订阅或频道已下架 403。**实际返回形状是 `{ post:{...}, assets:[...], cover }`（v1.7 修正：前端必须归一化，否则页面取 `detail.title/assets` 全为空）** |
| GET | `/api/media/assets/{id}/file?kind=full\|thumb\|poster` | 鉴权流式字节（Range） |

- 访问判定统一复用 M1 的 `subscribeService.isAccessible(userId, channelId)`（含 VIP 试用期 7 天全开逻辑）：
  - 频道列表/频道页前置：沿用现有"未订阅仅看第一页"的小说逻辑不适用于媒体帖——**媒体帖无未订阅预览**，未订阅/试用结束后列表接口对媒体内容返回空或 403（由前端隐藏区块，见 §7）；`thumb/poster/full` 均需先过订阅判定。
- 流式支持 Range 206 / Content-Range / Accept-Ranges；不对视频 gzip / 压缩缓冲。
- 缓存：一律 **`Cache-Control: private`**（thumb/poster 可 `private, max-age=600`；full 无公共缓存）；full 按 asset uuid 命名不可变，帖下架后新请求 404（非 DRM 取舍）。

## 7. H5 设计
- 路径：底部「订阅」tab → 频道列表 → 具体频道 → **统一内容网格/列表**（无 Tab）→ 点卡片进对应详情（图文/视频进媒体详情、小说进书详情/阅读）。
- **首页零改动**：不新增 tab、不改首页布局；多媒体内容不出现于首页。
- 具体频道页（内容组织最终口径，2026-09 确认）：**单一内容视图**——把"发布/加入该频道的内容"混排展示：
  - 内容源合并展示：小说（`subscribe_channel_novel` 关联，VIP文章管理加入）+ 图文帖 + 视频帖（池子发布）；
  - 呈现形式：**网格 或 列表**（沿用现有 SubscribeChannelPage 的"网格/列表"切换交互，卡片右上角/左下角显示类型标识：小说📖 / 图文🖼 / 视频▶）；
  - **不设 Tabs、不做分区标题**；排序策略见下；
  - 网格卡片=封面缩略（小说封面代理图 / 图文首图 / 视频封面帧）+ 标题；列表行=封面+标题+类型+元信息；
  - 点击行为：媒体帖进媒体详情；小说沿用现有阅读跳转与已读过滤（小说仍按现有"已读隐藏"逻辑过滤；媒体帖不做已读过滤）。
- 媒体内容列表（网格模式下的封面）：封面缩略图 + 标题 + 类型角标 + 媒体数（**v1.6 起不显示视频时长**）。
- 排序（C 端，已确认）：小说与媒体帖**统一时间倒序混排**——小说按加入频道时间（subscribe_channel_novel.created_at）、媒体按 published_at，混合成单一时间流。
  - **v1.6 修「乱序」**：`media_post.published_at` 是 `DATETIME`（秒精度），批量发布一次会把整批帖压在同一秒；同一秒内 SQL 返回顺序不稳定 → 列表看着是乱的。修法是 **SQL 与 Java 排序都加 `id` 倒序兜底**（`ChannelFeedServiceImpl.feed` 的 `thenComparing(id, reverseOrder())` + `collectMedia` 的 `orderByDesc(id)`；`pagePublished/pageChannelPublished` 早已有 id 兜底）。时间口径不变。
- **今日更新角标（v1.6）**：`GET /api/subscribe/channels` 返回 `todayMediaCount`（当天 00:00 起该频道 `published_at` 的 PUBLISHED 帖数，一次聚合查询避免 N+1）；H5 频道卡片 `todayMediaCount > 0` 时显示「今日更新」红标 + 「今日新增 N 条」。
- 订阅可见性（混排下）：媒体帖**无未订阅预览**——未订阅/试用过期用户的媒体卡片不渲染（列表直接不含媒体帖，字节接口 403，前端无特殊预览屏）；小说沿用现有 restricted 第一页预览逻辑。已订阅用户看到完整混排内容。
- 图文详情：图集宫格展示（首图放大布局），常规预览；图片按 §5.3 防盗（禁右键/长按/拖拽/缓存）。
- 视频详情（v1.3 多视频 / v1.6 标题 / v1.9 自定义播放器）：按 seq 顺序**竖向排列**多个视频；每个先显示封面帧占位（带角标「视频 i/N」），**点击才挂 src 播放**（`preload="none"`，避免多视频同时拉流）；某视频开始播放时自动暂停其余（同屏只播一个，组件暴露 `pause()`）；播放器为**自定义控件**（`MediaVideoPlayer.vue`）：封面 + 播放按钮 → 播放中点击画面暂停/继续，底部控制条含播放/暂停、进度拖动、时间、全屏（仅容器全屏可用时）；无原生控件 → 无下载/分享/AirPlay/画中画入口；@error 显示「视频加载失败」并可点击重试；禁用右键/长按/拖拽（§5.3）。
  - **导航标题**：视频类（VIDEO/MIXED）显示内容标题（`utils/mediaDetail.detailNavTitle`），纯图文仍显示「图文详情」；视频类正文不再重复大标题（`shouldShowBodyTitle`）。
  - **数据归一化（v1.7 修复）**：详情接口返回 `{post, assets, cover}`，页面按扁平结构读字段会全取不到（只剩「图文详情」、正文空白）。现在接口层 `normalizeMediaDetail()` 拍平（兼容两种形状），页面标题与素材恢复正常。
  - **标题动态缓存（v1.7）**：从频道详情点卡片前，把列表里的 `{title, type}` 写入 sessionStorage（`utils/mediaMetaCache.js`，最多 200 条）；详情接口返回前导航栏先用缓存标题，因此**不会先闪「图文详情」再变成视频标题**。

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
  - 草稿：新建图文、新建视频、编辑、删除、发布（发布选频道弹窗）、**多选 + 批量发布到同一个频道（v1.4）**。
  - 已发布：按频道筛选、**仅下架**。
- **批量发布（v1.4 · FR-48）**：
  - 草稿列表首列 `type="selection"`（`row-key=id`），换页/换 Tab/刷新后 `clearSelection()`，避免对着旧数据发布；
  - 顶部「📌 批量发布（N）」在无勾选时置灰；弹窗展示「已勾选 N / 可发布 M / 跳过 K」+ 频道单选；
  - `splitPublishable()` 先按 `row._notReady > 0` 拆出可发布与跳过（转码中/失败），未知状态交给后端校验；
  - `publishDraftsSequentially()` 逐条调发布接口，`onItemChange` 更新进度文案；单条失败记入 failures 并在弹窗里逐条列出，不影响其余；
  - 全成功 → 关闭弹窗并跳到「已发布列表」；有失败 → 留在弹窗展示失败原因（草稿列表已刷新）。
- 新建/编辑抽屉：标题（必填）+ **图片区（批量上传 + v1.8「📁 选择目录上传」，单帖最多 100 张，超出自动跳过）** + **视频区（可多个：列表展示封面帧/文件名/状态，支持上移/下移/移除/重新转码，第 1 个为封面）**。
- 发布弹窗：数据来自订阅频道管理（PUBLISHED）；已下架频道置灰不可选。
- **素材预览**：列表行/素材卡提供预览——图片弹层查看、视频 `<video controls>` 播放（后台运营不受 H5 禁下载约束，正常内联播放）；编辑器中视频素材同样可播放。
- **一帖多视频（v1.3 · FR-40/47）**：`videos` 数组即 seq 顺序，`allAssets = [...videos, ...images]`；后端封面规则「有视频取第 1 个视频」天然复用；**移除/上移/下移都会自动落草稿**（避免界面顺序变了但草稿没变）。
- **上传前查重、重复文件跳过（v1.5 · FR-49）**：「添加视频」与目录导入都会先算文件 md5（4MB 分块 + 进度）→ `GET /media/assets/by-md5`：
  - 命中 READY **视频** → 直接把已有素材挂进 `videos`，**跳过整包上传**，提示「《xxx》已在素材库中，跳过重复上传」；命中素材**不记入 `sessionUploaded`**（那是本次新上传、关闭时要回收的），避免误删别人的素材；
  - 目录导入里该行状态显示「校验中 x%」→「已存在 · 跳过上传」，整批结束统计「其中 N 个已在素材库（跳过上传）」；
  - 查重是优化不是前置条件：算哈希失败、请求失败、未命中，一律静默回退到正常上传（服务端 md5 去重仍兜底）。
  - 实现：`utils/md5.js`（增量 MD5，单测对齐 node crypto）+ `utils/fileFingerprint.js`（md5 + localStorage 缓存）+ `utils/uploadDedupe.js`（按 md5 查后端）。
- **视频上传后自动存草稿（v1.2 · FR-46，v1.3 扩展为每次视频变更都自动保存）**：编辑器里「添加视频」上传成功即自动落草稿，不必再点「保存草稿」。
  - 新建态 → `POST /admin/media/posts`；已有草稿/编辑态 → `PUT /admin/media/posts/{id}`（不会重复建帖）；自动保存走 `saveQueue` 串行排队，避免并发写同一条帖；
  - 标题口径：单视频 `resolveDraftTitle(已填标题, 文件名)`；目录导入 `resolveGroupTitle(已填标题, 目录名)`（目录名不截扩展名）；
  - 不等转码：草稿允许挂 PROCESSING 素材（§6.1 PUT/发布约束不变），保存后抽屉切「编辑内容（已自动保存草稿）」；
  - 保存成功后 `savedThisSession=true`：关闭抽屉不再弹「转码中会丢弃视频」确认，轮询保留到转码结束再刷新列表；
  - 素材回收口径：按 `persistedIds`（已被某次保存写进帖子的素材）过滤，自动保存之后再传的图片若未保存仍会在关闭时回收，避免孤儿；
  - 自动保存失败（网络/校验）不吞素材：提示「请点保存草稿重试」，编辑器状态保持不变。

### 8.3 视频目录批量导入（v1.1，v1.3 改为并入当前内容 · FR-45）

入口：「新建视频/新建图文/编辑内容」抽屉的视频区 → **「📁 选择目录批量导入」**。

- 目录选择用原生 `<input type="file" webkitdirectory multiple>`：`el-upload` 不透传 `webkitdirectory`，且目录选择必须在浏览器端才有相对路径信息；
- 只保留对应类型（视频白名单同后端 `VIDEO_EXT`；图片 jpg/png/gif/bmp，**webp 不支持**），其它文件忽略并计数；视频单文件 >300MB 直接标「跳过」；
- **图片上限（v1.8）**：单帖最多 **100 张**（`MAX_IMAGES_PER_POST`），传 `maxCount=剩余名额` 给弹窗，超出部分标「超过单帖上限」跳过；手工逐张/多选上传同样按此拦截；
  - 视频目录导入**不设张数上限**：`maxCount` 传 null/不传。**坑（已修）**：不能用 `Number.isFinite(Number(v))` 判断——`Number(null) === 0`，会把视频全部误判成「超过上限（最多 0 张）」。统一走 `coerceMaxCount()`：null/''/非数字 → 不限，数字才截断（有回归单测）。
- **v1.3：目录内视频全部加入当前这条内容**（不是一条内容一个视频，也不是每条视频建一条帖）：按相对路径**自然排序**（第2集在第10集前），顺序即内容内 seq，**第 1 个为封面**；标题已填则沿用、留空取**目录名**；
- 弹窗内表格逐行展示 **序号 / 文件相对路径 / 大小 / 状态（待上传→上传中 x%→已加入·转码中/失败原因）**，底部总进度、**停止**（当前文件传完即停）与**重试失败项**；
- 每传成功一个就 `emit('uploaded')` 给父级挂进 `videos` 并开始轮询；整批结束 `emit('finished')` → 父级自动保存一次草稿（`persistDraft(resolveGroupTitle(...))`）；
- 单条失败隔离：失败项单独标记可重试，成功项照常入内容；
- 实现拆分：`utils/media.js`（扩展名/标题/体积/清单排序）+ `utils/mediaImport.js`（逐条上传流程，依赖注入便于单测）+ `components/VideoDirectoryImportDialog.vue`（弹窗 UI，只负责上传与进度）。

## 9. 部署改动
| 项 | 改动 |
|---|---|
| volume | app 加 `mini-novel-media:/data/media` |
| Dockerfile | `apt-get install -y ffmpeg`；`ENV MEDIA_ROOT=/data/media` |
| multipart | `max-file-size:300MB` / `max-request-size:320MB` |
| H5 nginx | `location /media/ { proxy_pass http://mini-novel-app:8080; }` |

## 10. 运维与风险
- R1 磁盘预算：单文件 ≤300MB、时长 ≤15min、**一帖视频数量不限（v1.3）**（图片同样不限，但大图集/多集会占用较多存储，运营按需控制并观察磁盘监控）；孤儿/tmp 7 天回收任务。
- R2 转码资源低并发、`-threads` 受限，避开高峰。
- R3 备份：媒体 volume 纳入备份（DB 备份同为已知 P2 遗留）。
- R4 审计：operator_id + updated_at 全程留痕。
- R5 **防盗下载边界**：浏览器端禁下载只防"顺手保存"，无法阻止截屏、录屏、抓包、开发者工具；付费内容安全主要依赖订阅鉴权与不暴露直链。若后续要求更强保护（水印/DRM/分段加密）需另行设计。
- R6 **GIF 动图**：动图仅保留首帧静态成品（动画能力本期不做）；运营需动图时提示转视频或静态图。
- R7 **"清晰度上限"**：因 D9 不保留原图，全站最大可见档 = 压缩成品（长边≤2400px / ≤1MB）。若运营要求印刷级原图细节（壁纸/设定集用途），需改 D9 保留原图（存储翻倍），属后续可选增强。
- R8 **目录批量导入的转码排队（v1.1）**：一次导入 N 个视频会即时入队 N 个转码任务（queue 50，超出后 `CallerRunsPolicy` 在请求线程执行，不会丢任务但会拖慢该次请求）。表现为「草稿早已生成、转码陆续完成」，发布前必须等 READY（FR-39 既有约束）。首次使用建议先导 10-20 个观察转码耗时，再决定批量规模；避免一次性导入上千条。
- R10 **图片张数上限（v1.8）**：单帖最多 100 张是**前端约束**（防止目录误选整盘照片把帖子和页面撑爆），后端仍不限制；若绕过前端直接调接口仍可超限。
- R9 **前端 md5 校验成本（v1.5）**：上传前要算整文件 md5（浏览器没有 WebCrypto MD5，DB 也只存 md5）。47MB 视频在正常 PC 上约 1s；低功耗/被降频的机器可能数秒（本仓库开发机实测 JS MD5 仅 ≈5MB/s，属异常慢环境）。对策：md5 结果按「文件名+大小+修改时间」缓存进 localStorage，重复导入同一目录不再重算；校验失败/超时静默回退正常上传。若后续要更快，可改用浏览器 SHA-256 + 服务端新增 `sha256` 列（需 DB 迁移与历史素材回填），本期不做。

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
6. 图片与视频数量均不限；**一帖多视频时封面取第 1 个视频**，顺序可上下调整；同文件二次上传 md5 复用；删除草稿无孤儿残留。
7. **后台可播放**：运营在后台草稿/已发布/编辑器均可直接播放视频、查看图片。
8. **H5 禁下载**：iOS/Android 浏览器上长按/右键/拖拽无法保存图片与视频；视频无下载按钮、无画中画、右键菜单被禁；媒体 URL 无登录态不可播。
9. **风格统一（D13）**：后台所有页面与 H5 主色/底色/按钮/标签/侧栏同一品牌色系，无残留 Element Plus 默认蓝与灰蓝黑侧栏。
10. **素材就绪约束**：视频转码未完成（PROCESSING）或失败（FAILED）时，含该素材的帖**可存草稿但不可发布**；
    草稿列表会标「转码中」并禁用发布按钮，转码完成后可正常发布。
11. **未订阅/试用过期**：频道媒体区不渲染、列表为空、媒体字节 403；试用期内与已订阅一致。
12. **批量发布（v1.4）**：勾选 N 条草稿 → 选一个频道 → 逐条发布；素材未就绪自动跳过并提示；单条失败不影响其余且失败原因可见；成功后勾选清空、列表刷新（全成功跳已发布列表）。
13. **上传前查重（v1.5）**：同一视频再次「添加视频」/ 同一目录再次导入 → 状态显示「已存在 · 跳过上传」，不再产生上传流量与重复素材；草稿里的视频列表不因重复导入而翻倍；查重不可用时仍能正常上传。
14. **H5 列表与详情（v1.6）**：频道详情顺序稳定按发布时间倒序（批量发布同秒内新帖在前）；频道列表不显示视频时长；视频类详情页导航标题=内容标题（纯图文=图文详情）；当天有新发布媒体帖的频道，订阅频道列表显示「今日更新」角标。
15. **H5 详情页（v1.7）**：进入详情页导航栏**立即**显示视频标题（用列表缓存标题，不闪「图文详情」）；正文图片/视频正常渲染（`{post,assets,cover}` 已归一化）。
16. **图片目录导入（v1.8）**：新建图文选一个图片目录 → 图片全部加入（自然排序、第 1 张封面、webp 忽略）；超过 100 张的部分自动跳过并提示；导入完成自动存草稿；重复导入不重复上传。
17. **H5 播放器禁下载/禁分享（v1.9）**：视频用自定义控件播放，页面/控件里不存在下载、分享、AirPlay、画中画入口；长按/右键/拖拽无法保存视频；多视频仍同屏只播一个；播放失败可点击重试。

## 13. 实施记录（2026-09 代码落地）

**后端（已编译 + 单测通过）**
- 新模块 `mini-novel-media`：3 entity/mapper + `MediaFileStorage`（volume 路径/防穿越）+ `ImageCompressor`（预算压缩/缩略/EXIF，9 测）+ `VideoProcessor`（ffprobe/ffmpeg 命令）+ `MediaExecutorConfig` + `MediaStreamer`（Range 206 内联流）+ `MediaConfig`；`MediaPostServiceImpl`（草稿/发布状态机/引用清理/READY 校验）。
- Admin 接口：`AdminMediaAssetController`（**流式上传**/multipart 兼容上传/列表/删除/reprocess/流式预览）+ `AdminMediaPostController`（帖 CRUD/发布/下架/channels 下拉）。
- C 端：`SubscribeController./channels/{id}/feed`（小说+媒体统一时间流，restricted 语义）+ `SubscribeMediaController`（媒体分页/帖详情/鉴权流式，full 需订阅+素材属于该频道已发布帖）+ `AuthController` 登录同步种同名 Cookie（<img>/<video> 无头鉴权）。
- `SubscribeChannelVo.mediaCount`（频道卡片统计）。
- 表迁移 `sql/migrations/20260915_media_pool.sql`；deploy.sh 已登记；compose 加 `mini-novel-media` volume；Dockerfile 装 ffmpeg；application.yml multipart ≤300MB；admin-ui nginx `client_max_body_size 320m`。

**前端（vite build 通过）**
- Admin `MediaPoolView.vue`（草稿/已发布/编辑器/发布弹窗/后台视频播放）；Admin 全局墨绿主题（styles.css token + Element Plus 主色 + 侧栏/登录页）+ 菜单/路由。
- H5：`SubscribePage` 频道卡片统计内容数；`SubscribeChannelPage` 重写为统一网格/列表混排（小说+图文+视频，**已读隐藏仅小说且响应式即时移除**：点击小说 markSubscribeRead → 从列表消失 + 计数，封面加载失败自动回退品牌色块占位，媒体封面无 cookie 会话时同样回退不裂图）；新增 `SubscribeMediaPostPage`（图集 + 视频播放 + 禁下载：controlslist 禁下载/禁画中画/禁右键/禁长按/禁拖拽）。

**生产部署与端到端验收（2026-09-10，VPS 64.90.19.6 / 部署版本 8c3ead1）**

部署链路：push main → GitHub Actions（rsync + deploy.sh）→ 全部容器重建成功，三端 HTTP 200；迁移 `20260915`（建表）与 `20260916`（列修正）均已执行。

上线路程中修复的 4 个问题（均已提交并验证）：
| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | 部署后后端 502、启动崩溃 | `mediaProcessExecutor`（Executor 类型）抢占 Spring `@ConditionalOnMissingBean(Executor.class)`，默认 `applicationTaskExecutor` 不再创建，crawler 模块 `@Qualifier("applicationTaskExecutor")` 装配失败 | 显式定义同名 `applicationTaskExecutor` bean（commit 3ddd9f3） |
| 2 | Docker 构建受网络影响中断 | VPS 拉取 ffmpeg 不稳 | ffmpeg 安装改为失败不阻塞构建（视频降级、图片/后端不受影响）（commit 70d9232） |
| 3 | 后台新建视频报 `Field 'main_path' doesn't have a default value` | 视频 `registerVideo` 阶段为 PROCESSING 尚无成品路径，而 `main_path/thumb_path` 定义为 NOT NULL | 迁移 `20260916` 改为可空 + 视频 md5 改流式计算（commit 39f66ed） |
| 4 | 媒体图片裂图 | 自定义扩展名 `.img/.thumb/.poster` 未被识别 → 响应 `application/octet-stream`，叠加 `nosniff` 后浏览器拒渲染 | `MediaStreamer` 按扩展名返回 `image/jpeg`（commit 8c3ead1） |

端到端验收结果（生产实测）：
- ✅ 图片上传：800×600 PNG → 压缩 READY，main/thumb 落盘（`images/202609/*.img|.thumb`）
- ✅ 视频上传+转码：3s/640×360 源 → **25s 内 READY**，产物 **h264 Main / yuv420p / mp4**，自动生成 poster 帧与缩略图
- ✅ 媒体字节：图片 `image/jpeg`、视频 `video/mp4`、封面 `image/jpeg`；**Range 请求返回 206 + Content-Range**（双端拖动可用）
- ✅ 草稿→发布→挂载频道：post 落库 PUBLISHED、channel_id 正确、封面自动取首图/视频帧
- ✅ 规则校验：**已发布直接删除被拒绝**（"仅草稿可操作：已发布内容请先下架回到草稿"）；下架回草稿保留频道；删除草稿成功且**素材记录与文件同步回收（无孤儿）**
- ✅ C 端 feed：登录（邀请码自动 VIP）后频道 feed 返回**统一混排**（IMAGE 帖 + NOVEL 小说同一时间流），`restricted=false`
- ✅ 鉴权：未登录访问 feed / thumb / full 均 **401**；媒体字节需登录+订阅
- ✅ 前端部署：H5 含 `SubscribeMediaPostPage` chunk、Admin 含 `MediaPoolView` chunk；小说封面代理 `/api/cover/{id}` 正常（200 image/jpeg）
- ⏳ 待真机验收（需人工设备）：iOS Safari / Android Chrome / 微信内播放与拖动、禁下载（长按/右键/画中画）实际表现、页面视觉与交互

> 测试数据说明：验收过程中在频道 1 发布了一条 **"E2E测试图文"**（1 图）用于链路验证，测试账号、邀请码配额与测试内容均已清理；当前生产仅剩运营自有的视频素材与草稿。

**磁盘文件同步回收（2026-09-10 增强，commit 1e0f693）**

需求：删除内容时，磁盘上的图片/视频必须同步删除，不允许残留占盘。已实现四条清理路径：

| 触发场景 | 行为 |
|---|---|
| **草稿列表删除** | 解引用 → 无任何帖引用的素材删除 DB 记录 + 磁盘文件（main/thumb/poster） |
| **编辑草稿移除素材** | `replaceAssets` 记录旧关联，保存后释放不再被引用的素材（含文件） |
| **新建内容取消编辑 / 移除已选素材** | 前端立即调用素材删除接口回收未采用素材（后端对仍被引用的素材拒绝删除，安全） |
| **兜底（异常中断/漏清理）** | `MediaCleanupJob` 每日 03:30：清理孤儿素材（>24h 无引用）、tmp 中转文件（>24h）、卡死 PROCESSING（>2h 标记 FAILED） |

生产实测（部署版本 `1e0f693`）：
- 建草稿引用 3 图 → images 文件 9 个
- **编辑移除其中 1 图** → 该素材记录删除、文件 9→7（同步删主图+缩略图）
- 删除草稿 → 剩余素材记录删除、文件 7→3
- 结果：磁盘与 DB 一一对应，**零孤儿**；测试数据清理后仅剩运营自有素材

> 设计取舍：素材在 md5 命中且仍被其它内容引用时不会被删除（避免破坏其它内容）；该限制由「引用即保留 + 兜底任务回收」共同保证最终不残留。

**2026-10-28 修复与增强（详见 [`docs/incident-log-202610.md`](incident-log-202610.md) §2）**

- 视频转码恢复真异步（监听器独立 bean，`@Async` 代理不再被自调用绕过）→ 上传接口登记完即返回 `PROCESSING`；
- 上传改走流式接口（`upload-stream`）+ 关闭客户端超时，nginx 两侧 600s 且关闭请求缓冲；
- 前端按 `assetId` 轮询转码状态（含失败原因与「重新转码」）；
- 草稿允许挂 PROCESSING/FAILED 素材（发布仍强制 READY），草稿列表标「转码中」并禁用发布；
- 兜底：转码期间素材被删则回收产物文件；上传中断删除半截 tmp 文件。
