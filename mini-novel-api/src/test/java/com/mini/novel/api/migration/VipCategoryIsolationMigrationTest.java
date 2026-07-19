package com.mini.novel.api.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VipCategoryIsolationMigrationTest {
    private static final Path MIGRATION = Path.of("sql/migrations/20260719_vip_category_isolation.sql");
    private static final Path MODULE_MIGRATION = Path.of("../sql/migrations/20260719_vip_category_isolation.sql");

    @Test
    void h528TagsCleanupIsOptionalWhenColumnIsMissing() throws Exception {
        String sql = migrationSql();

        assertTrue(sql.contains("information_schema.COLUMNS"));
        assertTrue(sql.contains("COLUMN_NAME = 'tags_json'"));
        assertTrue(sql.contains("@has_h528_tags_json > 0"));
        assertTrue(sql.contains("skip h528 tags_json cleanup: column missing"));
        assertTrue(sql.contains("PREPARE h528_tags_stmt"));
        assertTrue(sql.contains("EXECUTE h528_tags_stmt"));
    }

    @Test
    void h528TagsCleanupStillRunsWhenColumnExists() throws Exception {
        String sql = migrationSql();

        assertTrue(sql.contains("UPDATE mini_novel_crawler.crawl_book_raw"));
        assertTrue(sql.contains("SET tags_json = CASE"));
        assertTrue(sql.contains("WHERE source_code = ''h528_authorized''"));
    }

    @Test
    void migrationCanRerunAfterPartialSuccess() throws Exception {
        String sql = migrationSql();

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vip_category"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS novel_vip_category_mapping"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vip_source_category_mapping"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE name=VALUES(name)"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE\r\n  source_category_name=VALUES(source_category_name)")
                || sql.contains("ON DUPLICATE KEY UPDATE\n  source_category_name=VALUES(source_category_name)"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE\r\n  vip_category_id=VALUES(vip_category_id)")
                || sql.contains("ON DUPLICATE KEY UPDATE\n  vip_category_id=VALUES(vip_category_id)"));
    }

    private String migrationSql() throws Exception {
        Path path = Files.exists(MIGRATION) ? MIGRATION : MODULE_MIGRATION;
        return Files.readString(path);
    }
}
