-- 多媒体池子（Media Pool）核心表（2026-09 需求 v5）
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，风格同现有 migrations

USE mini_novel;

CREATE TABLE IF NOT EXISTS media_asset (
  id BIGINT NOT NULL AUTO_INCREMENT,
  file_type VARCHAR(8) NOT NULL COMMENT 'IMAGE / VIDEO',
  original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  md5 CHAR(32) NOT NULL COMMENT '文件 md5（去重）',
  size_bytes BIGINT NOT NULL COMMENT '原始上传大小',
  width INT DEFAULT NULL COMMENT '成品宽',
  height INT DEFAULT NULL COMMENT '成品高',
  duration_ms BIGINT DEFAULT NULL COMMENT '视频时长(ms)',
  main_path VARCHAR(512) NOT NULL COMMENT '成品相对路径(图片jpg/视频mp4)',
  thumb_path VARCHAR(512) NOT NULL COMMENT '缩略图相对路径(必生成)',
  poster_path VARCHAR(512) DEFAULT NULL COMMENT '视频封面帧相对路径',
  status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/READY/FAILED',
  fail_reason VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
  operator_id BIGINT DEFAULT NULL COMMENT '上传运营',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_md5 (md5),
  KEY idx_status (status),
  KEY idx_type_time (file_type, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多媒体素材文件';

CREATE TABLE IF NOT EXISTS media_post (
  id BIGINT NOT NULL AUTO_INCREMENT,
  channel_id BIGINT DEFAULT NULL COMMENT '挂载的订阅频道(草稿期可空/下架后保留)',
  title VARCHAR(120) NOT NULL COMMENT '标题(必填)',
  type VARCHAR(8) NOT NULL DEFAULT 'IMAGE' COMMENT 'IMAGE/VIDEO/MIXED(自动判定,无纯文本帖)',
  cover_asset_id BIGINT DEFAULT NULL COMMENT '封面素材(默认视频封面帧或首图)',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT=草稿 / PUBLISHED=已发布',
  operator_id BIGINT DEFAULT NULL COMMENT '操作运营',
  published_at DATETIME DEFAULT NULL COMMENT '发布时间(倒序依据)',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_channel_status (channel_id, status, id),
  KEY idx_status (status, id),
  CONSTRAINT chk_media_post_status CHECK (status IN ('DRAFT','PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订阅频道多媒体内容(草稿/已发布)';

CREATE TABLE IF NOT EXISTS media_post_asset (
  id BIGINT NOT NULL AUTO_INCREMENT,
  post_id BIGINT NOT NULL COMMENT '内容帖 id',
  asset_id BIGINT NOT NULL COMMENT '素材 id',
  seq INT NOT NULL COMMENT '素材顺序(0=首素材/封面)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_seq (post_id, seq),
  KEY idx_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容帖-素材有序关联';
