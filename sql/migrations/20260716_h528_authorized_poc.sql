USE mini_novel_crawler;

INSERT INTO crawl_source (
  source_code,
  name,
  base_url,
  source_type,
  auth_mode,
  rule_config_json,
  enabled,
  priority,
  remark
) VALUES (
  'h528_authorized',
  'h528 authorized single-post PoC',
  'http://www.h528.com',
  'AUTHORIZED_VIP',
  'NONE',
  JSON_OBJECT(
    'isolation', JSON_OBJECT(
      'reviewOnly', true,
      'targetAudience', 'VIP_MANUAL_REVIEW'
    ),
    'poc', JSON_OBJECT(
      'mode', 'BATCH',
      'singleBookOnly', false,
      'metadataOnly', false,
      'bookUrl', 'http://www.h528.com/post/28936.html'
    ),
    'rankRules', JSON_OBJECT(
      'bookList', '.post h2 a[href], h3 a[rel=bookmark][href]'
    ),
    'chapterRules', JSON_OBJECT(
      'content', '.post .entry || .entry',
      'removeSelectors', JSON_ARRAY('script', 'style', 'iframe', '.navigation', '.sidebar', '.postmetadata', '.alignleft', '.alignright'),
      'minContentLength', 80,
      'maxPages', 1
    ),
    'riskRules', JSON_OBJECT(
      'enabled', true,
      'blockedTerms', JSON_ARRAY()
    )
  ),
  0,
  70,
  'Strictly isolated authorized h528 single-post-as-book source; disabled by default; batch crawling is manually controlled.'
) ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  base_url = VALUES(base_url),
  source_type = VALUES(source_type),
  auth_mode = VALUES(auth_mode),
  rule_config_json = VALUES(rule_config_json),
  enabled = 0,
  priority = VALUES(priority),
  remark = VALUES(remark),
  updated_at = NOW();

SET @h528_source_id := (SELECT id FROM crawl_source WHERE source_code = 'h528_authorized' LIMIT 1);

INSERT INTO crawl_rank_source (
  source_id,
  rank_name,
  rank_type,
  rank_url,
  prefer_completed,
  max_books,
  enabled
) SELECT
  @h528_source_id,
  'h528 authorized latest posts',
  'H528_AUTHORIZED_POSTS',
  'http://www.h528.com/',
  1,
  20,
  0
WHERE @h528_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @h528_source_id
      AND rank_type = 'H528_AUTHORIZED_POSTS'
  );

UPDATE crawl_rank_source r
JOIN crawl_source s ON s.id = r.source_id
SET r.rank_name = 'h528 authorized latest posts',
    r.rank_type = 'H528_AUTHORIZED_POSTS',
    r.rank_url = 'http://www.h528.com/',
    r.max_books = 20,
    r.enabled = 0,
    r.updated_at = NOW()
WHERE s.source_code = 'h528_authorized';
