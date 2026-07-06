# TODO

## P0 - Crawler Stability

- [ ] Let the current `23qb_public` task finish and verify final task status.
- [ ] Verify newly merged chapters contain complete正文 in the H5 reader.
- [ ] Confirm existing mapped chapters are skipped on repeated collection.
- [ ] Add/confirm automatic raw staging cleanup after successful merge.
- [ ] Add/confirm short retention for failed raw data.
- [ ] Monitor MySQL binlog and disk usage after the next full crawl.
- [ ] Confirm completed novel status is captured and displayed.
- [ ] Confirm category association for newly collected books.

## P0 - User Experience

- [ ] Continue global H5 UI consistency pass: spacing, button size, nav style, empty states.
- [ ] Improve book detail page layout and return-to-home entry.
- [ ] Improve reader navigation and bottom controls.
- [ ] Move reader settings into profile/settings area as the unified setting entry.
- [ ] Ensure reading history re-enters the last read chapter and auto-scrolls the chapter list to that chapter.
- [ ] Add/remove bookshelf behavior should be clear and reversible.
- [ ] Render `br` and paragraph breaks as real line breaks, never literal tags.
- [ ] Improve long chapter list loading with pagination or virtual loading.

## P1 - Admin

- [ ] Improve crawler task management page: run now, stop/stale close, schedule edit, task status.
- [ ] Show source/rank/category collection progress.
- [ ] Add clear data cleanup action for raw staging tables.
- [ ] Improve article chapter management accuracy.
- [ ] Add completed/on-shelf/off-shelf controls to article management.
- [ ] Improve VIP/user/payment visual management.

## P1 - Data Quality

- [ ] Add chapter正文 quality score.
- [ ] Add missing chapter detection by source chapter number and title sequence.
- [ ] Add duplicate book confidence details for admin review.
- [ ] Add category mapping normalization.
- [ ] Add completed novel crawl-skip policy after all chapters are mapped.

## P2 - Operations

- [ ] Add automated disk usage monitor or documented weekly check.
- [ ] Consider disabling MySQL binlog in non-replication environments, or keep one-day retention.
- [ ] Add database backup/export plan before the project becomes production-like.
- [ ] Add a lightweight deployment verification script.
- [ ] Fix README encoding and align it with the new docs.

## Deferred

- [ ] VIP credential-based crawler support.
- [ ] Manual TXT import flow.
- [ ] Multi-source weighted merge and conflict review.
- [ ] Payment gateway integration.
- [ ] Domain name, HTTPS, and CDN setup.
- [ ] Repository visibility back to private after core functionality stabilizes.

