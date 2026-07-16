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
  'novel69h_authorized',
  '69hnovel authorized single-article PoC',
  'https://www.69hnovel.com',
  'AUTHORIZED_VIP',
  'NONE',
  JSON_OBJECT(
    'isolation', JSON_OBJECT(
      'reviewOnly', true,
      'targetAudience', 'VIP_MANUAL_REVIEW'
    ),
    'poc', JSON_OBJECT(
      'singleBookOnly', true,
      'metadataOnly', false,
      'bookUrl', 'https://www.69hnovel.com/erotic-novel/story/article-12608.html'
    ),
    'rankRules', JSON_OBJECT(
      'bookList', '.L-main-col a[href*=\"/erotic-novel/\"][href*=\"article-\"]'
    ),
    'chapterRules', JSON_OBJECT(
      'content', 'article || .L-main-col article',
      'removeSelectors', JSON_ARRAY('script', 'style', 'iframe', 'noscript', '.M-banner', '.M-aside-nav', '.table-box', '.iframebox', '.iframe-outbox', '.article-page', '.pagination', '.prev-next', '.breadcrumb', '.adsbygoogle'),
      'minContentLength', 80,
      'maxPages', 1
    ),
    'riskRules', JSON_OBJECT(
      'enabled', true,
      'blockedTerms', JSON_ARRAY()
    )
  ),
  0,
  69,
  'Strictly isolated authorized single-article PoC; disabled by default; no batch crawling.'
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

SET @novel69h_source_id := (SELECT id FROM crawl_source WHERE source_code = 'novel69h_authorized' LIMIT 1);

INSERT INTO crawl_rank_source (
  source_id,
  rank_name,
  rank_type,
  rank_url,
  prefer_completed,
  max_books,
  enabled
) SELECT
  @novel69h_source_id,
  '69hnovel authorized single-article PoC',
  'SINGLE_ARTICLE_AUTHORIZED',
  'https://www.69hnovel.com/erotic-novel.html',
  1,
  1,
  0
WHERE @novel69h_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @novel69h_source_id
      AND rank_type = 'SINGLE_ARTICLE_AUTHORIZED'
  );
