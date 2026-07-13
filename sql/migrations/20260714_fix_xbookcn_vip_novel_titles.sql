UPDATE mini_novel.novel n
LEFT JOIN mini_novel_crawler.crawl_book_raw br
  ON br.id = CASE
    WHEN n.source_url LIKE '%#rawBook=%' THEN CAST(SUBSTRING_INDEX(n.source_url, '#rawBook=', -1) AS UNSIGNED)
    ELSE NULL
  END
  AND br.source_code = 'xbookcn_authorized'
LEFT JOIN mini_novel_crawler.crawler_authorized_book ab_by_id
  ON ab_by_id.id = CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(br.raw_json, '$.authorizedBookId')), 'null') AS UNSIGNED)
LEFT JOIN mini_novel_crawler.crawler_authorized_book ab_by_source
  ON ab_by_source.source_code = br.source_code
  AND ab_by_source.source_book_id = br.source_book_id
LEFT JOIN mini_novel_crawler.crawler_authorized_book ab_by_url
  ON ab_by_url.source_code = 'xbookcn_authorized'
  AND ab_by_url.book_url = n.source_url
SET n.title = COALESCE(ab_by_id.title, ab_by_source.title, ab_by_url.title),
    n.updated_at = NOW()
WHERE n.vip_required = 1
  AND n.status <> 0
  AND n.source_url LIKE '%book.xbookcn.net%'
  AND HEX(n.title) = 'E995BFE7AF87E68890E4BABAE68385E889B2E5B08FE8AFB4'
  AND COALESCE(ab_by_id.id, ab_by_source.id, ab_by_url.id) IS NOT NULL
  AND COALESCE(ab_by_id.title, ab_by_source.title, ab_by_url.title) IS NOT NULL
  AND TRIM(COALESCE(ab_by_id.title, ab_by_source.title, ab_by_url.title)) <> ''
  AND HEX(COALESCE(ab_by_id.title, ab_by_source.title, ab_by_url.title)) <> 'E995BFE7AF87E68890E4BABAE68385E889B2E5B08FE8AFB4'
  AND (
    br.id IS NULL
    OR br.source_book_id = COALESCE(ab_by_id.source_book_id, ab_by_source.source_book_id, ab_by_url.source_book_id)
  );
