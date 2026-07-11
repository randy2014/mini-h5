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
  'xbookcn_authorized',
  'xbookcn authorized PoC',
  'https://book.xbookcn.net',
  'AUTHORIZED_VIP',
  'COOKIE',
  JSON_OBJECT(
    'isolation', JSON_OBJECT(
      'reviewOnly', true,
      'targetAudience', 'VIP_MANUAL_REVIEW'
    ),
    'poc', JSON_OBJECT(
      'singleBookOnly', true,
      'bookUrl', ''
    ),
    'catalogRules', JSON_OBJECT(
      'maxPages', 20,
      'maxChapters', 5000
    ),
    'chapterRules', JSON_OBJECT(
      'content', '.chapter-content || #chapterContent || .read-content || .content || article',
      'removeSelectors', JSON_ARRAY('script', 'style', '.ad', '.ads', '.nav', '.pager', '.chapter-nav'),
      'minContentLength', 80,
      'maxPages', 8
    ),
    'riskRules', JSON_OBJECT(
      'enabled', true,
      'blockedTerms', JSON_ARRAY()
    )
  ),
  0,
  80,
  'Strictly isolated authorized PoC; disabled by default and limited to one approved book.'
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

SET @xbookcn_source_id := (SELECT id FROM crawl_source WHERE source_code = 'xbookcn_authorized' LIMIT 1);

INSERT INTO crawl_rank_source (
  source_id,
  rank_name,
  rank_type,
  rank_url,
  prefer_completed,
  max_books,
  enabled
) SELECT
  @xbookcn_source_id,
  'xbookcn authorized single-book PoC',
  'SINGLE_BOOK_AUTHORIZED',
  'https://book.xbookcn.net/',
  1,
  1,
  0
WHERE @xbookcn_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @xbookcn_source_id
      AND rank_type = 'SINGLE_BOOK_AUTHORIZED'
  );
