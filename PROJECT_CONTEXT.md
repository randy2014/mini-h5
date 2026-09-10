# Project Context

> Status: current | Last tidy-up: 2026-09 | Documentation map: [`docs/README.md`](docs/README.md)
> This document describes "what it looks like now". For rationale see [`DECISIONS.md`](DECISIONS.md); for deployment and operations see [`DEPLOYMENT.md`](DEPLOYMENT.md).
> Sources of truth: `main` branch code, `deploy/docker-compose.prod.yml`, and the single database script
> `sql/schema.sql` (all 31 historical migrations were merged into it in 2026-09). Live production state is the
> VPS containers.

## Objective

Mini H5 is a mobile novel reading product with a Java backend, an independent H5 reader frontend, an independent admin frontend, and an independent crawler service. The current goal is to keep the public reading experience usable while steadily improving crawler quality and product polish.

## Current Architecture

```text
mini-novel-application   Spring Boot application entrypoint
mini-novel-api           H5/public API layer
mini-novel-admin         Admin API layer
mini-novel-book          Novel, category, chapter domain
mini-novel-media         Media pool: assets, image compression, video transcoding, authorized streaming
mini-novel-user          Login, bookshelf, reading history
mini-novel-vip           VIP plan/order, invitation codes, subscribe channels, user coins
mini-novel-crawler       Shared crawler domain/entities/services
mini-novel-crawler-service
                         Independent crawler runtime and scheduler
mini-novel-h5            Independent mobile H5 reader app
mini-novel-admin-ui      Independent admin UI
sql                      Single idempotent database script (schema.sql) and seed helpers
deploy                   Production Docker Compose and deploy script
```

## Runtime Stack

- Java 17, Spring Boot 3, MyBatis-Plus.
- MySQL 8.4 as the business database and crawler staging database.
- Redis 7.4.
- Vue/Vite H5 frontend.
- Vue/Vite admin frontend.
- Docker Compose deployment on a VPS.
- GitHub Actions deploys `main` to the VPS.

Elasticsearch and RabbitMQ are intentionally not used.

## Production Addresses

Production host: `64.90.19.6` (SSH port `52527`, deploy path `/opt/mini-h5`). The earlier host `43.161.222.78:2222`
is the **old server** and is no longer current.

- Domain: `xs2026.site`

- H5: `http://64.90.19.6:5173/h5/home`
- Admin UI: `http://64.90.19.6:5180/admin/login`
- Backend API: `http://64.90.19.6:8080/api/home`
- API docs: `http://64.90.19.6:8080/swagger-ui.html`

Crawler service listens on internal port `8090` only and is not published.
MySQL is bound to `127.0.0.1:3306` on the VPS. Use an SSH tunnel for GUI tools such as Navicat.
See [`DEPLOYMENT.md`](DEPLOYMENT.md) for the current host/port facts.

## Databases

`mini_novel` is the business database used by the app:

- `category`: H5 category navigation.
- `novel`: novel metadata.
- `chapter`: chapter metadata and正文.
- `novel_identity`, `novel_source_mapping`, `chapter_source_mapping`: source-to-business mapping and duplicate recognition.
- `app_user`, `user_bookshelf`, `user_read_history`: user state, bookshelf, reading progress.
- `vip_plan`, `user_vip`, `vip_order`, `vip_adjust_log`: VIP and payment management.
- `vip_invitation_code`, `vip_invitation_record`, `vip_operation_audit`: invitation-code access and admin audit trail.
- `vip_category`, `novel_vip_category_mapping`, `vip_source_category_mapping`: VIP category isolation (legacy VIP shelves, kept separate from subscribe channels).
- `subscribe_channel`, `subscribe_channel_novel`: subscribe channels and the novels mounted into them.
- `user_subscribe`: per-user channel subscriptions (period, start/end, status, coins paid).
- `user_coin_balance`, `user_coin_log`: coin balance and ledger.
- `media_asset`, `media_post`, `media_post_asset`: media pool (assets, posts, post-asset ordering).
- `ticket`, `ticket_reply`: user tickets and replies.

`mini_novel_crawler` is the crawler staging database:

- `crawl_source`: source site and parsing rules.
- `crawl_rank_source`: rank/category entry points.
- `crawl_schedule`: daily trigger configuration.
- `crawl_source_credential`: credential configuration for sources that need it.
- `crawl_task_v2`: crawl execution record.
- `crawl_book_raw`, `crawl_chapter_raw`, `crawl_content_raw`: raw staging data.
- `crawl_merge_task`, `crawl_merge_item`: cleaning and merge records.

