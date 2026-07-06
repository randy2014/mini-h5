# Project Context

## Objective

Mini H5 is a mobile novel reading product with a Java backend, an independent H5 reader frontend, an independent admin frontend, and an independent crawler service. The current goal is to keep the public reading experience usable while steadily improving crawler quality and product polish.

## Current Architecture

```text
mini-novel-application   Spring Boot application entrypoint
mini-novel-api           H5/public API layer
mini-novel-admin         Admin API layer
mini-novel-book          Novel, category, chapter domain
mini-novel-user          Login, bookshelf, reading history
mini-novel-vip           VIP plan, order, user VIP status
mini-novel-crawler       Shared crawler domain/entities/services
mini-novel-crawler-service
                         Independent crawler runtime and scheduler
mini-novel-h5            Independent mobile H5 reader app
mini-novel-admin-ui      Independent admin UI
sql                      Base schema and migrations
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

- Domain: `xs2026.site`

- H5: `http://43.161.222.78:5173/h5/home`
- Admin UI: `http://43.161.222.78:5180/`
- Backend API: `http://43.161.222.78:8080`

MySQL is bound to `127.0.0.1:3306` on the VPS. Use an SSH tunnel for GUI tools such as Navicat.

## Databases

`mini_novel` is the business database used by the app:

- `category`: H5 category navigation.
- `novel`: novel metadata.
- `chapter`: chapter metadata and正文.
- `novel_identity`, `novel_source_mapping`, `chapter_source_mapping`: source-to-business mapping and duplicate recognition.
- `app_user`, `user_bookshelf`, `user_read_history`: user state, bookshelf, reading progress.
- `vip_plan`, `user_vip`, `vip_order`, `vip_adjust_log`: VIP and payment management.

`mini_novel_crawler` is the crawler staging database:

- `crawl_source`: source site and parsing rules.
- `crawl_rank_source`: rank/category entry points.
- `crawl_schedule`: daily trigger configuration.
- `crawl_task_v2`: crawl execution record.
- `crawl_book_raw`, `crawl_chapter_raw`, `crawl_content_raw`: raw staging data.
- `crawl_merge_task`, `crawl_merge_item`: cleaning and merge records.

The crawler database is allowed to be cleaned aggressively. Raw and failed records do not need long-term retention at this stage.

## Current Product Scope

H5 has:

- Home, category, rank, search, bookshelf, profile, VIP, login.
- Book detail and reader pages.
- Reader can navigate chapters and return to home/detail.
- Reading history exists and should restore the previously read chapter and scroll the chapter list to the current position.
- Bookshelf supports association with user and should include cancel/remove behavior.
- VIP books/chapters must remain visually distinguishable.

Admin UI has:

- Dashboard.
- Article management.
- Category management.
- User management.
- VIP management.
- Order/payment related views.
- Crawler management.

## Current Crawler Direction

The stable source is `23qb_public`. Earlier Qidian/Shuqi experiments are no longer the primary direction and should stay disabled unless explicitly reactivated.

Current policy:

- Pull high-value rank/category sources, currently the 13 homepage categories.
- Pull actual chapter正文, not only IDs or URLs.
- Avoid re-crawling existing chapters where source mapping already confirms the chapter was collected.
- Completed novels should carry a completed status; completed novels should need less follow-up crawling.
- Staging data is cleaned/merged into business tables.
- Daily schedule is currently `04:00` Asia/Shanghai for `23qb_public`.

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
- SSH instability was handled by changing SSH daemon settings and using port `2222`.
- MySQL direct public access was avoided; Navicat should use SSH tunnel.
- MySQL 8 `caching_sha2_password` client compatibility was handled by using native password mode in Docker command.
- Crawler source direction was narrowed to `23qb_public`.
- Raw crawler data and MySQL binlog growth caused disk pressure; raw tables were cleared and binlog retention was reduced.
- Daily crawler schedule was enabled at `04:00`.

