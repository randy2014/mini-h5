USE mini_novel;

SET @expires_at_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'mini_novel' AND table_name = 'vip_invitation_code' AND column_name = 'expires_at'
);
SET @expires_at_sql = IF(
  @expires_at_exists = 0,
  'ALTER TABLE vip_invitation_code ADD COLUMN expires_at DATETIME NULL AFTER last_used_at',
  'SELECT 1'
);
PREPARE expires_at_stmt FROM @expires_at_sql;
EXECUTE expires_at_stmt;
DEALLOCATE PREPARE expires_at_stmt;
