-- 20260901 爬虫链路改造：移除授权书单功能、移除过滤规则、统一内容审核流
-- 目标链路：爬取 -> 内容审核(PENDING_REVIEW) -> 审核通过 -> 小说库
-- 说明：该迁移针对已有生产数据；新装环境直接使用 schema.sql（已不含下列表）。

-- 1) 删除授权书单相关表（授权书、审计、raw 修复游标）
DROP TABLE IF EXISTS mini_novel_crawler.crawler_authorized_book_audit;
DROP TABLE IF EXISTS mini_novel_crawler.crawler_authorized_book;
DROP TABLE IF EXISTS mini_novel_crawler.xbookcn_raw_repair_cursor;

-- 2) 关闭自动合并：新流程下数据一律进入审核队列，不再自动清洗入库
UPDATE mini_novel_crawler.crawl_schedule SET auto_merge = 0, updated_at = NOW()
WHERE auto_merge = 1;

-- 3) 存量暂存数据纳入统一审核流：
--    有正文的 CONTENT_READY 章节 -> PENDING_REVIEW（等待人工审核）
--    对应书籍内容状态同步为 PENDING_REVIEW
UPDATE mini_novel_crawler.crawl_chapter_raw c
JOIN mini_novel_crawler.crawl_content_raw r ON r.chapter_raw_id = c.id
SET c.content_status = 'PENDING_REVIEW', c.updated_at = NOW()
WHERE c.content_status = 'CONTENT_READY' AND r.content_length > 0;

UPDATE mini_novel_crawler.crawl_book_raw b
SET b.content_status = 'PENDING_REVIEW', b.updated_at = NOW()
WHERE b.content_status = 'CONTENT_READY'
  AND EXISTS (
    SELECT 1 FROM mini_novel_crawler.crawl_chapter_raw c
    WHERE c.book_raw_id = b.id AND c.content_status = 'PENDING_REVIEW'
  );

-- 4) 存量 RISK_BLOCKED（历史风险拦截）章节改为待审核，由人工决定去留
UPDATE mini_novel_crawler.crawl_chapter_raw
SET content_status = 'PENDING_REVIEW', updated_at = NOW()
WHERE content_status = 'RISK_BLOCKED';

-- 5) 已入库数据不动；xbookcn_authorized 源保留禁用状态（解析器已移除，无法再爬）
