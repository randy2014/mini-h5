# 事故与问题记录 · 2026-10

> 文档地图：见 [`docs/README.md`](README.md)。新事故先记这里，可复用结论再提炼进 [`../DECISIONS.md`](../DECISIONS.md) / [`../LESSONS_LEARNED.md`](../LESSONS_LEARNED.md)。

## 1. 订阅频道金币购买成功后仍提示「订阅后查看」（10-21）

**现象**

- H5 端：用户用快乐币购买订阅频道成功后，进入频道详情列表点击内容仍提示「订阅后查看」，媒体图文/视频不可见、不可打开；
- 频道列表页却能正常显示「已订阅」（因为那里走的是另一条查询）。

**根因**

- 2026-10-04 的枚举整数化重构（`82116be`）把 `user_subscribe.status` 由字符串改为 `TINYINT`（`1=生效`），
  但 `UserSubscribeMapper.selectActive` 里的 SQL 仍是 `status = 'ACTIVE'`；
- MySQL 在比较 `TINYINT` 列与字符串 `'ACTIVE'` 时会把字符串转成 `0`，导致**永远匹配不到** →
  `SubscribeService.isAccessible` 恒为 `false`，付费用户被当作未订阅；
- 附带影响：续订时 `selectActive` 查不到已有有效订阅 → 不会延长 `end_time`，而是**再插入一行**（重复订阅、重复扣币）。

**修复**

- `UserSubscribeMapper.selectActive` 改为整数枚举 `status = 1`（复用 `UserSubscribe.STATUS_ACTIVE` 常量）；
- 新增 `UserSubscribeMapperSqlTest`：断言该 SQL 使用整数状态、不含 `'ACTIVE'` 等字符串字面量，防止回退。

**预防**

- 所有类型/状态枚举均已整数化；手写 SQL 里不要再出现字符串枚举字面量（`status = 'XXX'` / `type = 'XXX'` 等）；
- 提交前全局检索 `= '[A-Z_]+'`（限定在 Mapper / XML）确认无残留。

**验证**

- 本地 `mvn -pl mini-novel-vip -am -Dtest=UserSubscribeMapperSqlTest test` 通过；
- 上线后由 CI 部署，VPS 记录 `DEPLOY_SHA` 追溯。

## 2. 多媒体池子「添加视频」约 10-15 秒必超时（10-28）

**现象**

- 后台 `多媒体池子 → 新建视频 → 添加视频`，选一个 47MB 的手机短视频，约 10-15 秒弹出
  `timeout of 15000ms exceeded`，视频传不上去；图片素材正常。

**根因（三层叠加，主因是第一层）**

1. **视频转码实际没异步跑（主因）**：`MediaAssetProcessServiceImpl` 里的
   `@TransactionalEventListener` 方法直接 `this.processVideoAsync(...)` —— 同一个 bean 内自调用，
   绕过 Spring 的 `@Async` 代理，**ffmpeg 转码跑在上传请求的线程上**，上传接口要等转码+抽帧全部跑完
   才返回（47MB 视频 20s+），前端 15s 超时即在此刻触发。
2. **前端全局 15s 超时套在上传上**：`admin-ui/src/services/http.js` 的 axios 实例
   `timeout: 15000`，`MediaPoolView.uploadOne` 直接复用，视频仅传输就可能超过 15s。
3. **代理层无长超时 + 多层重复缓冲**：`mini-novel-admin-ui/nginx.conf` 的 `/admin-api/` 未设
   `proxy_*_timeout`（默认 60s）；且请求体在网关、admin-ui、Spring `/tmp`、`/data/media` 各落一份，
   300MB 视频瞬时占用可达 1GB+（磁盘是既定首要风险）。

**附带发现并一并修掉的缺陷**

- `VideoProcessor.run()` 先 `readAllBytes()` 再 `waitFor(900s)`，**超时永远不生效**：
  ffmpeg 卡死会无限阻塞，素材永久停在 `PROCESSING`，只能等每日 03:30 兜底任务标 FAILED
  （「处理超时（超过 2 小时未完成）」）。
- `MediaPoolView.pollAsset` 查「素材列表最新一条」再按 id 匹配，同帖又传了图片时**视频状态永远刷不到**，
  UI 一直停在「转码中…」并挡住保存。
- 转码期间移除/关闭编辑器删除素材后，转码产物（mp4/poster/thumb）无人回收 ——
  `MediaCleanupJob` 只扫 DB 记录，会变成永久磁盘孤儿。

