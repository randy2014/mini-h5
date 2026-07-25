USE mini_novel;

SET @has_vip_category_default := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'mini_novel'
    AND TABLE_NAME = 'vip_category'
    AND COLUMN_NAME = 'is_default'
);

SET @add_vip_category_default_sql := IF(
  @has_vip_category_default = 0,
  'ALTER TABLE vip_category ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0 AFTER enabled',
  'SELECT ''skip vip_category.is_default: column exists'' AS message'
);
PREPARE add_vip_category_default_stmt FROM @add_vip_category_default_sql;
EXECUTE add_vip_category_default_stmt;
DEALLOCATE PREPARE add_vip_category_default_stmt;

CREATE TEMPORARY TABLE tmp_fixed_vip_category (
  name VARCHAR(64) NOT NULL PRIMARY KEY,
  sort INT NOT NULL,
  is_default TINYINT(1) NOT NULL DEFAULT 0
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_fixed_vip_category(name, sort, is_default) VALUES
('家庭乱伦', 10, 0),
('学生校园', 20, 0),
('武侠科幻', 30, 0),
('都市生活', 40, 0),
('人妻熟女', 50, 0),
('名人明星', 60, 0),
('其他', 999, 1)
ON DUPLICATE KEY UPDATE sort=VALUES(sort), is_default=VALUES(is_default);

INSERT INTO vip_category(name, normalized_name, sort, enabled, is_default, created_at, updated_at)
SELECT name, name, sort, 1, is_default, NOW(), NOW()
FROM tmp_fixed_vip_category
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  sort=VALUES(sort),
  enabled=1,
  is_default=VALUES(is_default),
  updated_at=NOW();

UPDATE vip_category vc
LEFT JOIN tmp_fixed_vip_category fixed ON fixed.name = vc.normalized_name
SET vc.is_default = 0,
    vc.updated_at = NOW()
WHERE fixed.name IS NULL
  AND vc.is_default = 1;

CREATE TEMPORARY TABLE tmp_vip_category_classify (
  old_vip_category_id BIGINT PRIMARY KEY,
  target_vip_category_id BIGINT NOT NULL
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_vip_category_classify(old_vip_category_id, target_vip_category_id)
SELECT old.id,
       target.id
FROM vip_category old
JOIN vip_category target
  ON target.normalized_name = CASE
    WHEN old.name IS NULL OR TRIM(old.name) = ''
      OR UPPER(TRIM(old.name)) IN ('UNKNOWN','AUTHORIZED_VIP','VIP_AUTH_REVIEW')
      THEN '其他'
    WHEN old.name REGEXP '家庭|乱伦|亂倫|母子|父女|兄妹|姐弟|姐妹'
      THEN '家庭乱伦'
    WHEN old.name REGEXP '学生|學生|校园|校園|师生|師生|同学|同學|老师|老師'
      THEN '学生校园'
    WHEN old.name REGEXP '武侠|武俠|科幻|玄幻|仙侠|仙俠|修真|古典'
      THEN '武侠科幻'
    WHEN old.name REGEXP '都市|生活|职场|職場|白领|白領'
      THEN '都市生活'
    WHEN old.name REGEXP '人妻|熟女|少妇|少婦|妈妈|媽媽|阿姨'
      THEN '人妻熟女'
    WHEN old.name REGEXP '名人|明星|偶像|娱乐圈|娛樂圈|女星'
      THEN '名人明星'
    ELSE '其他'
  END
WHERE target.normalized_name IN (SELECT name FROM tmp_fixed_vip_category)
ON DUPLICATE KEY UPDATE target_vip_category_id=VALUES(target_vip_category_id);

UPDATE novel_vip_category_mapping nvm
JOIN tmp_vip_category_classify c ON c.old_vip_category_id = nvm.vip_category_id
SET nvm.vip_category_id = c.target_vip_category_id,
    nvm.updated_at = NOW()
WHERE nvm.vip_category_id <> c.target_vip_category_id;

UPDATE novel_vip_category_mapping nvm
JOIN vip_category target
  ON target.normalized_name = CASE
    WHEN nvm.source_category_name IS NULL OR TRIM(nvm.source_category_name) = ''
      OR UPPER(TRIM(nvm.source_category_name)) IN ('UNKNOWN','AUTHORIZED_VIP','VIP_AUTH_REVIEW')
      THEN '其他'
    WHEN nvm.source_category_name REGEXP '家庭|乱伦|亂倫|母子|父女|兄妹|姐弟|姐妹'
      THEN '家庭乱伦'
    WHEN nvm.source_category_name REGEXP '学生|學生|校园|校園|师生|師生|同学|同學|老师|老師'
      THEN '学生校园'
    WHEN nvm.source_category_name REGEXP '武侠|武俠|科幻|玄幻|仙侠|仙俠|修真|古典'
      THEN '武侠科幻'
    WHEN nvm.source_category_name REGEXP '都市|生活|职场|職場|白领|白領'
      THEN '都市生活'
    WHEN nvm.source_category_name REGEXP '人妻|熟女|少妇|少婦|妈妈|媽媽|阿姨'
      THEN '人妻熟女'
    WHEN nvm.source_category_name REGEXP '名人|明星|偶像|娱乐圈|娛樂圈|女星'
      THEN '名人明星'
    ELSE '其他'
  END
SET nvm.vip_category_id = target.id,
    nvm.updated_at = NOW()
WHERE target.normalized_name IN (SELECT name FROM tmp_fixed_vip_category)
  AND nvm.vip_category_id <> target.id;

INSERT INTO vip_source_category_mapping(source_code, source_category_name, normalized_name, vip_category_id, enabled, created_at, updated_at)
SELECT seed.source_code,
       seed.source_category_name,
       seed.normalized_name,
       target.id,
       1,
       NOW(),
       NOW()
FROM (
  SELECT DISTINCT
         b.source_code,
         LEFT(COALESCE(TRIM(b.category_name), ''), 64) AS source_category_name,
         LEFT(REPLACE(REPLACE(REPLACE(LOWER(COALESCE(TRIM(b.category_name), '')), ' ', ''), '-', ''), '_', ''), 64) AS normalized_name,
         CASE
           WHEN b.category_name IS NULL OR TRIM(b.category_name) = ''
             OR UPPER(TRIM(b.category_name)) IN ('UNKNOWN','AUTHORIZED_VIP','VIP_AUTH_REVIEW')
             THEN '其他'
           WHEN b.category_name REGEXP '家庭|乱伦|亂倫|母子|父女|兄妹|姐弟|姐妹'
             THEN '家庭乱伦'
           WHEN b.category_name REGEXP '学生|學生|校园|校園|师生|師生|同学|同學|老师|老師'
             THEN '学生校园'
           WHEN b.category_name REGEXP '武侠|武俠|科幻|玄幻|仙侠|仙俠|修真|古典'
             THEN '武侠科幻'
           WHEN b.category_name REGEXP '都市|生活|职场|職場|白领|白領'
             THEN '都市生活'
           WHEN b.category_name REGEXP '人妻|熟女|少妇|少婦|妈妈|媽媽|阿姨'
             THEN '人妻熟女'
           WHEN b.category_name REGEXP '名人|明星|偶像|娱乐圈|娛樂圈|女星'
             THEN '名人明星'
           ELSE '其他'
         END AS target_name
  FROM mini_novel_crawler.crawl_book_raw b
  JOIN mini_novel_crawler.crawl_source s
    ON s.source_code = b.source_code
   AND s.source_type = 'AUTHORIZED_VIP'
  UNION ALL
  SELECT source_code, '', '', '其他'
  FROM mini_novel_crawler.crawl_source
  WHERE source_type = 'AUTHORIZED_VIP'
  UNION ALL
  SELECT source_code, 'UNKNOWN', 'unknown', '其他'
  FROM mini_novel_crawler.crawl_source
  WHERE source_type = 'AUTHORIZED_VIP'
  UNION ALL
  SELECT source_code, 'AUTHORIZED_VIP', 'authorizedvip', '其他'
  FROM mini_novel_crawler.crawl_source
  WHERE source_type = 'AUTHORIZED_VIP'
  UNION ALL
  SELECT source_code, 'VIP_AUTH_REVIEW', 'vipauthreview', '其他'
  FROM mini_novel_crawler.crawl_source
  WHERE source_type = 'AUTHORIZED_VIP'
) seed
JOIN vip_category target ON target.normalized_name = seed.target_name
ON DUPLICATE KEY UPDATE
  source_category_name=VALUES(source_category_name),
  vip_category_id=VALUES(vip_category_id),
  enabled=1,
  updated_at=NOW();

UPDATE vip_source_category_mapping vscm
JOIN vip_category vc ON vc.id = vscm.vip_category_id
JOIN tmp_vip_category_classify c ON c.old_vip_category_id = vc.id
SET vscm.vip_category_id = c.target_vip_category_id,
    vscm.enabled = 1,
    vscm.updated_at = NOW()
WHERE vscm.vip_category_id <> c.target_vip_category_id;

DELETE vc
FROM vip_category vc
LEFT JOIN tmp_fixed_vip_category fixed ON fixed.name = vc.normalized_name
LEFT JOIN novel_vip_category_mapping nvm ON nvm.vip_category_id = vc.id
LEFT JOIN vip_source_category_mapping vscm ON vscm.vip_category_id = vc.id
WHERE fixed.name IS NULL
  AND nvm.id IS NULL
  AND vscm.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_vip_category_classify;
DROP TEMPORARY TABLE IF EXISTS tmp_fixed_vip_category;
