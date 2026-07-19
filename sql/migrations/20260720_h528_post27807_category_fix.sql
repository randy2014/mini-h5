USE mini_novel_crawler;

UPDATE crawl_book_raw
SET category_name = NULL,
    updated_at = NOW()
WHERE source_code = 'h528_authorized'
  AND source_book_id = '27807'
  AND UPPER(TRIM(COALESCE(category_name, ''))) IN ('AUTHORIZED_VIP', 'UNKNOWN');

UPDATE crawler_authorized_book
SET category_name = NULL,
    updated_at = NOW()
WHERE source_code = 'h528_authorized'
  AND source_book_id = '27807'
  AND UPPER(TRIM(COALESCE(category_name, ''))) IN ('AUTHORIZED_VIP', 'UNKNOWN');

USE mini_novel;

INSERT INTO vip_category(name, normalized_name, sort, enabled)
VALUES ('其他', '其他', 999, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), sort = VALUES(sort), enabled = 1, updated_at = NOW();

SET @h528_other_vip_category_id := (
  SELECT id FROM vip_category WHERE normalized_name = '其他' LIMIT 1
);

INSERT INTO novel_vip_category_mapping(novel_id, vip_category_id, source_code, source_book_id, source_category_name)
SELECT nsm.novel_id, @h528_other_vip_category_id, nsm.source_code, nsm.source_book_id, NULL
FROM novel_source_mapping nsm
WHERE nsm.source_code = 'h528_authorized'
  AND nsm.source_book_id = '27807'
  AND nsm.novel_id IS NOT NULL
ON DUPLICATE KEY UPDATE
  vip_category_id = VALUES(vip_category_id),
  source_code = VALUES(source_code),
  source_book_id = VALUES(source_book_id),
  source_category_name = NULL,
  updated_at = NOW();
