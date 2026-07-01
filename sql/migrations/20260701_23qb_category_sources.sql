CREATE DATABASE IF NOT EXISTS mini_novel DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mini_novel;

INSERT INTO category (name, sort) VALUES
  ('言情小说', 110),
  ('都市小说', 120),
  ('耽美百合', 130),
  ('穿越时空', 140),
  ('青春校园', 150),
  ('玄幻魔法', 160),
  ('修真武侠', 170),
  ('历史军事', 180),
  ('游戏竞技', 190),
  ('科幻空间', 200),
  ('悬疑惊悚', 210),
  ('同人小说', 220),
  ('官场职场', 230)
ON DUPLICATE KEY UPDATE
  sort = VALUES(sort);

CREATE DATABASE IF NOT EXISTS mini_novel_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
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
  '23qb_public',
  '铅笔小说公开分类',
  'https://www.23qb.net',
  'PUBLIC',
  'NONE',
  JSON_OBJECT(
    'rankRules', JSON_OBJECT(
      'bookList', '.module-items .module-item',
      'bookUrl', '.module-item-title@href',
      'bookName', '.module-item-title',
      'author', '.module-item-text'
    ),
    'bookRules', JSON_OBJECT(
      'name', 'meta[property=og:novel:book_name]',
      'author', 'meta[property=og:novel:author]',
      'intro', 'meta[property=og:description]',
      'cover', 'meta[property=og:image]@content',
      'categoryName', 'meta[property=og:novel:category]',
      'catalogUrl', 'meta[property=og:novel:read_url]@content',
      'sourceBookId', 'meta[property=og:url]@content'
    ),
    'catalogRules', JSON_OBJECT(
      'maxChapters', 3000
    ),
    'chapterRules', JSON_OBJECT(
      'content', '.article-content',
      'removeSelectors', JSON_ARRAY('script', 'style', '.adsbygoogle', '.readinline', '.article-page'),
      'minContentLength', 80,
      'maxPages', 1,
      'rejectPatterns', JSON_ARRAY('请登录', '请订阅', '购买本章', '本章未完')
    )
  ),
  1,
  30,
  '铅笔小说首页13个分类公开章节采集源，每个分类默认采集前20本。'
) ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  base_url = VALUES(base_url),
  source_type = VALUES(source_type),
  auth_mode = VALUES(auth_mode),
  rule_config_json = VALUES(rule_config_json),
  enabled = VALUES(enabled),
  priority = VALUES(priority),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;

SET @source_id := (SELECT id FROM crawl_source WHERE source_code = '23qb_public' LIMIT 1);

CREATE TEMPORARY TABLE IF NOT EXISTS tmp_23qb_rank_sources (
  rank_name VARCHAR(64) NOT NULL,
  rank_type VARCHAR(32) NOT NULL,
  rank_url VARCHAR(512) NOT NULL,
  sort_no INT NOT NULL
) ENGINE=Memory;

DELETE FROM tmp_23qb_rank_sources;

INSERT INTO tmp_23qb_rank_sources (rank_name, rank_type, rank_url, sort_no) VALUES
  ('言情小说', '23QB_CATEGORY_01', 'https://www.23qb.net/book/lastupdate_0_1_0_0_0_0_0_1_0.html', 10),
  ('都市小说', '23QB_CATEGORY_02', 'https://www.23qb.net/book/lastupdate_0_2_0_0_0_0_0_1_0.html', 20),
  ('耽美百合', '23QB_CATEGORY_03', 'https://www.23qb.net/book/lastupdate_0_3_0_0_0_0_0_1_0.html', 30),
  ('穿越时空', '23QB_CATEGORY_04', 'https://www.23qb.net/book/lastupdate_0_4_0_0_0_0_0_1_0.html', 40),
  ('青春校园', '23QB_CATEGORY_05', 'https://www.23qb.net/book/lastupdate_0_5_0_0_0_0_0_1_0.html', 50),
  ('玄幻魔法', '23QB_CATEGORY_06', 'https://www.23qb.net/book/lastupdate_0_6_0_0_0_0_0_1_0.html', 60),
  ('修真武侠', '23QB_CATEGORY_07', 'https://www.23qb.net/book/lastupdate_0_7_0_0_0_0_0_1_0.html', 70),
  ('历史军事', '23QB_CATEGORY_08', 'https://www.23qb.net/book/lastupdate_0_8_0_0_0_0_0_1_0.html', 80),
  ('游戏竞技', '23QB_CATEGORY_09', 'https://www.23qb.net/book/lastupdate_0_9_0_0_0_0_0_1_0.html', 90),
  ('科幻空间', '23QB_CATEGORY_10', 'https://www.23qb.net/book/lastupdate_0_10_0_0_0_0_0_1_0.html', 100),
  ('悬疑惊悚', '23QB_CATEGORY_11', 'https://www.23qb.net/book/lastupdate_0_11_0_0_0_0_0_1_0.html', 110),
  ('同人小说', '23QB_CATEGORY_12', 'https://www.23qb.net/book/lastupdate_0_12_0_0_0_0_0_1_0.html', 120),
  ('官场职场', '23QB_CATEGORY_13', 'https://www.23qb.net/book/lastupdate_0_13_0_0_0_0_0_1_0.html', 130);

UPDATE crawl_rank_source r
JOIN tmp_23qb_rank_sources t ON r.rank_name = t.rank_name
SET r.rank_type = t.rank_type,
    r.rank_url = t.rank_url,
    r.prefer_completed = 0,
    r.max_books = 20,
    r.enabled = 1,
    r.updated_at = CURRENT_TIMESTAMP
WHERE r.source_id = @source_id;

INSERT INTO crawl_rank_source (source_id, rank_name, rank_type, rank_url, prefer_completed, max_books, enabled)
SELECT @source_id, t.rank_name, t.rank_type, t.rank_url, 0, 20, 1
FROM tmp_23qb_rank_sources t
WHERE @source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM crawl_rank_source r
    WHERE r.source_id = @source_id
      AND r.rank_name = t.rank_name
  )
ORDER BY t.sort_no;

UPDATE crawl_schedule
SET schedule_times = '00:00,08:00,14:00',
    timezone = 'Asia/Shanghai',
    crawl_public = 1,
    crawl_vip = 0,
    auto_merge = 1,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @source_id
  AND name = '铅笔小说13分类公开章节采集';

INSERT INTO crawl_schedule (
  name,
  source_id,
  credential_id,
  schedule_times,
  timezone,
  crawl_public,
  crawl_vip,
  auto_merge,
  enabled
)
SELECT
  '铅笔小说13分类公开章节采集',
  @source_id,
  NULL,
  '00:00,08:00,14:00',
  'Asia/Shanghai',
  1,
  0,
  1,
  1
WHERE @source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM crawl_schedule
    WHERE source_id = @source_id
      AND name = '铅笔小说13分类公开章节采集'
  );

DROP TEMPORARY TABLE IF EXISTS tmp_23qb_rank_sources;
