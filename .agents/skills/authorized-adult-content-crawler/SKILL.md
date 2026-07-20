---
name: authorized-adult-content-crawler
description: Design, audit, implement, and operate crawlers for legally authorized adult-content sources with metadata isolation, stable book/chapter identity, complete catalog traversal, quarantined human review, incremental VIP publishing, production safety, and evidence-based 403/WAF diagnosis. Use for new authorized sources, missing chapters, duplicate raw data, review/publish mismatches, or crawler incident response.
---

# Authorized Adult Content Crawler

Use this workflow only for sources with documented collection and display authorization. Keep collection, review, and publication as separate gates.

## Operating Rules

- Confirm authorization scope, allowed content, rate limits, retention, and display rights before implementation.
- Never bypass login, CAPTCHA, WAF, Cloudflare challenges, robots restrictions, or other access controls. Stop and request an allowlist or official access method.
- Require explicit user approval before code changes, production writes, collection runs, approval, or publication.
- Keep public/free sources isolated from authorized adult sources.
- Store new content in a restricted review area. Publish only chapters explicitly approved by a human reviewer.
- Never expose pending, rejected, or blocked chapter titles or content through public APIs or H5.

## Workflow

### 1. Establish The Source Contract

Record source code, source type, authorization reference, allowed operations, effective dates, and whether authentication is genuinely required. Verify behavior from network evidence; do not infer authentication from stale configuration.

### 2. Reconnoiter Read-Only

Identify every page type before coding:

- metadata/detail page;
- label/search/aggregation page;
- catalog pagination;
- chapter page;
- cover and canonical URL;
- redirects, domains, and URL variants.

Sample first, middle, last, paginated, and irregular books. Save structural fixtures, not sensitive content.

For Blogger-style sources, explicitly test numeric article paths and `blog-post*.html`, `updated-max`, `start`, `max-results`, `by-date`, and older-page links.

### 3. Define Stable Identity

Use source-owned identifiers whenever available:

```text
book identity    = source_code + authorized source_book_id
chapter identity = source_code + source_book_id + normalized source_chapter_id
```

Generate `source_chapter_id` from a normalized canonical URL path only when the source has no identifier. Never use title, chapter number, short hash, raw row ID, or current ordering as identity.

Titles, body, chapter number, and source URL must come from the same raw chapter record.

### 4. Parse Catalog Completely

- Extract links only from the main content list.
- Exclude introductions, navigation, sidebars, recommendations, labels, and pagination links.
- Traverse pagination until there is no new canonical chapter URL; also enforce a conservative page cap.
- Preserve source order. Hidden or pending chapters must not renumber approved chapters.
- Re-running must not create duplicate books, chapters, or bodies.

### 5. Separate Collection From Publication

Use these gates:

```text
DISCOVERED -> METADATA -> CATALOG -> QUARANTINED_CONTENT
            -> HUMAN_APPROVED -> FORMAL_VIP_CHAPTER -> H5
```

Collection success never means publication approval. New adult content enters `PENDING_REVIEW`. Automated textual risk signals may annotate review priority, but ambiguous keyword or distance matches must not become confirmed facts.

### 6. Publish Incrementally And Safely

- One approved chapter may make a book visible in the VIP list.
- H5 returns only formally approved chapters.
- Map each formal chapter through the stable book/chapter identity.
- Approval upserts the formal chapter and mapping in one transaction.
- Rejection removes the formal chapter without altering raw evidence or audit history.
- VIP authorization remains enforced at the body endpoint.

### 7. Measure Completeness Correctly

Never use task `successCount` as book completeness. Reconcile per book:

```text
source catalog distinct URLs
raw distinct source_chapter_id
raw bodies
human-approved chapters
source mappings
formal chapters and bodies
H5 API total
```

Classify books as: no raw, metadata only, catalog incomplete, body incomplete, complete pending review, or completely published.

### 8. Run Production In Small Batches

- Preflight one detail, catalog, and chapter request.
- Start with one book, then batches of at most five.
- Track selected IDs, current book/stage, inserted/updated/deduplicated counts, timeouts, and continuation.
- Stop on repeated selections, non-advancing cursors, elevated timeout rates, deployment restarts, or access-control responses.
- Disable the source and verify no running/pending tasks after completion or failure.

### 9. Diagnose 403 With Evidence

Compare the exact same URL from local public internet, VPS, and crawler container. Capture status, redirect chain, final domain, server/CDN headers, challenge markers, DNS, IP family, and low-frequency behavior.

Compare against a known historical successful task: deployment SHA, real network writes, URL, headers, DNS, and egress path. A configured `COOKIE` mode does not prove a cookie is needed. A Cloudflare challenge affecting only VPS egress is an IP/network-path issue; do not work around it.

## Required Verification

Before declaring success, verify:

- stable identities are unique;
- repeated runs are idempotent;
- catalog count matches source evidence;
- title/body/source URL belong to the same chapter;
- approved count equals mapping count, formal count, API total, and H5 visible count;
- pending content is unavailable publicly;
- ordinary users cannot read VIP bodies;
- the public/free source has no regression;
- production source and tasks end in a safe state.

Read [references/xbookcn-postmortem.md](references/xbookcn-postmortem.md) when diagnosing Blogger catalogs, duplicate raw records, incomplete VIP publication, or Cloudflare 403 incidents.

