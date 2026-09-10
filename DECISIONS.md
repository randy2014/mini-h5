# Decisions

> Status: current | Last tidy-up: 2026-09 | Documentation map: [`docs/README.md`](docs/README.md)
> Scope: architecture, deployment, database, crawler and product decisions that should not be re-litigated.
> Current state lives in [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md); product tasks in [`TASKS.md`](TASKS.md).

This document records project decisions that should guide future work. It is meant to prevent repeated debates and keep implementation aligned with the current direction.

## Architecture

### Java backend is the primary backend architecture

Decision:

- Keep the backend on Java/Spring Boot.
- Keep the current modular monolith structure.
- Do not introduce Spring Cloud at this stage.

Reason:

- The product is still early-stage and does not need distributed-service complexity.
- A modular monolith is easier to deploy, debug, and evolve on one VPS.

### Frontends are independent projects

Decision:

- H5 app remains in `mini-novel-h5`.
- Admin UI remains in `mini-novel-admin-ui`.
- Do not mix admin UI into the H5 app.

Reason:

- H5 is user-facing and mobile-first.
- Admin is operations-facing and should evolve independently.

### Crawler runtime is independent

Decision:

- Crawler execution runs in `mini-novel-crawler-service`.
- It should be deployed as a separate container from the main app.

Reason:

- Crawler workload can be long-running and unstable.
- Reader traffic should not be blocked by crawling.

### No Elasticsearch or RabbitMQ for now

Decision:

- Do not add Elasticsearch.
- Do not add RabbitMQ.

Reason:

- Current scale and feature set do not require them.
- MySQL plus direct scheduled execution is enough for the current phase.

## Deployment

### CI/CD is the normal deployment path

Decision:

- Code changes should be committed and pushed to `main`.
- GitHub Actions deploys to the VPS.
- Do not manually build application code on the VPS as the normal workflow.

Reason:

- Keeps deployment repeatable.
- Avoids patching production by hand.
- Makes production state traceable by Git commit.

### VPS manual work is limited to operations

Decision:

- Manual VPS commands are acceptable for runtime checks, logs, database inspection, task toggles, emergency cleanup, and one-off verification.
- Manual VPS commands are not the standard way to change application code.

Reason:

- Operations sometimes require immediate intervention.
- Application behavior should still be controlled by repository code and CI/CD.

### Current data does not require strict backward compatibility

Decision:

- During this stage, schema/data changes may directly change or clean existing VPS data.
- Historical crawler raw data can be removed.

Reason:

- The system is still under active construction.
- Faster iteration is more valuable than preserving bad or experimental data.

## Database

### Business database and crawler staging database are separate

Decision:

- `mini_novel` stores business data used by the H5 and admin apps.
- `mini_novel_crawler` stores raw crawler data and task state.

Reason:

- Crawling should not directly pollute reader-facing data.
- Raw data can be cleaned without touching the business tables.

### Raw crawler data is temporary

Decision:

- `crawl_book_raw`, `crawl_chapter_raw`, `crawl_content_raw`, and `crawl_merge_item` do not need long-term retention at this stage.
- Failed raw data does not need special preservation.

Reason:

- Raw正文 grows quickly.
- Failed records are rarely useful right now.
- Disk pressure has already been a real operational risk.

### MySQL binlog is disabled (supersedes the earlier 1-day retention decision)

Decision:

- Keep binary logging disabled for the single-node production MySQL (`--disable-log-bin`).
- Do not re-enable it unless replication or point-in-time recovery is actually introduced.

Reason:

- The VPS is not using MySQL replication.
- Crawler-heavy writes generated tens of GB of binlog and filled the disk twice.
- Retention tuning (1 day) reduced but did not remove the risk; disabling removes it.

History: the original decision was "keep binlog retention short (target 1 day)"; that was superseded on
2026-08-31 when binary logging was turned off (see [`LESSONS_LEARNED.md`](LESSONS_LEARNED.md) and
[`docs/ops-handbook.md`](docs/ops-handbook.md)).

## Crawler

### `23qb_public` is the current stable source

Decision:

- Use `23qb_public` as the primary active source.
- Disable Qidian and Shuqi schedules unless explicitly reactivated.

Reason:

- Prior research found `23qb_public` is the first source with a working chain for public chapter正文.
- Qidian/Shuqi paths were unstable or incomplete for the current goal.

### Collect正文, not placeholders

Decision:

- A crawler task is not considered successful unless it collects actual chapter正文.
- IDs, URLs, catalog entries, or placeholder text are not enough.

Reason:

- The product value is readable novel content.
- Dirty placeholder data caused earlier H5 failures and confusion.

### Complete chapter content is required

Decision:

- If a source chapter has pagination, the crawler must collect all pages and merge them in order.
- Incomplete chapters should not be treated as valid business content.

Reason:

- Chapter completeness is the core correctness requirement for reading.

### Duplicate novels must be identified across sources

