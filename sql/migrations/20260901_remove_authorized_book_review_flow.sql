-- 20260901 爬虫链路改造：移除授权书单功能、移除过滤规则
-- 说明：本迁移会在每次部署时重新执行，因此只保留幂等语句。
--       一次性数据翻转（CONTENT_READY -> PENDING_REVIEW）已移入
--       sql/migrations/20260901_23qb_direct_publish.sql 的幂等纠正逻辑。

-- 1) 删除授权书单相关表（授权书、审计、raw 修复游标）——幂等
DROP TABLE IF EXISTS mini_novel_crawler.crawler_authorized_book_audit;
DROP TABLE IF EXISTS mini_novel_crawler.crawler_authorized_book;
DROP TABLE IF EXISTS mini_novel_crawler.xbookcn_raw_repair_cursor;

-- 2) 关闭自动合并（授权源走审核队列；公开源由 20260901_23qb_direct_publish.sql 恢复）——幂等
UPDATE mini_novel_crawler.crawl_schedule SET auto_merge = 0, updated_at = NOW()
WHERE auto_merge = 1;

-- 3) xbookcn_authorized 源保留禁用状态（解析器已移除，无法再爬）