**修复**

- 新增 `MediaVideoProcessEvent` + `MediaVideoProcessListener`（独立 bean，持有 `MediaAssetProcessService`
  代理），AFTER_COMMIT 后经 `@Async("mediaProcessExecutor")` 投递，上传接口登记完即返回 `PROCESSING`。
- 前端新增 `adminUploadApi`（`timeout: 0`）专供上传；`uploadOne` 带进度、保存时拦住「上传中」。
- 新增 `GET /admin/media/assets/{id}`，轮询改为按 id 精确查（含 FAILED 原因展示与「重新转码」按钮）。
- `VideoProcessor.run()` 改为「守护线程并发排空输出 + `waitFor(timeout)`」，并加子进程超时/输出截尾、
  `-nostdin -loglevel error`。
- 转码完成后二次确认素材仍存在且仍为 `PROCESSING`，否则回收产物文件（防孤儿）。
- nginx：网关与 admin-ui 的 `/admin-api/` 统一 `proxy_send/read_timeout 600s` +
  `proxy_request_buffering off`（边收边转，不再落中间临时文件）。
- `deploy/deploy.sh`：部署后重启列表加上 `mini-novel-gateway` —— 网关的 `nginx.conf` 是 bind mount，
  compose 不会因此重建容器，不重启的话改动永远不生效。

**预防**

- **禁止在同一 bean 内调用自己的 `@Async` / `@Transactional` 方法**：注解走代理，自调用等于没加。
  跨 bean 调用或注入自身代理（`ObjectProvider` / `@Lazy` 自引用）才生效。
- 大文件上传必须走「无超时/长超时」的独立客户端，且与 nginx 超时、multipart 上限三者对齐。
- 文档（`docs/media-pool-design.md` §5.2）写的是「异步」，实现偏离时必须回归核对——
  本次是「文档正确、代码错」，验收时只看了「25s 内 READY」，没验证「上传接口是否秒回」。

**验证（待 CI）**

- 本机沙箱 `pwsh` 不可用（`SetNamedSecurityInfoW failed`），无法本地 `mvn` / `npm` 构建，
  以 GitHub Actions 结果为准；部署后按 `docs/incident-log-202609.md` 的部署后核对清单复查。
- 生产复测：47MB 短视频应「上传完立即返回 PROCESSING」，转码期间可继续编辑，
  完成后提示「转码完成」，保存/发布成功；`docker logs` 中 `video processed assetId=…` 应在
  `media-process-*` 线程输出（而非 `http-nio-8080-exec-*`）。

## 3. 同日增强：草稿可含转码中素材 + 上传改流式（10-28）

由 §2 引出的两个产品/工程决策，用户确认后一并实施。

**1) 草稿允许保存带 PROCESSING/FAILED 素材（产品口径调整）**

- 原因：视频转码恢复异步后，「保存草稿」原先仍要求全部素材 READY，运营传完 47MB 要干等 1–3 分钟，
  中途关编辑器还会丢素材，体验等于把超时问题换了个形式。
- 后端：`MediaPostServiceImpl.replaceAssets` 去掉 READY 硬校验（素材存在即可挂草稿）；
  `publish()` 的「全部素材 READY」门槛**保持不变**（发布口径不放松）。
- 前端：保存不再拦截转码中素材，草稿列表按素材状态显示「转码中」标签并禁用发布按钮；
  转码完成后自动刷新列表（保存后保留轮询），发布按钮恢复可用。

**2) 上传改流式接口**

- 新增 `POST /admin/media/assets/upload-stream?fileName=xxx`（`application/octet-stream`，body=文件字节）。
  旧 multipart 接口保留兼容。
- 收益：去掉 Spring multipart 先落 `/tmp` 再复制到媒体卷的那一次重复写盘（300MB 视频省一半 IO 与峰值盘占用），
  也避免容器临时目录被大文件撑爆。请求体读取加 300MB 上限守卫；中断时删除半截 tmp 文件。

**验证（待 CI）**

- 草稿：转码中保存成功 → 草稿列表显示「转码中」且发布置灰 → 转码完成后自动变可发布 → 发布成功；
- 流式上传：`curl -X POST --data-binary @video.mp4 '.../upload-stream?fileName=video.mp4'` 应返回
  `ok=true` 且 `status=1`（PROCESSING），随后转码为 READY。