Tables from the removed authorized-book feature (`crawler_authorized_book`, `crawler_authorized_book_audit`,
`xbookcn_raw_repair_cursor`) are dropped by §1 of `sql/schema.sql`.

MySQL binary logging is permanently disabled (`--disable-log-bin`), so crawler writes no longer produce binlogs.

The crawler database is allowed to be cleaned aggressively. Raw and failed records do not need long-term retention at this stage.

## Current Product Scope

H5 has:

- Home, category, rank, search, bookshelf, profile, VIP, login.
- Book detail and reader pages.
- Reader can navigate chapters and return to home/detail.
- Reading history exists and should restore the previously read chapter and scroll the chapter list to the current position.
- Bookshelf supports association with user and should include cancel/remove behavior.
- VIP books/chapters must remain visually distinguishable.
- Subscribe tab: channel list, channel detail (unified grid/list feed of novels + image/video posts), reading
  history inside the subscribe area, and the coin wallet page.
- Media detail page: image gallery and video playback, with download prevention.
- Ticket page for user feedback.

Admin UI has:

- Dashboard.
- Article management.
- Category management.
- User management.
- VIP management (including VIP categories).
- Order/payment related views.
- Crawler management and content review.
- Subscribe channel management.
- Coin management (manual recharge and ledger).
- Media pool (drafts / published, editor, publish dialog, playback preview).
- Ticket management.

## Current Crawler Direction

The stable source is `23qb_public`. Earlier Qidian/Shuqi experiments are no longer the primary direction and should stay disabled unless explicitly reactivated.

Current policy:

- Pull high-value rank/category sources, currently the 13 homepage categories.
- Pull actual chapter正文, not only IDs or URLs.
- `23qb_public` content is published directly into the library after crawl (no review); `h528_authorized` / `novel69h_authorized` content goes through the content review queue (`PENDING_REVIEW`) and only approved content is published. The authorized-book list feature and content filter rules were removed in the 2026-09-01 refactor.
- Avoid re-crawling existing chapters where source mapping already confirms the chapter was collected.
- Completed novels should carry a completed status; completed novels should need less follow-up crawling.
- Staging data is cleaned after merge/review.
- Schedules registered by migrations: `04:00` Asia/Shanghai for `23qb_public` (`auto_merge=1`), `02:00` for the
  h528 and 69hnovel authorized sources (`auto_merge=0`, review queue). Actual enabled state lives in
  `mini_novel_crawler.crawl_schedule`; the global on/off switch is `CRAWLER_SCHEDULE_ENABLED` in
  `deploy/docker-compose.prod.yml`.

## Important Operating Principles

- Do not manually build and deploy application code on the VPS as the normal path.
- Code changes should be pushed to GitHub and deployed through GitHub Actions.
- VPS manual operations are acceptable for database inspection, scheduler toggles, emergency cleanup, and runtime verification.
- For current development, historical data compatibility is not a hard requirement. If schema/data changes are needed, direct changes are acceptable after confirming the goal.
- Keep H5 user experience and crawler validity as high-priority work streams.

## Recent Resolved Issues

- Docker Desktop virtualization issue was resolved locally.
- VPS Docker installation and production compose deployment were completed.
- GitHub Actions CI/CD deployment was configured.
- SSH instability was handled by changing SSH daemon settings and moving to a dedicated SSH port (`52527` on the
  current host; an earlier host used `2222`).
- MySQL direct public access was avoided; Navicat should use SSH tunnel.
- MySQL 8 `caching_sha2_password` client compatibility was handled by using native password mode in Docker command.
- Crawler source direction was narrowed to `23qb_public`.
- Raw crawler data and MySQL binlog growth caused disk pressure; raw tables were cleared and binary logging was
  permanently disabled (`--disable-log-bin`, 2026-08-31).
- Daily crawler schedule was enabled at `04:00`.
- 2026-09-01 crawler refactor: authorized-book list and content filter rules removed; publication split into
  "public source auto-publish" vs "authorized source review queue".
- 2026-09 deployment/ops incidents (concurrent build OOM, nginx 502 after container rebuild, parameter binding
  and native-SQL column-name 500s, non-idempotent migration) are recorded in
  [`docs/incident-log-202609.md`](docs/incident-log-202609.md).
- 2026-09 subscribe channel (M1) and media pool shipped; see [`TASKS.md`](TASKS.md) and
  [`docs/media-pool-design.md`](docs/media-pool-design.md).