Decision:

- Same novel from multiple sources should map to one business novel when confidence is high.
- Mapping tables must be used instead of blindly inserting duplicate novels.

Reason:

- Multi-source expansion will otherwise pollute the H5 catalog.

### Existing collected chapters should be skipped

Decision:

- If a chapter is already collected and mapped, future tasks should skip it unless a refresh policy is explicitly added.

Reason:

- Saves crawl time and disk.
- Avoids duplicate and repeated writes.

### Completed novels should be marked and crawled less

Decision:

- Source-completed novels should be reflected in business status.
- Completed novels should not be repeatedly crawled after all chapters are mapped.

Reason:

- Completed works are preferred for initial data quality.
- They are stable and reduce ongoing crawler cost.

### Collected content is published in two tiers

Decision:

- Public sources (currently `23qb_public`) publish directly: crawled chapters become `CONTENT_READY` and the
  clean-merge service writes them into the business tables automatically (`auto_merge=1`).
- Authorized VIP sources (`h528_authorized`, `novel69h_authorized`) always go through the content review queue:
  chapters become `PENDING_REVIEW`, and only human-approved chapters are published (`auto_merge=0`).
- The merge service only processes `CONTENT_READY` books and never touches `PENDING_REVIEW` ones.

Reason:

- Public content has low risk and volume matters, so review would only add latency.
- Authorized content carries rights/quality risk, so a human gate is required before it reaches H5.

Removed in the 2026-09-01 refactor: the authorized-book list feature and the automated content filter rules
(risk keyword blocking, reject patterns, minimum-length checks). See
[`CRAWLER_DESIGN.md`](CRAWLER_DESIGN.md) and [`docs/incident-log-202609.md`](docs/incident-log-202609.md).

### The database has exactly one idempotent script

Decision:

- `sql/schema.sql` is the only database script. It holds databases, current table structures, seeded
  configuration and the necessary idempotent data corrections, and is applied on every deploy.
- Every statement in it must be safe to re-run: `CREATE TABLE IF NOT EXISTS`, `ON DUPLICATE KEY UPDATE` or
  `NOT EXISTS` guarded inserts, and `information_schema` + `PREPARE` guards for `ADD COLUMN` / `ADD INDEX`.
- New changes are appended to §4 of that file. Do **not** add numbered `sql/migrations/*.sql` files again.

Reason:

- `deploy.sh` re-applies the script on each deployment, so non-idempotent statements break or corrupt data
  (the 2026-09-01 incident flipped already-approved content back to the review queue on every deploy).
- A single ordered file removes the "which migration has run?" question and the need for marker-table guards.
- History: 31 separate migration files were merged into the single script in 2026-09; the old files remain
  readable in git history.

### Schedulers need stale-task recovery and a global switch

Decision:

- Only enabled schedules run; an active `PENDING`/`RUNNING` task for the same source blocks duplicate creation.
- Stale `RUNNING`/`MERGING` tasks must be closed instead of silently blocking all future collection.
- Keep the global `CRAWLER_SCHEDULE_ENABLED` switch so crawling can be paused instantly when disk or load spikes.

Reason:

- A single stuck task previously blocked scheduled collection indefinitely.
- Runaway crawling filled the VPS disk twice and made the server unresponsive.

## Product

### H5 user experience is high priority

Decision:

- H5 visual consistency, navigation, reading continuity, and performance are top priorities.

Reason:

- The H5 reader is the main user-facing surface.
- Usable reading flow matters as much as backend correctness.

### Reader settings belong in profile/settings

Decision:

- Move broad reader setting controls into the profile/settings area rather than scattering them in reader-only bottom UI.

Reason:

- Keeps reader controls focused.
- Makes global preferences easier to find and reuse.

### Book detail and reader must have clear return paths

Decision:

- Book detail and reader pages must provide obvious navigation back to home/detail.

Reason:

- H5 users should not get trapped in reading/detail pages.

### VIP and completed states must be visible

Decision:

- VIP content and completed status should be shown clearly in H5 and admin.

Reason:

- VIP affects access/payment behavior.
- Completed status affects user choice and crawler policy.

## Admin

### Admin should operate core content and crawler flows

Decision:

- Admin should cover dashboard, article, category, user, VIP, order/payment, and crawler task management.

Reason:

- Direct SQL should not be the normal operation path forever.
- Crawler and content state need visual management.

### Article off-shelf ability is required

Decision:

- Admin article management must support taking novels/articles off shelf.

Reason:

- Bad or incomplete content must be removable from H5 display.

## Deferred

### VIP credential crawler support is deferred

Decision:

- Credential-based VIP crawling is not part of the immediate implementation.

Reason:

- The current priority is the public chapter chain.
- Credential validation and account risk need separate design.

### Manual TXT import is deferred

Decision:

- TXT import remains a future capability.

Reason:

- It is useful, but the current first goal is stable web-source collection.

