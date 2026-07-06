# Crawler Design

## Goal

The crawler must collect real novel chapter正文 from stable public sources, clean the data, deduplicate across sources, and write usable novels/chapters into the business database so the H5 reader can display them normally.

At this stage the priority is a stable working chain, not broad source coverage.

## Current Source Strategy

Primary source:

```text
23qb_public
https://www.23qb.net
```

Disabled or non-primary sources:

- Qidian: not used for current public collection chain.
- Shuqi: prior experiment only, should stay disabled unless explicitly reopened.
- Other sources: must be validated by a complete chain before code integration.

## Collection Scope

Current target:

- 13 category/rank entries from `23qb_public`.
- High-value category/rank pages.
- Public chapter正文.
- Existing chapters should be skipped when mapping proves they already exist.
- Completed novels should be marked completed and crawled less often after completion.

Current schedule:

```text
04:00 Asia/Shanghai
```

Manual trigger remains available from the crawler service internal endpoint.

## Data Flow

```text
rank/category page
  -> book detail
  -> catalog/chapter list
  -> chapter page(s)
  -> raw staging tables
  -> quality checks and cleaning
  -> source mapping / duplicate recognition
  -> business tables
  -> H5 reader
```

## Staging Tables

Crawler database: `mini_novel_crawler`.

- `crawl_source`: source site and JSON rule configuration.
- `crawl_rank_source`: rank/category/list entry URLs and per-entry max book count.
- `crawl_schedule`: schedule configuration and whether auto-merge is enabled.
- `crawl_task_v2`: crawl execution state.
- `crawl_book_raw`: raw book metadata.
- `crawl_chapter_raw`: raw chapter list and chapter metadata.
- `crawl_content_raw`: raw chapter正文.
- `crawl_merge_task`: merge execution state.
- `crawl_merge_item`: book-level merge details.

Raw staging data is temporary. It can be truncated after successful merge or during emergency cleanup.

## Business Tables

Business database: `mini_novel`.

- `novel`: final book metadata.
- `chapter`: final chapter metadata and正文.
- `category`: app categories.
- `novel_identity`: dedup identity by normalized title/author.
- `novel_source_mapping`: source book to business novel mapping.
- `chapter_source_mapping`: source chapter to business chapter mapping.

The H5 app reads from business tables only. It should not depend on raw crawler tables.

## Dedup Rules

Book-level recognition should use multiple signals:

- Normalized title.
- Normalized author.
- Source book ID and source URL mapping.
- Category and status as secondary signals.
- Word count and latest chapter as confidence helpers.

Chapter-level recognition should use:

- Source chapter ID.
- Source chapter URL.
- Chapter number/order.
- Normalized chapter title.
- Content hash.

If a source chapter is already mapped, skip collection and merge for that chapter unless a future update policy explicitly requires refresh.

## Content Completeness Rules

The crawler must collect novel正文, not only IDs, URLs, or chapter titles.

For each chapter:

- Extract正文 from the configured content selector.
- Remove ads, navigation text, update tips, and unrelated site boilerplate.
- Preserve paragraph breaks.
- If the source chapter has pagination, follow all next-page links and concatenate in order.
- Compute content hash after cleaning.
- Store content length for quality checks.

Minimum quality checks:

- Content is not blank.
- Content length is above a source-specific threshold.
- Content does not look like a login/error/anti-bot page.
- Title and body are not accidentally swapped.
- Repeated boilerplate ratio is controlled.

If a chapter fails quality checks, it should not be written into the business chapter table as valid正文.

## Completed Novel Handling

When source metadata indicates a novel is completed:

- Write business `novel.status` as completed.
- Keep the completed status visible on H5 detail/list cards.
- Reduce or skip future crawling after all known chapters are mapped.

If the source status is unknown, keep it as ongoing/unknown rather than guessing.

## Schedule Behavior

The scheduler checks enabled schedules and exact `HH:mm` values.

Rules:

- Only enabled schedules run.
- Current active `PENDING` or `RUNNING` tasks for the same source should block duplicate task creation.
- `auto_merge=1` means merge should run after crawl.
- `crawl_public=1` and `crawl_vip=0` is the current default.

Current active schedule:

```text
source: 23qb_public
time: 04:00
timezone: Asia/Shanghai
auto_merge: enabled
```

## Failure Retention Policy

At this stage, failed raw data does not need special preservation.

Recommended behavior:

- Keep task-level summary and counts.
- Do not keep large failed正文 raw data indefinitely.
- Clean raw staging tables after successful merge or on a short retention schedule.
- Do not let raw tables or binlog growth threaten the VPS disk.

## Validation Checklist

For each crawler change:

1. Trigger one manual task.
2. Confirm raw book, chapter, and content counts increase.
3. Confirm正文 exists in `crawl_content_raw`.
4. Confirm merge task runs.
5. Confirm business `novel` and `chapter` rows are created or updated.
6. Open H5 book detail.
7. Open reader and verify chapter正文 is readable.
8. Confirm categories are associated.
9. Confirm duplicate source chapters are skipped on the next run.
10. Confirm no task stays stuck in `RUNNING` or `MERGING`.

## Known Risks

- Source HTML can change and break selectors.
- Large crawls can generate large MySQL binlogs.
- Raw正文 can grow quickly if not cleaned.
- Some novels may have missing or reordered source chapters.
- Source category names may not match business categories exactly.
- Aggressive crawling may be blocked by source-side anti-abuse rules.
