# Xbookcn Postmortem And Reusable Lessons

## Scope

This case involved an authorized adult source collected into an isolated review pipeline and published chapter-by-chapter to a VIP H5 area.

## What Failed

### Long Tasks And Misleading Success

Large sequential jobs were interrupted by deployments and repeatedly restarted from early items. Task success represented processing, deduplication, or risk-state writes, not a complete readable book.

Lesson: split by source/book, persist continuation, and calculate completeness from catalog/body joins.

### Temporary Limits Became Production Configuration

Test sampling values remained in persistent source configuration, making categories appear incomplete.

Lesson: keep task-level limits separate from persistent production limits and audit configuration changes.

### Duplicate Raw Identity

Parsed IDs, URL hashes, and authorized IDs created multiple raw books for one source book. Chapters and statuses became split across records.

Lesson: make the authorized `source_book_id` canonical. Use URL only as a reconciliation fallback, never as a competing identity.

### Approval Did Not Guarantee Formal Publication

Raw approval status, mapping rows, formal chapters, and H5 counts diverged. Some publishing logic matched by chapter number.

Lesson: approval must transactionally upsert formal content and stable mappings. Reconcile approval, mapping, formal, API, and H5 counts.

### Keyword Risk Rules Produced False Positives

Whole-chapter keyword co-occurrence and character-distance rules were treated as confirmed findings.

Lesson: ambiguous text signals are review annotations, not facts. Keep content isolated and require human approval for publication. Do not expose review content publicly.

### Generic And Prefixed Titles Leaked To H5

Placeholder book titles and chapter titles containing repeated book-name prefixes reached formal tables.

Lesson: derive book title from the authorized metadata record and chapter title from the main catalog link. Backfill raw, mapping, and formal layers by stable IDs.

### Blogger URLs Were Under-Recognized

The parser accepted numeric `.html` paths but rejected most `blog-post_*.html` chapter URLs. A multi-chapter book appeared as one chapter.

Lesson: inventory real URL families before writing allowlists. Test mixed URL styles and multiple label pages.

### 403 Was Misdiagnosed As Missing Credentials

Production configuration said `COOKIE`, but historical successful runs used no credential. The same URLs returned 200 locally and Cloudflare challenge 403 from VPS/container.

Lesson: compare historical network evidence and current egress behavior before changing authentication. Do not bypass WAF; request source allowlisting or an official access path.

## Incident Checklist

1. Freeze collection and publication writes.
2. Confirm source disabled and no active tasks.
3. Reproduce with one URL at low frequency.
4. Compare historical successful task evidence.
5. Audit stable book/chapter mappings.
6. Quantify affected books and chapters from joins, not task counters.
7. Fix code with fixtures and idempotency tests.
8. Deploy through CI/CD.
9. Repair data in small resumable batches.
10. Validate one book end-to-end before expanding.

## Minimum Handoff Report

- authorization and source boundary;
- deployment commit;
- source accessibility evidence;
- total/complete/incomplete book counts;
- missing catalog/body counts;
- duplicate identity count;
- approved/mapped/formal/API/H5 reconciliation;
- batch results and timeouts;
- source/task final safe state;
- unresolved blockers and required owner decision.
