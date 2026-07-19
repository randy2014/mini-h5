USE mini_novel_crawler;

UPDATE crawl_source
SET rule_config_json = JSON_OBJECT(
    'isolation', JSON_OBJECT(
      'reviewOnly', true,
      'targetAudience', 'VIP_MANUAL_REVIEW'
    ),
    'poc', JSON_OBJECT(
      'mode', 'BATCH',
      'singleBookOnly', false,
      'metadataOnly', false
    ),
    'rankRules', JSON_OBJECT(
      'bookList', '.L-main-col a[href*="/erotic-novel/"][href*="article-"], main a[href*="/erotic-novel/"][href*="article-"]'
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
  enabled = 0,
  remark = 'Strictly isolated authorized article batch source; disabled by default; pending review only.',
  updated_at = NOW()
WHERE source_code = 'novel69h_authorized';

SET @novel69h_source_id := (SELECT id FROM crawl_source WHERE source_code = 'novel69h_authorized' LIMIT 1);

UPDATE crawl_rank_source
SET rank_name = '69hnovel authorized article batch',
    rank_type = 'CATEGORY_AUTHORIZED',
    rank_url = 'https://www.69hnovel.com/erotic-novel.html',
    prefer_completed = 1,
    max_books = 20,
    enabled = 0,
    updated_at = NOW()
WHERE source_id = @novel69h_source_id;

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
  '69hnovel authorized article batch',
  'CATEGORY_AUTHORIZED',
  'https://www.69hnovel.com/erotic-novel.html',
  1,
  20,
  0
WHERE @novel69h_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @novel69h_source_id
  );
