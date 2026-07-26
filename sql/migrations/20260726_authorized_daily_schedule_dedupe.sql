USE mini_novel_crawler;

SET @h528_source_id := (SELECT id FROM crawl_source WHERE source_code = 'h528_authorized' LIMIT 1);
SET @novel69h_source_id := (SELECT id FROM crawl_source WHERE source_code = 'novel69h_authorized' LIMIT 1);

SET @h528_rank_id := (
  SELECT MIN(id)
  FROM crawl_rank_source
  WHERE source_id = @h528_source_id
    AND rank_type = 'H528_AUTHORIZED_POSTS'
);

UPDATE crawl_rank_source
SET enabled = CASE WHEN id = @h528_rank_id THEN 1 ELSE 0 END,
    max_books = CASE WHEN id = @h528_rank_id THEN 200 ELSE max_books END,
    updated_at = NOW()
WHERE source_id = @h528_source_id
  AND rank_type = 'H528_AUTHORIZED_POSTS'
  AND @h528_rank_id IS NOT NULL;

SET @novel69h_rank_id := (
  SELECT MIN(id)
  FROM crawl_rank_source
  WHERE source_id = @novel69h_source_id
    AND rank_type = 'CATEGORY_AUTHORIZED'
);

UPDATE crawl_rank_source
SET enabled = CASE WHEN id = @novel69h_rank_id THEN 1 ELSE 0 END,
    max_books = CASE WHEN id = @novel69h_rank_id THEN 100 ELSE max_books END,
    updated_at = NOW()
WHERE source_id = @novel69h_source_id
  AND rank_type = 'CATEGORY_AUTHORIZED'
  AND @novel69h_rank_id IS NOT NULL;
