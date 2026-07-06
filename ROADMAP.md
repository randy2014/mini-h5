# Roadmap

## Direction

Mini H5 will continue along two main work streams:

1. Crawler stability and data quality.
2. H5 reading experience and admin manageability.

The near-term objective is not to support many sources at once. The priority is to make one stable public source produce readable, complete, deduplicated novel data, then gradually improve scale and operations.

## Phase 1 - Stable Data Chain

Goal: make `23qb_public` a stable collection source.

Focus:

- Keep only reliable crawler sources enabled.
- Collect category/rank books, chapter catalogs, and chapter正文.
- Skip chapters already collected and mapped.
- Merge raw staging data into the business database.
- Clean raw staging data after merge.
- Keep disk usage under control.
- Verify H5 can read newly collected books normally.

Exit criteria:

- Manual collection can complete without stuck tasks.
- Scheduled collection at `04:00` runs normally.
- New books appear in H5.
- Chapter正文 is readable and not placeholder/dirty content.
- Repeated collection does not duplicate existing chapters.

## Phase 2 - H5 Product Polish

Goal: make the reader feel coherent and usable.

Focus:

- Unify spacing, buttons, page headers, and navigation style.
- Improve home, category, book detail, bookshelf, and reader pages.
- Make return-to-home/detail navigation obvious.
- Restore last-read chapter and scroll position.
- Improve long chapter list performance.
- Make completed/VIP/off-shelf states visible and consistent.

Exit criteria:

- Core reading flow is smooth on mobile.
- Book detail and reader pages have clear back/home paths.
- Bookshelf add/remove and reading history work predictably.
- UI no longer has obvious spacing, alignment, or inconsistent control issues.

## Phase 3 - Admin Operations

Goal: allow crawler and content operations from the admin UI.

Focus:

- Crawler source/rank/schedule management.
- Manual trigger and task status visibility.
- Raw data cleanup entry.
- Article on/off shelf management.
- Category association review.
- User, VIP, payment status management.

Exit criteria:

- Admin can start and observe crawler tasks.
- Admin can see collection/merge results.
- Admin can manage article status and category.
- VIP/user status can be adjusted without direct SQL.

## Phase 4 - Data Quality Expansion

Goal: improve correctness before adding more sources.

Focus:

- Missing chapter detection.
- Chapter content quality score.
- Duplicate book confidence details.
- Completed novel crawl-skip policy.
- Category normalization.
- Better error classification.

Exit criteria:

- Bad chapters are blocked from business tables.
- Missing chapters can be detected and reviewed.
- Completed books stop wasting crawl effort.
- Duplicate novels are merged with explainable confidence.

## Phase 5 - Source Expansion

Goal: add new sources only after proving a full chain.

Rules:

- A source must support book metadata, catalog, and complete chapter正文.
- Pagination must be handled before integration.
- Selectors and quality checks must be source-specific.
- Source must pass a one-book proof, then a category/rank proof.
- New sources should not pollute business data if quality checks fail.

Candidates should be researched and validated one at a time.

## Phase 6 - Production Hardening

Goal: prepare for real public use.

Focus:

- Domain and HTTPS.
- Backup and recovery plan.
- Monitoring for disk, service health, and crawler failures.
- Safer secret rotation.
- More formal release verification.
- Repository visibility and access control.

Exit criteria:

- Deployment is repeatable through CI/CD.
- Data can be backed up and restored.
- Service health and disk pressure are visible early.
- Public access is protected with HTTPS.

## Current Priority

Current highest priorities:

1. Keep `23qb_public` crawler running and verify collected正文 quality.
2. Finish H5 user experience cleanup.
3. Add admin controls for crawler/task visibility and content status.
4. Add automatic cleanup/retention so raw data and binlog do not fill disk again.

