-- ============================================================================
-- Mini H5 · 数据库完整脚本（唯一入口，幂等，可重复执行）
-- ----------------------------------------------------------------------------
-- 内容 = 当前库结构（业务库 mini_novel + 采集暂存库 mini_novel_crawler）
--        + 必须的预置配置 + 必要的幂等数据修正
-- 状态 = 由历次迁移合并而成的「当前形态」：43 张表 / 441 个字段
--
-- 用法
--   docker exec -i mini-novel-mysql mysql --default-character-set=utf8mb4 \
--     -uroot -p"$MYSQL_ROOT_PASSWORD" < sql/schema.sql
--   常规路径由 deploy/deploy.sh 在每次部署时自动执行（部署前会先等 MySQL healthy）
--   本地全新环境由 docker compose 的 MySQL 初始化挂载执行
--
-- 特性与安全
--   · 全部 CREATE TABLE IF NOT EXISTS，可重复执行；
--   · 预置数据均为 ON DUPLICATE KEY UPDATE 语义，不会覆盖运营改动；
--   · 仅包含一处 DROP TABLE IF EXISTS：清理已废弃的授权书单三张表；
--   · 不含任何示例小说/demo 用户等本地开发数据（需要时用 sql/seed_demo.sql）。
--
-- 修改规则（重要：本文件是唯一入口，不要再新增 sql/migrations 下的分散脚本）
--   1. 结构变更写在 §4「后续变更区」，语句必须幂等：
--      ADD COLUMN / ADD INDEX 使用 information_schema + PREPARE 守卫写法（§4 有模板）；
--   2. 结构变更同时请更新 §2 中对应表的 CREATE TABLE 定义（新库直接建对，老库靠 §3 补齐）；
--   3. 任何改动先在测试库连跑两遍，确认第二遍无报错、无数据变化。
-- ============================================================================

-- ============================================================================
-- §1  数据库与废弃对象清理
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `mini_novel` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `mini_novel_crawler` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 会话字符集：保证中文注释、预置数据在任何客户端默认设置下都按 utf8mb4 写入
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 已废弃对象（历史上由迁移创建，现不再使用）：
--   mini_novel_crawler.crawler_authorized_book_audit（来自 sql/migrations/20260901_remove_authorized_book_review_flow.sql）
DROP TABLE IF EXISTS `mini_novel_crawler`.`crawler_authorized_book_audit`;
--   mini_novel_crawler.crawler_authorized_book（来自 sql/migrations/20260901_remove_authorized_book_review_flow.sql）
DROP TABLE IF EXISTS `mini_novel_crawler`.`crawler_authorized_book`;
--   mini_novel_crawler.xbookcn_raw_repair_cursor（来自 sql/migrations/20260901_remove_authorized_book_review_flow.sql）
DROP TABLE IF EXISTS `mini_novel_crawler`.`xbookcn_raw_repair_cursor`;

-- ============================================================================
-- §2  表结构 · mini_novel（33 张表）
-- ============================================================================

USE `mini_novel`;

CREATE TABLE IF NOT EXISTS `category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类主键 ID',
  `name` VARCHAR(64) NOT NULL COMMENT '分类名称，如玄幻、都市、仙侠',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说分类表，维护 H5 首页、分类页和后台分类管理使用的分类字典';

CREATE TABLE IF NOT EXISTS `novel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '小说主键 ID',
  `title` VARCHAR(128) NOT NULL COMMENT '小说标题',
  `author` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '作者名称',
  `cover_url` VARCHAR(512) NULL COMMENT '封面图片地址',
  `intro` TEXT NULL COMMENT '小说简介',
  `category_id` BIGINT NULL COMMENT '分类 ID，关联 category.id',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '小说状态枚举：0=下架，1=连载，2=完结',
  `vip_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否 VIP 小说：0=否，1=是；H5 用于区分 VIP 展示',
  `free_chapter_count` INT NOT NULL DEFAULT 0 COMMENT '免费章节数量，超过后可按 VIP 权限控制',
  `word_count` BIGINT NOT NULL DEFAULT 0 COMMENT '总字数，来自采集或人工维护',
  `latest_chapter_id` BIGINT NULL COMMENT '最新章节 ID，关联 chapter.id',
  `latest_chapter_title` VARCHAR(255) NULL COMMENT '最新章节标题冗余字段',
  `source_url` VARCHAR(512) NULL COMMENT '来源页面地址或人工导入来源标识',
  `offline_reason` VARCHAR(255) NULL COMMENT '下架原因',
  `offline_at` DATETIME NULL COMMENT '下架时间',
  `operator_id` BIGINT NULL COMMENT '最近一次后台操作人 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_category_id (category_id),
  KEY idx_updated_at (updated_at),
  KEY idx_title_author (title, author)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务小说主表，仅保存已通过清洗、可在 H5 展示或管理端维护的小说';

CREATE TABLE IF NOT EXISTS `chapter` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '章节主键 ID',
  `novel_id` BIGINT NOT NULL COMMENT '小说 ID，关联 novel.id',
  `chapter_no` INT NOT NULL COMMENT '章节序号，同一本小说内唯一',
  `title` VARCHAR(255) NOT NULL COMMENT '章节标题',
  `content` LONGTEXT NULL COMMENT '章节正文；兼容旧结构，后续大正文优先使用 chapter_content',
  `is_vip` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否 VIP 章节：0=免费，1=VIP',
  `price_coin` INT NOT NULL DEFAULT 0 COMMENT '章节价格，单位为站内虚拟币；0 表示免费或 VIP 包含',
  `source_url` VARCHAR(512) NULL COMMENT '章节来源地址',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_novel_chapter_no (novel_id, chapter_no),
  KEY idx_novel_id (novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务章节表，保存 H5 阅读端可读取的章节元数据和正文';

CREATE TABLE IF NOT EXISTS `app_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键 ID',
  `nickname` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '用户昵称',
  `avatar` VARCHAR(512) NULL COMMENT '头像地址',
  `mobile` VARCHAR(32) NULL COMMENT '手机号，唯一',
  `email` VARCHAR(128) NULL COMMENT '邮箱，唯一',
  `password_hash` VARCHAR(255) NULL COMMENT '登录密码哈希',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '用户状态枚举：0=禁用，1=正常',
  `vip_status` TINYINT NOT NULL DEFAULT 0 COMMENT 'VIP 状态：0=非 VIP，1=有效期 VIP，2=永久 VIP',
  `vip_expire_time` DATETIME NULL COMMENT 'VIP 到期时间',
  `vip_source` VARCHAR(32) NULL COMMENT 'VIP 来源：INVITATION/ADMIN/ORDER',
  `vip_activated_at` DATETIME NULL COMMENT 'VIP 最近激活时间',
  `vip_disabled_at` DATETIME NULL COMMENT 'VIP 最近停用/降级时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_mobile (mobile),
  UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='前台用户表，保存 H5 登录用户基础资料和 VIP 状态';

CREATE TABLE IF NOT EXISTS `user_bookshelf` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '书架记录主键 ID',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID，关联 app_user.id',
  `novel_id` BIGINT NOT NULL COMMENT '小说 ID，关联 novel.id',
  `last_chapter_id` BIGINT NULL COMMENT '最近阅读章节 ID',
  `progress` INT NOT NULL DEFAULT 0 COMMENT '阅读进度百分比或客户端进度值',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_user_novel (user_id, novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户书架表，记录用户收藏小说和最近阅读进度';

CREATE TABLE IF NOT EXISTS `user_read_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '阅读历史主键 ID',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID，关联 app_user.id',
  `novel_id` BIGINT NOT NULL COMMENT '小说 ID，关联 novel.id',
  `chapter_id` BIGINT NOT NULL COMMENT '章节 ID，关联 chapter.id',
  `progress` INT NOT NULL DEFAULT 0 COMMENT '章节内阅读进度',
  `read_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
  KEY idx_user_read_at (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户阅读历史表，记录用户最近阅读章节和阅读时间';

CREATE TABLE IF NOT EXISTS `vip_plan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '套餐主键 ID',
  `name` VARCHAR(64) NOT NULL COMMENT '套餐名称',
  `duration_days` INT NOT NULL COMMENT '套餐有效天数',
  `price` DECIMAL(10,2) NOT NULL COMMENT '销售价格',
  `original_price` DECIMAL(10,2) NULL COMMENT '原价/划线价',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0=停用，1=启用',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  `description` VARCHAR(255) NULL COMMENT '套餐说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='VIP 套餐表，后台维护可购买或可授予的 VIP 套餐';

CREATE TABLE IF NOT EXISTS `user_vip` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户 VIP 主键 ID',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID，关联 app_user.id',
  `vip_plan_id` BIGINT NULL COMMENT 'VIP 套餐 ID，关联 vip_plan.id；后台手工调整可为空',
  `start_time` DATETIME NOT NULL COMMENT 'VIP 开始时间',
  `end_time` DATETIME NOT NULL COMMENT 'VIP 结束时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '权益状态枚举：0=失效，1=生效',
  `source_order_id` BIGINT NULL COMMENT '来源订单 ID，关联 vip_order.id',
  `source_type` VARCHAR(32) NULL COMMENT '权益来源类型：INVITATION/ADMIN/ORDER',
  `source_ref_id` BIGINT NULL COMMENT '来源记录 ID，如邀请记录或订单 ID',
  `operator_id` BIGINT NULL COMMENT '后台操作人 ID',
  `remark` VARCHAR(255) NULL COMMENT '权益备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_user_end_time (user_id, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户 VIP 权益表，记录用户当前或历史 VIP 有效期';

CREATE TABLE IF NOT EXISTS `vip_invitation_code` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `owner_user_id` BIGINT NOT NULL,
  `code` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  `total_quota` INT NOT NULL DEFAULT 3,
  `used_quota` INT NOT NULL DEFAULT 0,
  `remaining_quota` INT NOT NULL DEFAULT 3,
  `is_current` TINYINT(1) NOT NULL DEFAULT 1,
  `generated_at` DATETIME NOT NULL,
  `enabled_at` DATETIME,
  `disabled_at` DATETIME,
  `revoked_at` DATETIME,
  `last_used_at` DATETIME,
  `expires_at` DATETIME,
  `replaced_by_code_id` BIGINT,
  `operator_id` BIGINT,
  `remark` VARCHAR(255),
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code),
  KEY idx_owner_current (owner_user_id, is_current),
  KEY idx_status (status),
  KEY idx_owner_status (owner_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vip_invitation_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `invitation_code_id` BIGINT NOT NULL,
  `code_snapshot` VARCHAR(32) NOT NULL,
  `inviter_user_id` BIGINT NOT NULL,
  `invitee_user_id` BIGINT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVATED',
  `activated_at` DATETIME,
  `remark` VARCHAR(255),
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_invitee_user (invitee_user_id),
  KEY idx_inviter_created (inviter_user_id, created_at),
  KEY idx_code_id (invitation_code_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vip_operation_audit` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `action` VARCHAR(64) NOT NULL,
  `target_user_id` BIGINT,
  `target_code_id` BIGINT,
  `target_invitation_record_id` BIGINT,
  `before_json` JSON,
  `after_json` JSON,
  `operator_id` BIGINT,
  `reason` VARCHAR(255),
  `request_id` VARCHAR(64),
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_request_id (request_id),
  KEY idx_target_user_created (target_user_id, created_at),
  KEY idx_target_code_created (target_code_id, created_at),
  KEY idx_action_created (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `vip_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单主键 ID',
  `order_no` VARCHAR(64) NOT NULL COMMENT '订单号，唯一',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID，关联 app_user.id',
  `vip_plan_id` BIGINT NOT NULL COMMENT '套餐 ID，关联 vip_plan.id',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  `pay_status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态枚举：0=待支付，1=已支付，2=已取消，3=已退款',
  `pay_channel` VARCHAR(32) NULL COMMENT '支付渠道，如 wechat、alipay、manual',
  `paid_at` DATETIME NULL COMMENT '支付完成时间',
  `expire_at` DATETIME NULL COMMENT '订单过期时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='VIP 订单表，记录用户购买 VIP 的订单和支付状态';

CREATE TABLE IF NOT EXISTS `crawl_source` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '旧版采集源主键 ID',
  `name` VARCHAR(64) NOT NULL COMMENT '旧版采集源名称',
  `base_url` VARCHAR(512) NOT NULL COMMENT '旧版采集源基础地址',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0=停用，1=启用',
  `rule_config_json` JSON NULL COMMENT '旧版解析规则 JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旧版采集源表，早期单体采集配置，后续以 mini_novel_crawler.crawl_source 为准';

CREATE TABLE IF NOT EXISTS `crawl_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '旧版任务主键 ID',
  `source_id` BIGINT NOT NULL COMMENT '旧版采集源 ID',
  `novel_id` BIGINT NULL COMMENT '关联业务小说 ID',
  `task_type` VARCHAR(32) NOT NULL COMMENT '任务类型，如 LIST、DETAIL、CHAPTER',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态枚举：0=待执行，1=成功，2=失败',
  `message` VARCHAR(1024) NULL COMMENT '任务执行消息或失败原因',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `started_at` DATETIME NULL COMMENT '开始时间',
  `finished_at` DATETIME NULL COMMENT '结束时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_source_created_at (source_id, created_at),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旧版采集任务表，早期单体任务记录，后续以 mini_novel_crawler.crawl_task_v2 为准';

CREATE TABLE IF NOT EXISTS `novel_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '身份主键 ID',
  `canonical_title` VARCHAR(128) NOT NULL COMMENT '标准书名',
  `canonical_author` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '标准作者',
  `normalized_title` VARCHAR(128) NOT NULL COMMENT '归一化书名，用于去重匹配',
  `normalized_author` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '归一化作者，用于去重匹配',
  `novel_id` BIGINT NULL COMMENT '已合并到的业务小说 ID，关联 novel.id',
  `match_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '身份状态枚举：ACTIVE=有效，MERGED=已合并，IGNORED=忽略',
  `confidence_score` INT NOT NULL DEFAULT 100 COMMENT '匹配置信度，0-100',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_identity_norm (normalized_title, normalized_author),
  KEY idx_novel_id (novel_id),
  KEY idx_match_status (match_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说统一身份表，用于跨采集源识别同一本小说，避免重复入库';

CREATE TABLE IF NOT EXISTS `novel_source_mapping` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '来源映射主键 ID',
  `identity_id` BIGINT NOT NULL COMMENT '小说统一身份 ID，关联 novel_identity.id',
  `novel_id` BIGINT NULL COMMENT '业务小说 ID，关联 novel.id；未入库时为空',
  `source_code` VARCHAR(64) NOT NULL COMMENT '采集源编码',
  `source_book_id` VARCHAR(128) NOT NULL COMMENT '来源站点书籍 ID',
  `source_url` VARCHAR(512) NULL COMMENT '来源站点书籍地址',
  `source_title` VARCHAR(128) NOT NULL COMMENT '来源站点书名',
  `source_author` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '来源站点作者',
  `content_status` VARCHAR(32) NOT NULL DEFAULT 'META_ONLY' COMMENT '内容状态枚举：META_ONLY=仅元数据，CATALOG_READY=目录已抓，CONTENT_READY=正文可用，PENDING_REVIEW=待审核，FAILED=失败，IGNORED=忽略',
  `match_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '匹配状态枚举：PENDING=待匹配，MATCHED=已匹配，MERGED=已入库，PARTIAL_MERGED=部分入库，PENDING_REVIEW=待审核，FAILED=失败，IGNORED=忽略',
  `confidence_score` INT NOT NULL DEFAULT 0 COMMENT '来源匹配置信度，0-100',
  `last_crawled_at` DATETIME NULL COMMENT '最近采集时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_source_book (source_code, source_book_id),
  KEY idx_identity_id (identity_id),
  KEY idx_novel_id (novel_id),
  KEY idx_match_status (match_status),
  KEY idx_content_status (content_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说来源映射表，记录各采集源书籍与统一身份/业务小说的关系';

CREATE TABLE IF NOT EXISTS `chapter_source_mapping` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '章节映射主键 ID',
  `novel_mapping_id` BIGINT NOT NULL COMMENT '小说来源映射 ID，关联 novel_source_mapping.id',
  `chapter_id` BIGINT NULL COMMENT '业务章节 ID，关联 chapter.id；未入库时为空',
  `source_chapter_id` VARCHAR(128) NOT NULL COMMENT '来源站点章节 ID',
  `source_url` VARCHAR(512) NULL COMMENT '来源章节地址',
  `source_title` VARCHAR(255) NOT NULL COMMENT '来源章节标题',
  `chapter_no` INT NOT NULL COMMENT '来源章节序号',
  `is_vip` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否来源 VIP 章节：0=免费，1=VIP',
  `content_hash` CHAR(64) NULL COMMENT '来源正文哈希',
  `content_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '内容状态枚举：PENDING=待处理，MERGED=已入库，PENDING_REVIEW=待审核，FAILED=失败，IGNORED=忽略',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_source_chapter (novel_mapping_id, source_chapter_id),
  KEY idx_chapter_id (chapter_id),
  KEY idx_content_status (content_status),
  KEY idx_chapter_no (novel_mapping_id, chapter_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节来源映射表，记录来源章节与业务章节的对应关系和清洗状态';

CREATE TABLE IF NOT EXISTS `vip_category` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `name` VARCHAR(64) NOT NULL,
  `normalized_name` VARCHAR(64) NOT NULL,
  `sort` INT NOT NULL DEFAULT 100,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `is_default` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_vip_category_normalized_name (normalized_name),
  KEY idx_vip_category_enabled_sort (enabled, sort, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Independent VIP category table';

CREATE TABLE IF NOT EXISTS `novel_vip_category_mapping` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `novel_id` BIGINT NOT NULL,
  `vip_category_id` BIGINT NOT NULL,
  `source_code` VARCHAR(64) NOT NULL,
  `source_book_id` VARCHAR(128) NOT NULL,
  `source_category_name` VARCHAR(64),
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_novel_vip_category (novel_id),
  KEY idx_vip_category_novel (vip_category_id, novel_id),
  KEY idx_source_book (source_code, source_book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Novel to independent VIP category mapping';

CREATE TABLE IF NOT EXISTS `vip_source_category_mapping` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `source_code` VARCHAR(64) NOT NULL,
  `source_category_name` VARCHAR(64) NOT NULL,
  `normalized_name` VARCHAR(64) NOT NULL,
  `vip_category_id` BIGINT NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_vip_source_category (source_code, normalized_name),
  KEY idx_vip_source_category_target (vip_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Source category to independent VIP category mapping';

CREATE TABLE IF NOT EXISTS `chapter_content` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '正文记录主键 ID',
  `chapter_id` BIGINT NOT NULL COMMENT '章节 ID，关联 chapter.id，一章一条正文记录',
  `content` LONGTEXT NULL COMMENT '章节完整正文，MySQL LONGTEXT 最大约 4GB 字节',
  `content_hash` VARCHAR(64) NULL COMMENT '正文 SHA-256 或其他哈希，用于去重和变更检测',
  `storage_type` VARCHAR(16) NOT NULL DEFAULT 'MYSQL' COMMENT '正文存储方式枚举：MYSQL=存 MySQL，FILE=文件/对象存储',
  `content_path` VARCHAR(512) NULL COMMENT '当 storage_type=FILE 时的文件或对象存储路径',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_chapter_id (chapter_id),
  KEY idx_content_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节正文扩展表，用于承载 LONGTEXT 正文、正文哈希和外部存储路径';

CREATE TABLE IF NOT EXISTS `chapter_segment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分段主键 ID',
  `chapter_id` BIGINT NOT NULL COMMENT '章节 ID，关联 chapter.id',
  `segment_no` INT NOT NULL COMMENT '分段序号，同一章节内唯一',
  `content` LONGTEXT NULL COMMENT '分段正文内容',
  `word_count` INT NOT NULL DEFAULT 0 COMMENT '分段字数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_chapter_segment (chapter_id, segment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节分段表，预留给超长章节切片、分页阅读或全文索引使用';

CREATE TABLE IF NOT EXISTS `vip_adjust_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '调整日志主键 ID',
  `user_id` BIGINT NOT NULL COMMENT '被调整用户 ID',
  `action` VARCHAR(32) NOT NULL COMMENT '调整动作枚举：GRANT=开通/延长，REVOKE=取消，EXPIRE=置为过期，CORRECT=纠正',
  `before_expire_time` DATETIME NULL COMMENT '调整前 VIP 到期时间',
  `after_expire_time` DATETIME NULL COMMENT '调整后 VIP 到期时间',
  `before_status` VARCHAR(32) NULL COMMENT '调整前状态',
  `after_status` VARCHAR(32) NULL COMMENT '调整后状态',
  `days` INT NULL COMMENT '调整天数，正数延长，负数扣减',
  `reason` VARCHAR(255) NULL COMMENT '调整原因',
  `operator_id` BIGINT NULL COMMENT '后台操作人 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_user_created (user_id, created_at),
  KEY idx_operator_created (operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='VIP 后台调整日志表，记录管理员直接调整用户 VIP 状态的审计信息';

CREATE TABLE IF NOT EXISTS `subscribe_channel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(64) NOT NULL COMMENT '频道名称',
  `sort` INT NOT NULL DEFAULT 100 COMMENT '排序',
  `status` VARCHAR(16) NOT NULL DEFAULT 'OFFLINE' COMMENT 'PUBLISHED=已发布 / OFFLINE=已下架',
  `cover` VARCHAR(512) DEFAULT NULL COMMENT '封面（非必填）',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '简介（非必填）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订阅频道分类（独立于 vip_category）';

CREATE TABLE IF NOT EXISTS `subscribe_channel_novel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `channel_id` BIGINT NOT NULL COMMENT '订阅频道 id',
  `novel_id` BIGINT NOT NULL COMMENT '小说 id',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_channel_novel (channel_id, novel_id),
  KEY idx_novel (novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说-订阅频道映射（加入频道）';

CREATE TABLE IF NOT EXISTS `user_coin_balance` (
  `user_id` BIGINT NOT NULL,
  `balance` BIGINT NOT NULL DEFAULT 0 COMMENT '快乐币余额',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='快乐币余额';

CREATE TABLE IF NOT EXISTS `user_coin_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `change_amount` BIGINT NOT NULL COMMENT '变动量（正=增加，负=扣减）',
  `balance_after` BIGINT NOT NULL COMMENT '变动后余额',
  `biz_type` VARCHAR(32) NOT NULL COMMENT 'RECHARGE=后台充值 / SUBSCRIBE=订阅扣费 / REFUND=冲正 / GRANT=赠送',
  `biz_id` VARCHAR(64) DEFAULT NULL COMMENT '业务 id（如订阅记录 id）',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人（后台充值）',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='快乐币流水';

CREATE TABLE IF NOT EXISTS `user_subscribe` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `channel_id` BIGINT NOT NULL COMMENT '订阅频道 id',
  `period_type` VARCHAR(16) NOT NULL COMMENT 'WEEK / MONTH / QUARTER / YEAR',
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / EXPIRED / CANCELLED',
  `cost_coins` BIGINT NOT NULL DEFAULT 0 COMMENT '扣币数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id, status),
  KEY idx_channel (channel_id, status),
  KEY idx_end_time (end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订阅关系';

CREATE TABLE IF NOT EXISTS `ticket` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '提交人用户 id',
  `title` VARCHAR(100) NOT NULL COMMENT '标题',
  `content` VARCHAR(300) NOT NULL COMMENT '正文（标题+正文合计 ≤300 字）',
  `status` VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN=待处理 / CLOSED=已关闭',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单';

CREATE TABLE IF NOT EXISTS `ticket_reply` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `ticket_id` BIGINT NOT NULL COMMENT '工单 id',
  `content` VARCHAR(1000) NOT NULL COMMENT '回复内容',
  `replier_type` VARCHAR(16) NOT NULL COMMENT 'USER=用户 / ADMIN=管理员',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ticket (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单回复';

CREATE TABLE IF NOT EXISTS `media_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `file_type` VARCHAR(8) NOT NULL COMMENT 'IMAGE / VIDEO',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `md5` CHAR(32) NOT NULL COMMENT '文件 md5（去重）',
  `size_bytes` BIGINT NOT NULL COMMENT '原始上传大小',
  `width` INT DEFAULT NULL COMMENT '成品宽',
  `height` INT DEFAULT NULL COMMENT '成品高',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '视频时长(ms)',
  `main_path` VARCHAR(512) DEFAULT NULL COMMENT '成品相对路径(图片jpg/视频mp4；视频转码完成前为空)',
  `thumb_path` VARCHAR(512) DEFAULT NULL COMMENT '缩略图相对路径(图片必生成；视频转码完成后生成)',
  `poster_path` VARCHAR(512) DEFAULT NULL COMMENT '视频封面帧相对路径',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/READY/FAILED',
  `fail_reason` VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
  `operator_id` BIGINT DEFAULT NULL COMMENT '上传运营',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_md5 (md5),
  KEY idx_status (status),
  KEY idx_type_time (file_type, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多媒体素材文件';

CREATE TABLE IF NOT EXISTS `media_post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `channel_id` BIGINT DEFAULT NULL COMMENT '挂载的订阅频道(草稿期可空/下架后保留)',
  `title` VARCHAR(120) NOT NULL COMMENT '标题(必填)',
  `type` VARCHAR(8) NOT NULL DEFAULT 'IMAGE' COMMENT 'IMAGE/VIDEO/MIXED(自动判定,无纯文本帖)',
  `cover_asset_id` BIGINT DEFAULT NULL COMMENT '封面素材(默认视频封面帧或首图)',
  `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT=草稿 / PUBLISHED=已发布',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作运营',
  `published_at` DATETIME DEFAULT NULL COMMENT '发布时间(倒序依据)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_channel_status (channel_id, status, id),
  KEY idx_status (status, id),
  CONSTRAINT chk_media_post_status CHECK (status IN ('DRAFT','PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订阅频道多媒体内容(草稿/已发布)';

CREATE TABLE IF NOT EXISTS `media_post_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_id` BIGINT NOT NULL COMMENT '内容帖 id',
  `asset_id` BIGINT NOT NULL COMMENT '素材 id',
  `seq` INT NOT NULL COMMENT '素材顺序(0=首素材/封面)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_post_seq (post_id, seq),
  KEY idx_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容帖-素材有序关联';

-- ============================================================================
-- §2  表结构 · mini_novel_crawler（10 张表）
-- ============================================================================

USE `mini_novel_crawler`;

CREATE TABLE IF NOT EXISTS `crawl_source` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '采集源主键 ID',
  `source_code` VARCHAR(64) NOT NULL COMMENT '采集源编码，系统内唯一，如 qidian_public',
  `name` VARCHAR(64) NOT NULL COMMENT '采集源名称',
  `base_url` VARCHAR(512) NOT NULL COMMENT '采集源基础域名或入口地址',
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' COMMENT '来源类型枚举：PUBLIC=公开网页，AUTHORIZED_VIP=授权 VIP，IMPORT=手动导入',
  `auth_mode` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '认证方式枚举：NONE=无需认证，PASSWORD=账号密码，COOKIE=Cookie',
  `rule_config_json` JSON,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0=停用，1=启用',
  `priority` INT NOT NULL DEFAULT 100 COMMENT '采集优先级，越小越优先',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_source_code (source_code),
  KEY idx_enabled_priority (enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集源配置表，定义可抓取的网站或导入源';

CREATE TABLE IF NOT EXISTS `crawl_rank_source` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '榜单源主键 ID',
  `source_id` BIGINT NOT NULL COMMENT '采集源 ID，关联 crawl_source.id',
  `rank_name` VARCHAR(64) NOT NULL COMMENT '榜单名称',
  `rank_type` VARCHAR(32) NOT NULL COMMENT '榜单类型枚举：MONTH=月榜，WEEK=周榜，COMPLETED=完结榜，HOT=热度榜',
  `rank_url` VARCHAR(512) NOT NULL COMMENT '榜单页面地址',
  `prefer_completed` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否优先完结作品：0=否，1=是',
  `max_books` INT NOT NULL DEFAULT 50 COMMENT '单次最多采集书籍数',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0=停用，1=启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_source_enabled (source_id, enabled),
  KEY idx_rank_type (rank_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='榜单源配置表，定义月榜、周榜、完结榜等高热度入口';

CREATE TABLE IF NOT EXISTS `crawl_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '调度计划主键 ID',
  `name` VARCHAR(64) NOT NULL COMMENT '计划名称',
  `source_id` BIGINT NULL COMMENT '采集源 ID；为空表示按服务逻辑选择可用源',
  `credential_id` BIGINT NULL COMMENT '授权凭据 ID，关联 crawl_source_credential.id',
  `schedule_times` VARCHAR(64) NOT NULL DEFAULT '00:00,08:00,14:00' COMMENT '每日执行时间列表，逗号分隔，如 00:00,08:00,14:00',
  `timezone` VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '调度时区',
  `crawl_public` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否抓取公开章节：0=否，1=是',
  `crawl_vip` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否抓取授权 VIP 章节：0=否，1=是',
  `auto_merge` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '采集完成后是否自动清洗入库：0=否，1=是',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0=停用，1=启用',
  `last_run_at` DATETIME NULL COMMENT '最近执行时间',
  `next_run_at` DATETIME NULL COMMENT '下次计划执行时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_enabled_next_run (enabled, next_run_at),
  KEY idx_source_id (source_id),
  KEY idx_credential_id (credential_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集调度计划表，维护每日固定时间抓取和清洗配置';

CREATE TABLE IF NOT EXISTS `crawl_source_credential` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '凭据主键 ID',
  `source_id` BIGINT NOT NULL COMMENT '采集源 ID，关联 crawl_source.id',
  `name` VARCHAR(64) NOT NULL COMMENT '凭据名称',
  `auth_mode` VARCHAR(32) NOT NULL DEFAULT 'PASSWORD' COMMENT '认证方式枚举：PASSWORD=账号密码，COOKIE=Cookie',
  `username` VARCHAR(128) NULL COMMENT '登录用户名',
  `password_cipher` VARCHAR(1000) NULL COMMENT '密码密文或加密占位，不在前端明文回显',
  `cookie_text` TEXT NULL COMMENT 'Cookie 文本，用于授权抓取',
  `headers_json` JSON NULL COMMENT '额外请求头 JSON，如 User-Agent',
  `login_url` VARCHAR(512) NULL COMMENT '登录页面地址',
  `status` VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '凭据状态枚举：UNVERIFIED=未校验，VALID=有效，INVALID=无效，EXPIRED=过期',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0=停用，1=启用',
  `last_check_status` VARCHAR(32) NULL COMMENT '最近一次校验结果',
  `last_check_at` DATETIME NULL COMMENT '最近一次校验时间',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_source_enabled (source_id, enabled),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集源账号凭据表，保存授权抓取所需账号、Cookie 和请求头';

CREATE TABLE IF NOT EXISTS `crawl_task_v2` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '采集任务主键 ID',
  `schedule_id` BIGINT NULL COMMENT '调度计划 ID，手动任务可为空',
  `source_id` BIGINT NULL COMMENT '采集源 ID',
  `rank_source_id` BIGINT NULL COMMENT '榜单源 ID',
  `credential_id` BIGINT NULL COMMENT '授权凭据 ID',
  `task_type` VARCHAR(32) NOT NULL COMMENT '任务类型枚举：PUBLIC=公开采集，VIP_AND_PUBLIC=公开+授权 VIP，IMPORT=手动导入',
  `trigger_type` VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '触发方式枚举：MANUAL=手动，SCHEDULE=调度，SYSTEM=系统补偿',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态枚举：PENDING=待执行，RUNNING=执行中，SUCCESS=成功，FAILED=失败',
  `target_url` VARCHAR(512) NULL COMMENT '本次采集目标 URL',
  `total_count` INT NOT NULL DEFAULT 0 COMMENT '发现或计划处理总数',
  `success_count` INT NOT NULL DEFAULT 0 COMMENT '成功处理数量',
  `fail_count` INT NOT NULL DEFAULT 0 COMMENT '失败数量',
  `message` VARCHAR(1000) NULL COMMENT '执行消息或失败原因',
  `started_at` DATETIME NULL COMMENT '开始时间',
  `finished_at` DATETIME NULL COMMENT '结束时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_status_created (status, created_at),
  KEY idx_schedule_id (schedule_id),
  KEY idx_source_id (source_id),
  KEY idx_credential_id (credential_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集任务表，记录每次手动或调度触发的采集执行情况';

CREATE TABLE IF NOT EXISTS `crawl_book_raw` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '原始小说主键 ID',
  `crawl_task_id` BIGINT,
  `source_code` VARCHAR(64) NOT NULL COMMENT '采集源编码',
  `source_book_id` VARCHAR(128) NOT NULL COMMENT '来源站点书籍 ID',
  `source_url` VARCHAR(512) NULL COMMENT '来源站点书籍地址',
  `title` VARCHAR(128) NOT NULL COMMENT '来源书名',
  `author` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '来源作者',
  `intro` TEXT NULL COMMENT '来源简介',
  `cover_url` VARCHAR(512) NULL COMMENT '来源封面地址',
  `category_name` VARCHAR(64) NULL COMMENT '来源分类名称',
  `book_status` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '来源书籍状态枚举：UNKNOWN=未知，SERIALIZING=连载，COMPLETED=完结',
  `word_count` BIGINT NOT NULL DEFAULT 0 COMMENT '来源字数',
  `heat_score` BIGINT NOT NULL DEFAULT 0 COMMENT '热度分或榜单排序分',
  `rank_type` VARCHAR(32) NULL COMMENT '来源榜单类型：MONTH/WEEK/COMPLETED/HOT',
  `content_status` VARCHAR(32) NOT NULL DEFAULT 'META_ONLY' COMMENT '内容状态枚举：META_ONLY=仅元数据，CATALOG_READY=目录已抓，CONTENT_READY=正文已抓，PENDING_REVIEW=待审核，FAILED=失败',
  `raw_json` JSON NULL COMMENT '来源原始 JSON 或解析快照',
  `crawled_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_source_book (source_code, source_book_id),
  KEY idx_crawl_task_id (crawl_task_id),
  KEY idx_source_url (source_code, source_url),
  KEY idx_source_status (source_code, content_status),
  KEY idx_title_author (title, author),
  KEY idx_content_status (content_status),
  KEY idx_rank_heat (rank_type, heat_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集原始小说表，保存采集源返回的书籍元数据，尚未直接进入业务库';

CREATE TABLE IF NOT EXISTS `crawl_chapter_raw` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '原始章节主键 ID',
  `book_raw_id` BIGINT NOT NULL COMMENT '原始小说 ID，关联 crawl_book_raw.id',
  `source_chapter_id` VARCHAR(128) NOT NULL COMMENT '来源站点章节 ID',
  `source_url` VARCHAR(512) NULL COMMENT '来源章节地址',
  `chapter_no` INT NOT NULL COMMENT '章节序号',
  `title` VARCHAR(255) NOT NULL COMMENT '来源章节标题',
  `is_vip` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否来源 VIP 章节：0=免费，1=VIP',
  `price_coin` INT NOT NULL DEFAULT 0 COMMENT '来源章节价格或站内价格，0 表示免费',
  `content_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '正文状态枚举：PENDING=待抓取，CONTENT_READY=正文已抓，PENDING_REVIEW=待审核，FAILED=失败',
  `content_hash` CHAR(64) NULL COMMENT '正文哈希',
  `crawled_at` DATETIME NULL COMMENT '章节采集时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_book_chapter (book_raw_id, source_chapter_id),
  KEY idx_book_no (book_raw_id, chapter_no),
  KEY idx_book_chapter_url (book_raw_id, source_url),
  KEY idx_content_status (content_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集原始章节表，保存来源章节目录和章节级元数据';

CREATE TABLE IF NOT EXISTS `crawl_content_raw` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '原始正文主键 ID',
  `chapter_raw_id` BIGINT NOT NULL COMMENT '原始章节 ID，关联 crawl_chapter_raw.id',
  `content` LONGTEXT NOT NULL COMMENT '原始章节正文，必须是真实正文，不应仅为 URL 或 ID',
  `content_hash` CHAR(64) NOT NULL COMMENT '正文哈希，用于去重和变更检测',
  `content_length` INT NOT NULL DEFAULT 0 COMMENT '正文字符长度',
  `storage_mode` VARCHAR(32) NOT NULL DEFAULT 'MYSQL_LONGTEXT' COMMENT '存储模式枚举：MYSQL_LONGTEXT=MySQL 长文本，FILE=文件/对象存储',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_chapter_content (chapter_raw_id),
  KEY idx_content_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采集原始正文表，保存原始章节正文，清洗通过后再写入业务库';

CREATE TABLE IF NOT EXISTS `crawl_merge_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '清洗任务主键 ID',
  `crawl_task_id` BIGINT NULL COMMENT '来源采集任务 ID，关联 crawl_task_v2.id',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '清洗状态枚举：PENDING=待清洗，MERGING=清洗中，MERGED=全部入库，PARTIAL_MERGED=部分入库，PENDING_REVIEW=待审核，FAILED=失败',
  `total_count` INT NOT NULL DEFAULT 0 COMMENT '处理书籍总数',
  `merged_count` INT NOT NULL DEFAULT 0 COMMENT '成功入库书籍数',
  `pending_review_count` INT NOT NULL DEFAULT 0 COMMENT '待审核书籍数',
  `failed_count` INT NOT NULL DEFAULT 0 COMMENT '失败书籍数',
  `message` VARCHAR(1000) NULL COMMENT '清洗结果说明或失败原因',
  `started_at` DATETIME NULL COMMENT '开始时间',
  `finished_at` DATETIME NULL COMMENT '结束时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_status_created (status, created_at),
  KEY idx_crawl_task_id (crawl_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='清洗入库任务表，记录从采集库清洗到业务库的一次执行结果';

CREATE TABLE IF NOT EXISTS `crawl_merge_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '清洗明细主键 ID',
  `merge_task_id` BIGINT NOT NULL COMMENT '清洗任务 ID，关联 crawl_merge_task.id',
  `book_raw_id` BIGINT NOT NULL COMMENT '原始小说 ID，关联 crawl_book_raw.id',
  `identity_id` BIGINT NULL COMMENT '小说统一身份 ID，关联 mini_novel.novel_identity.id',
  `novel_id` BIGINT NULL COMMENT '业务小说 ID，关联 mini_novel.novel.id；未入库时为空',
  `match_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '明细状态枚举：PENDING=待处理，RETRYING=重新清洗中，MERGED=已入库，PARTIAL_MERGED=部分入库，PENDING_REVIEW=待审核，FAILED=失败，IGNORED=人工忽略',
  `confidence_score` INT NOT NULL DEFAULT 0 COMMENT '匹配置信度，0-100',
  `message` VARCHAR(1000) NULL COMMENT '明细处理说明、待审核原因或失败原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_merge_status (merge_task_id, match_status),
  KEY idx_book_raw_id (book_raw_id),
  KEY idx_novel_id (novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='清洗明细表，记录每本原始小说的匹配、入库、待审核或忽略状态';


-- ============================================================================
-- §3  预置配置与幂等数据修正（顺序即历史执行顺序）
-- ----------------------------------------------------------------------------
-- 说明：这些语句保证「采集源 / 榜单入口 / 调度计划 / VIP 分类」等基础数据存在，
--       并修正历史遗留状态；均可重复执行。
-- 后续结构变更请写在 §4「后续变更区」，不要另建脚本。
-- 注：每个分节标注了「历史来源文件」；这些历史迁移文件已在 2026-09 整理中合并进本文件并删除，
--     内容以本文件为准，需要追溯历史可查 git 记录。
-- ============================================================================


-- ---- 来自 sql/schema.sql ----
USE `mini_novel`;
INSERT INTO category (name, sort) VALUES
('玄幻', 10),
('都市', 20),
('仙侠', 30),
('悬疑', 40)
ON DUPLICATE KEY UPDATE sort = VALUES(sort);
INSERT INTO vip_plan (name, duration_days, price, original_price, enabled, sort, description) VALUES
('月度 VIP', 30, 19.90, 29.90, 1, 10, '30 天 VIP 阅读权益'),
('年度 VIP', 365, 198.00, 298.00, 1, 20, '365 天 VIP 阅读权益')
ON DUPLICATE KEY UPDATE enabled = VALUES(enabled);

-- ---- 来自 sql/migrations/20260629_crawl_task_scope.sql ----
SET @has_crawl_task_id := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'mini_novel_crawler'
    AND TABLE_NAME = 'crawl_book_raw'
    AND COLUMN_NAME = 'crawl_task_id'
);
SET @add_crawl_task_id := IF(
  @has_crawl_task_id = 0,
  'ALTER TABLE mini_novel_crawler.crawl_book_raw ADD COLUMN crawl_task_id BIGINT NULL COMMENT ''采集任务 ID，关联 crawl_task_v2.id，用于限定本次清洗范围'' AFTER id',
  'SELECT 1'
);
PREPARE stmt FROM @add_crawl_task_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @has_idx_crawl_task_id := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'mini_novel_crawler'
    AND TABLE_NAME = 'crawl_book_raw'
    AND INDEX_NAME = 'idx_crawl_task_id'
);
SET @add_idx_crawl_task_id := IF(
  @has_idx_crawl_task_id = 0,
  'ALTER TABLE mini_novel_crawler.crawl_book_raw ADD KEY idx_crawl_task_id (crawl_task_id)',
  'SELECT 1'
);
PREPARE stmt FROM @add_idx_crawl_task_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 来自 sql/migrations/20260629_rule_config_chain.sql ----
SET @add_rule_config_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE mini_novel_crawler.crawl_source ADD COLUMN rule_config_json JSON NULL COMMENT ''采集规则 JSON，定义榜单、详情、目录、章节分页、清洗和质量校验规则'' AFTER auth_mode',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'mini_novel_crawler'
    AND TABLE_NAME = 'crawl_source'
    AND COLUMN_NAME = 'rule_config_json'
);
PREPARE add_rule_config_stmt FROM @add_rule_config_sql;
EXECUTE add_rule_config_stmt;
DEALLOCATE PREPARE add_rule_config_stmt;

-- ---- 来自 sql/migrations/20260701_shuqi_public_seed.sql ----
USE `mini_novel_crawler`;
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
  'shuqi_public',
  '书旗公开免费章节',
  'https://www.shuqi.com',
  'PUBLIC',
  'NONE',
  JSON_OBJECT('parser', 'shuqi', 'freeOnly', true, 'verifiedBookId', '8872073'),
  1,
  20,
  '书旗免费章节链路验证源：仅采集 payStatus=0 的公开免费章节。'
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
SET @shuqi_source_id := (SELECT id FROM crawl_source WHERE source_code = 'shuqi_public' LIMIT 1);
UPDATE crawl_rank_source
SET rank_type = 'SINGLE_BOOK_FREE',
    rank_url = 'https://www.shuqi.com/book/8872073.html',
    prefer_completed = 1,
    max_books = 1,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗单书免费链路验证';
INSERT INTO crawl_rank_source (
  source_id,
  rank_name,
  rank_type,
  rank_url,
  prefer_completed,
  max_books,
  enabled
)
SELECT
  @shuqi_source_id,
  '书旗单书免费链路验证',
  'SINGLE_BOOK_FREE',
  'https://www.shuqi.com/book/8872073.html',
  1,
  1,
  1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM crawl_rank_source
    WHERE source_id = @shuqi_source_id
      AND rank_name = '书旗单书免费链路验证'
  );
UPDATE crawl_schedule
SET schedule_times = '00:00,08:00,14:00',
    timezone = 'Asia/Shanghai',
    crawl_public = 1,
    crawl_vip = 0,
    auto_merge = 1,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND name = '书旗免费章节链路验证';
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
  '书旗免费章节链路验证',
  @shuqi_source_id,
  NULL,
  '00:00,08:00,14:00',
  'Asia/Shanghai',
  1,
  0,
  1,
  1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM crawl_schedule
    WHERE source_id = @shuqi_source_id
      AND name = '书旗免费章节链路验证'
  );

-- ---- 来自 sql/migrations/20260701_shuqi_store_rank_sources.sql ----
SET @shuqi_source_id := (SELECT id FROM crawl_source WHERE source_code = 'shuqi_public' LIMIT 1);
UPDATE crawl_rank_source
SET enabled = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗单书免费链路验证';
UPDATE crawl_schedule
SET name = '书旗榜单免费章节采集',
    schedule_times = '00:00,08:00,14:00',
    crawl_public = 1,
    crawl_vip = 0,
    auto_merge = 1,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND name IN ('书旗免费章节链路验证', '书旗榜单免费章节采集');
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
  '书旗榜单免费章节采集',
  @shuqi_source_id,
  NULL,
  '00:00,08:00,14:00',
  'Asia/Shanghai',
  1,
  0,
  1,
  1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM crawl_schedule
    WHERE source_id = @shuqi_source_id
      AND name = '书旗榜单免费章节采集'
  );
DELETE s
FROM crawl_schedule s
JOIN (
  SELECT MIN(id) AS keep_id
  FROM crawl_schedule
  WHERE source_id = @shuqi_source_id
    AND name = '书旗榜单免费章节采集'
) k
WHERE s.source_id = @shuqi_source_id
  AND s.name = '书旗榜单免费章节采集'
  AND s.id <> k.keep_id;
UPDATE crawl_rank_source
SET rank_type = 'STORE_ALL',
    rank_url = 'https://www.shuqi.com/store?sz=0&fc=0&wd=10&tm=0&st=0',
    prefer_completed = 0,
    max_books = 20,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗书库综合前20';
INSERT INTO crawl_rank_source (source_id, rank_name, rank_type, rank_url, prefer_completed, max_books, enabled)
SELECT @shuqi_source_id, '书旗书库综合前20', 'STORE_ALL',
       'https://www.shuqi.com/store?sz=0&fc=0&wd=10&tm=0&st=0', 0, 20, 1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @shuqi_source_id AND rank_name = '书旗书库综合前20'
  );
UPDATE crawl_rank_source
SET rank_type = 'STORE_MALE',
    rank_url = 'https://www.shuqi.com/store?sz=1&fc=0&wd=10&tm=0&st=0',
    prefer_completed = 0,
    max_books = 20,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗男频前20';
INSERT INTO crawl_rank_source (source_id, rank_name, rank_type, rank_url, prefer_completed, max_books, enabled)
SELECT @shuqi_source_id, '书旗男频前20', 'STORE_MALE',
       'https://www.shuqi.com/store?sz=1&fc=0&wd=10&tm=0&st=0', 0, 20, 1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @shuqi_source_id AND rank_name = '书旗男频前20'
  );
UPDATE crawl_rank_source
SET rank_type = 'STORE_FEMALE',
    rank_url = 'https://www.shuqi.com/store?sz=2&fc=0&wd=10&tm=0&st=0',
    prefer_completed = 0,
    max_books = 20,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗女频前20';
INSERT INTO crawl_rank_source (source_id, rank_name, rank_type, rank_url, prefer_completed, max_books, enabled)
SELECT @shuqi_source_id, '书旗女频前20', 'STORE_FEMALE',
       'https://www.shuqi.com/store?sz=2&fc=0&wd=10&tm=0&st=0', 0, 20, 1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @shuqi_source_id AND rank_name = '书旗女频前20'
  );
UPDATE crawl_rank_source
SET rank_type = 'STORE_COMPLETED',
    rank_url = 'https://www.shuqi.com/store?sz=0&fc=0&wd=10&tm=0&st=2',
    prefer_completed = 1,
    max_books = 20,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗完结前20';
INSERT INTO crawl_rank_source (source_id, rank_name, rank_type, rank_url, prefer_completed, max_books, enabled)
SELECT @shuqi_source_id, '书旗完结前20', 'STORE_COMPLETED',
       'https://www.shuqi.com/store?sz=0&fc=0&wd=10&tm=0&st=2', 1, 20, 1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @shuqi_source_id AND rank_name = '书旗完结前20'
  );

-- ---- 来自 sql/migrations/20260701_23qb_category_sources.sql ----
USE `mini_novel`;
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
USE `mini_novel_crawler`;
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
      'maxPages', 8,
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
SET schedule_times = '04:00',
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
  '04:00',
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

-- ---- 来自 sql/migrations/20260702_23qb_only_crawler_source.sql ----
SET @keep_source_id := (SELECT id FROM crawl_source WHERE source_code = '23qb_public' LIMIT 1);
UPDATE crawl_source
SET enabled = CASE WHEN id = @keep_source_id THEN 1 ELSE 0 END,
    updated_at = CURRENT_TIMESTAMP
WHERE @keep_source_id IS NOT NULL;
UPDATE crawl_rank_source
SET enabled = CASE WHEN source_id = @keep_source_id THEN 1 ELSE 0 END,
    updated_at = CURRENT_TIMESTAMP
WHERE @keep_source_id IS NOT NULL;
UPDATE crawl_schedule
SET enabled = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE @keep_source_id IS NOT NULL
  AND (source_id IS NULL OR source_id <> @keep_source_id);
UPDATE crawl_schedule
SET schedule_times = '04:00',
    timezone = 'Asia/Shanghai',
    crawl_public = 1,
    crawl_vip = 0,
    auto_merge = 1,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE @keep_source_id IS NOT NULL
  AND source_id = @keep_source_id;

-- ---- 来自 sql/migrations/20260714_vip_invitation_expiry.sql ----
USE `mini_novel`;
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

-- ---- 来自 sql/migrations/20260716_h528_authorized_poc.sql ----
USE `mini_novel_crawler`;
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
  'h528_authorized',
  'h528 authorized single-post PoC',
  'http://www.h528.com',
  'AUTHORIZED_VIP',
  'NONE',
  JSON_OBJECT(
    'isolation', JSON_OBJECT(
      'reviewOnly', true,
      'targetAudience', 'VIP_MANUAL_REVIEW'
    ),
    'poc', JSON_OBJECT(
      'mode', 'BATCH',
      'singleBookOnly', false,
      'metadataOnly', false,
      'bookUrl', 'http://www.h528.com/post/28936.html'
    ),
    'rankRules', JSON_OBJECT(
      'bookList', '.post h2 a[href], h3 a[rel=bookmark][href]'
    ),
    'chapterRules', JSON_OBJECT(
      'content', '.post .entry || .entry',
      'removeSelectors', JSON_ARRAY('script', 'style', 'iframe', '.navigation', '.sidebar', '.postmetadata', '.alignleft', '.alignright'),
      'minContentLength', 80,
      'maxPages', 1
    ),
    'riskRules', JSON_OBJECT(
      'enabled', true,
      'blockedTerms', JSON_ARRAY()
    )
  ),
  0,
  70,
  'Strictly isolated authorized h528 single-post-as-book source; disabled by default; batch crawling is manually controlled.'
) ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  base_url = VALUES(base_url),
  source_type = VALUES(source_type),
  auth_mode = VALUES(auth_mode),
  rule_config_json = VALUES(rule_config_json),
  enabled = 0,
  priority = VALUES(priority),
  remark = VALUES(remark),
  updated_at = NOW();
SET @h528_source_id := (SELECT id FROM crawl_source WHERE source_code = 'h528_authorized' LIMIT 1);
INSERT INTO crawl_rank_source (
  source_id,
  rank_name,
  rank_type,
  rank_url,
  prefer_completed,
  max_books,
  enabled
) SELECT
  @h528_source_id,
  'h528 authorized latest posts',
  'H528_AUTHORIZED_POSTS',
  'http://www.h528.com/',
  1,
  20,
  0
WHERE @h528_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @h528_source_id
      AND rank_type = 'H528_AUTHORIZED_POSTS'
  );
UPDATE crawl_rank_source r
JOIN crawl_source s ON s.id = r.source_id
SET r.rank_name = 'h528 authorized latest posts',
    r.rank_type = 'H528_AUTHORIZED_POSTS',
    r.rank_url = 'http://www.h528.com/',
    r.max_books = 20,
    r.enabled = 0,
    r.updated_at = NOW()
WHERE s.source_code = 'h528_authorized';

-- ---- 来自 sql/migrations/20260717_novel69h_authorized_poc.sql ----
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
  'novel69h_authorized',
  '69hnovel authorized single-article PoC',
  'https://www.69hnovel.com',
  'AUTHORIZED_VIP',
  'NONE',
  JSON_OBJECT(
    'isolation', JSON_OBJECT(
      'reviewOnly', true,
      'targetAudience', 'VIP_MANUAL_REVIEW'
    ),
    'poc', JSON_OBJECT(
      'singleBookOnly', true,
      'metadataOnly', false,
      'bookUrl', 'https://www.69hnovel.com/erotic-novel/story/article-12608.html'
    ),
    'rankRules', JSON_OBJECT(
      'bookList', '.L-main-col a[href*=\"/erotic-novel/\"][href*=\"article-\"]'
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
  0,
  69,
  'Strictly isolated authorized single-article PoC; disabled by default; no batch crawling.'
) ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  base_url = VALUES(base_url),
  source_type = VALUES(source_type),
  auth_mode = VALUES(auth_mode),
  rule_config_json = VALUES(rule_config_json),
  enabled = 0,
  priority = VALUES(priority),
  remark = VALUES(remark),
  updated_at = NOW();
SET @novel69h_source_id := (SELECT id FROM crawl_source WHERE source_code = 'novel69h_authorized' LIMIT 1);
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
  '69hnovel authorized single-article PoC',
  'SINGLE_ARTICLE_AUTHORIZED',
  'https://www.69hnovel.com/erotic-novel.html',
  1,
  1,
  0
WHERE @novel69h_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @novel69h_source_id
      AND rank_type = 'SINGLE_ARTICLE_AUTHORIZED'
  );

-- ---- 来自 sql/migrations/20260719_vip_category_isolation.sql ----
USE `mini_novel`;
INSERT INTO vip_category(name, normalized_name, sort, enabled)
VALUES ('其他', '其他', 999, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), sort=VALUES(sort), enabled=1;
INSERT INTO vip_category(name, normalized_name, sort, enabled)
SELECT source_category_name, normalized_name, 100, 1
FROM (
  SELECT DISTINCT
    CASE
      WHEN b.category_name IS NULL OR TRIM(b.category_name) = '' OR UPPER(TRIM(b.category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(TRIM(b.category_name), 64)
    END source_category_name,
    CASE
      WHEN b.category_name IS NULL OR TRIM(b.category_name) = '' OR UPPER(TRIM(b.category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(REPLACE(REPLACE(REPLACE(LOWER(TRIM(b.category_name)), ' ', ''), '-', ''), '_', ''), 64)
    END normalized_name
  FROM novel_source_mapping nsm
  JOIN mini_novel_crawler.crawl_book_raw b
    ON b.source_code = nsm.source_code
   AND b.source_book_id = nsm.source_book_id
  JOIN mini_novel_crawler.crawl_source s
    ON s.source_code = nsm.source_code
   AND s.source_type = 'AUTHORIZED_VIP'
  WHERE nsm.content_status = 'CONTENT_READY'
) seed
ON DUPLICATE KEY UPDATE name=VALUES(name), enabled=1, updated_at=NOW();
INSERT INTO vip_source_category_mapping(source_code, source_category_name, normalized_name, vip_category_id, enabled)
SELECT seed.source_code, seed.source_category_name, seed.normalized_name, vc.id, 1
FROM (
  SELECT DISTINCT
    b.source_code,
    CASE
      WHEN b.category_name IS NULL OR TRIM(b.category_name) = '' OR UPPER(TRIM(b.category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(TRIM(b.category_name), 64)
    END source_category_name,
    CASE
      WHEN b.category_name IS NULL OR TRIM(b.category_name) = '' OR UPPER(TRIM(b.category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(REPLACE(REPLACE(REPLACE(LOWER(TRIM(b.category_name)), ' ', ''), '-', ''), '_', ''), 64)
    END normalized_name
  FROM mini_novel_crawler.crawl_book_raw b
  JOIN mini_novel_crawler.crawl_source s
    ON s.source_code = b.source_code
   AND s.source_type = 'AUTHORIZED_VIP'
) seed
JOIN vip_category vc ON vc.normalized_name = seed.normalized_name
ON DUPLICATE KEY UPDATE
  source_category_name=VALUES(source_category_name),
  vip_category_id=VALUES(vip_category_id),
  enabled=1,
  updated_at=NOW();
INSERT INTO novel_vip_category_mapping(novel_id, vip_category_id, source_code, source_book_id, source_category_name)
SELECT nsm.novel_id, vc.id, nsm.source_code, nsm.source_book_id, seed.source_category_name
FROM novel_source_mapping nsm
JOIN mini_novel_crawler.crawl_book_raw b
  ON b.source_code = nsm.source_code
 AND b.source_book_id = nsm.source_book_id
JOIN mini_novel_crawler.crawl_source s
  ON s.source_code = nsm.source_code
 AND s.source_type = 'AUTHORIZED_VIP'
JOIN (
  SELECT
    source_code,
    source_book_id,
    CASE
      WHEN category_name IS NULL OR TRIM(category_name) = '' OR UPPER(TRIM(category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(TRIM(category_name), 64)
    END source_category_name,
    CASE
      WHEN category_name IS NULL OR TRIM(category_name) = '' OR UPPER(TRIM(category_name)) IN ('UNKNOWN','AUTHORIZED_VIP') THEN '其他'
      ELSE LEFT(REPLACE(REPLACE(REPLACE(LOWER(TRIM(category_name)), ' ', ''), '-', ''), '_', ''), 64)
    END normalized_name
  FROM mini_novel_crawler.crawl_book_raw
) seed
  ON seed.source_code = nsm.source_code
 AND seed.source_book_id = nsm.source_book_id
JOIN vip_category vc ON vc.normalized_name = seed.normalized_name
WHERE nsm.content_status = 'CONTENT_READY'
ON DUPLICATE KEY UPDATE
  vip_category_id=VALUES(vip_category_id),
  source_code=VALUES(source_code),
  source_book_id=VALUES(source_book_id),
  source_category_name=VALUES(source_category_name),
  updated_at=NOW();
SET @has_h528_tags_json := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'mini_novel_crawler'
    AND TABLE_NAME = 'crawl_book_raw'
    AND COLUMN_NAME = 'tags_json'
);
SET @h528_tags_sql := IF(
  @has_h528_tags_json > 0,
  'UPDATE mini_novel_crawler.crawl_book_raw
   SET tags_json = CASE
     WHEN category_name IS NULL OR TRIM(category_name) = '''' OR UPPER(TRIM(category_name)) IN (''UNKNOWN'',''AUTHORIZED_VIP'') THEN ''[]''
     ELSE JSON_ARRAY(TRIM(category_name))
   END,
   updated_at = NOW()
   WHERE source_code = ''h528_authorized''',
  'SELECT ''skip h528 tags_json cleanup: column missing'' AS message'
);
PREPARE h528_tags_stmt FROM @h528_tags_sql;
EXECUTE h528_tags_stmt;
DEALLOCATE PREPARE h528_tags_stmt;

-- ---- 来自 sql/migrations/20260720_novel69h_batch_config.sql ----
USE `mini_novel_crawler`;
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

-- ---- 来自 sql/migrations/20260726_vip_category_converge.sql ----
USE `mini_novel`;
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

-- ---- 来自 sql/migrations/20260726_authorized_daily_schedule.sql ----
USE `mini_novel_crawler`;
SET @now_shanghai := UTC_TIMESTAMP() + INTERVAL 8 HOUR;
SET @next_0200 := IF(
  TIME(@now_shanghai) < '02:00:00',
  TIMESTAMP(DATE(@now_shanghai), '02:00:00'),
  TIMESTAMP(DATE(@now_shanghai) + INTERVAL 1 DAY, '02:00:00')
);
SET @h528_source_id := (SELECT id FROM crawl_source WHERE source_code = 'h528_authorized' LIMIT 1);
SET @novel69h_source_id := (SELECT id FROM crawl_source WHERE source_code = 'novel69h_authorized' LIMIT 1);
SET @xbookcn_source_id := (SELECT id FROM crawl_source WHERE source_code = 'xbookcn_authorized' LIMIT 1);
UPDATE crawl_source
SET enabled = 1,
    rule_config_json = JSON_SET(
      COALESCE(rule_config_json, JSON_OBJECT()),
      '$.rankRules.maxPages', 10
    ),
    updated_at = NOW()
WHERE source_code = 'h528_authorized';
UPDATE crawl_rank_source
SET max_books = 200,
    enabled = 1,
    updated_at = NOW()
WHERE source_id = @h528_source_id
  AND rank_type = 'H528_AUTHORIZED_POSTS';
UPDATE crawl_source
SET enabled = 1,
    rule_config_json = JSON_SET(
      COALESCE(rule_config_json, JSON_OBJECT()),
      '$.rankRules.maxPages', 5
    ),
    updated_at = NOW()
WHERE source_code = 'novel69h_authorized';
UPDATE crawl_rank_source
SET max_books = 100,
    enabled = 1,
    updated_at = NOW()
WHERE source_id = @novel69h_source_id
  AND rank_type = 'CATEGORY_AUTHORIZED';
UPDATE crawl_source
SET enabled = 0,
    updated_at = NOW()
WHERE source_code = 'xbookcn_authorized';
UPDATE crawl_rank_source
SET enabled = 0,
    updated_at = NOW()
WHERE source_id = @xbookcn_source_id;
UPDATE crawl_schedule
SET source_id = @h528_source_id,
    credential_id = NULL,
    schedule_times = '02:00',
    timezone = 'Asia/Shanghai',
    crawl_public = 0,
    crawl_vip = 1,
    auto_merge = 0,
    enabled = 1,
    next_run_at = @next_0200,
    updated_at = NOW()
WHERE name = 'h528 authorized daily review crawl'
  AND @h528_source_id IS NOT NULL;
INSERT INTO crawl_schedule (
  name,
  source_id,
  credential_id,
  schedule_times,
  timezone,
  crawl_public,
  crawl_vip,
  auto_merge,
  enabled,
  last_run_at,
  next_run_at
) SELECT
  'h528 authorized daily review crawl',
  @h528_source_id,
  NULL,
  '02:00',
  'Asia/Shanghai',
  0,
  1,
  0,
  1,
  NULL,
  @next_0200
WHERE @h528_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_schedule WHERE name = 'h528 authorized daily review crawl'
  );
UPDATE crawl_schedule
SET source_id = @novel69h_source_id,
    credential_id = NULL,
    schedule_times = '02:00',
    timezone = 'Asia/Shanghai',
    crawl_public = 0,
    crawl_vip = 1,
    auto_merge = 0,
    enabled = 1,
    next_run_at = @next_0200,
    updated_at = NOW()
WHERE name = '69hnovel authorized daily review crawl'
  AND @novel69h_source_id IS NOT NULL;
INSERT INTO crawl_schedule (
  name,
  source_id,
  credential_id,
  schedule_times,
  timezone,
  crawl_public,
  crawl_vip,
  auto_merge,
  enabled,
  last_run_at,
  next_run_at
) SELECT
  '69hnovel authorized daily review crawl',
  @novel69h_source_id,
  NULL,
  '02:00',
  'Asia/Shanghai',
  0,
  1,
  0,
  1,
  NULL,
  @next_0200
WHERE @novel69h_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_schedule WHERE name = '69hnovel authorized daily review crawl'
  );
UPDATE crawl_schedule
SET enabled = 0,
    updated_at = NOW()
WHERE source_id = @xbookcn_source_id;

-- ---- 来自 sql/migrations/20260726_authorized_daily_schedule_dedupe.sql ----
SET @h528_source_id := (SELECT id FROM crawl_source WHERE source_code = 'h528_authorized' LIMIT 1);
SET @novel69h_source_id := (SELECT id FROM crawl_source WHERE source_code = 'novel69h_authorized' LIMIT 1);
SET @h528_rank_id := (
  SELECT MIN(id)
  FROM crawl_rank_source
  WHERE source_id = @h528_source_id
    AND rank_type = 'H528_AUTHORIZED_POSTS'
);
UPDATE crawl_rank_source
SET enabled = CASE WHEN id = @h528_rank_id THEN 1 ELSE 0 END,
    max_books = CASE WHEN id = @h528_rank_id THEN 200 ELSE max_books END,
    updated_at = NOW()
WHERE source_id = @h528_source_id
  AND rank_type = 'H528_AUTHORIZED_POSTS'
  AND @h528_rank_id IS NOT NULL;
SET @novel69h_rank_id := (
  SELECT MIN(id)
  FROM crawl_rank_source
  WHERE source_id = @novel69h_source_id
    AND rank_type = 'CATEGORY_AUTHORIZED'
);
UPDATE crawl_rank_source
SET enabled = CASE WHEN id = @novel69h_rank_id THEN 1 ELSE 0 END,
    max_books = CASE WHEN id = @novel69h_rank_id THEN 100 ELSE max_books END,
    updated_at = NOW()
WHERE source_id = @novel69h_source_id
  AND rank_type = 'CATEGORY_AUTHORIZED'
  AND @novel69h_rank_id IS NOT NULL;

-- ---- 来自 sql/migrations/20260726_kkxsz_public_source.sql ----
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
  'kkxsz_public',
  '2k小说站授权免费源',
  'https://www.kkxsz.com',
  'PUBLIC',
  'NONE',
  JSON_OBJECT(
    'authorization', JSON_OBJECT(
      'proofRef', 'offline_company_agreement',
      'scope', 'metadata,catalog,chapter_content,storage,free_display'
    ),
    'rankRules', JSON_OBJECT(
      'maxPages', 1
    ),
    'chapterRules', JSON_OBJECT(
      'content', '#content || .content',
      'removeSelectors', JSON_ARRAY(
        'script',
        'style',
        '.top',
        '.headerW',
        '.navW',
        '.searchBoxM',
        '.readNav',
        '.page',
        '.recommend',
        '.chapterPages',
        'a[href*=\"rrssk.com\"]'
      ),
      'minContentLength', 80,
      'maxPages', 1,
      'rejectPatterns', JSON_ARRAY('请登录', '正在手打中', '本章未完')
    ),
    'qualityRules', JSON_OBJECT(
      'requireFullCatalog', true,
      'completeBeforeFreeMerge', true
    )
  ),
  0,
  35,
  '授权免费小说源，默认禁用；受控验证后才启用采集，完整正文才允许进入免费主站。'
) ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  base_url = VALUES(base_url),
  source_type = VALUES(source_type),
  auth_mode = VALUES(auth_mode),
  rule_config_json = VALUES(rule_config_json),
  enabled = 0,
  priority = VALUES(priority),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;
SET @kkxsz_source_id := (SELECT id FROM crawl_source WHERE source_code = 'kkxsz_public' LIMIT 1);
CREATE TEMPORARY TABLE IF NOT EXISTS tmp_kkxsz_rank_sources (
  rank_name VARCHAR(64) NOT NULL,
  rank_type VARCHAR(32) NOT NULL,
  rank_url VARCHAR(512) NOT NULL,
  sort_no INT NOT NULL
) ENGINE=Memory;
DELETE FROM tmp_kkxsz_rank_sources;
INSERT INTO tmp_kkxsz_rank_sources (rank_name, rank_type, rank_url, sort_no) VALUES
  ('玄幻魔法', 'KKXSZ_CATEGORY_04', 'https://www.kkxsz.com/list-4/', 10),
  ('修真武侠', 'KKXSZ_CATEGORY_07', 'https://www.kkxsz.com/list-7/', 20),
  ('都市小说', 'KKXSZ_CATEGORY_08', 'https://www.kkxsz.com/list-8/', 30),
  ('游戏竞技', 'KKXSZ_CATEGORY_11', 'https://www.kkxsz.com/list-11/', 40),
  ('修真武侠', 'KKXSZ_CATEGORY_17', 'https://www.kkxsz.com/list-17/', 50),
  ('言情小说', 'KKXSZ_CATEGORY_18', 'https://www.kkxsz.com/list-18/', 60),
  ('悬疑惊悚', 'KKXSZ_CATEGORY_21', 'https://www.kkxsz.com/list-21/', 70),
  ('科幻空间', 'KKXSZ_CATEGORY_22', 'https://www.kkxsz.com/list-22/', 80);
UPDATE crawl_rank_source r
JOIN tmp_kkxsz_rank_sources t ON r.rank_type = t.rank_type
SET r.rank_name = t.rank_name,
    r.rank_url = t.rank_url,
    r.prefer_completed = 0,
    r.max_books = 1,
    r.enabled = 0,
    r.updated_at = CURRENT_TIMESTAMP
WHERE r.source_id = @kkxsz_source_id;
INSERT INTO crawl_rank_source (source_id, rank_name, rank_type, rank_url, prefer_completed, max_books, enabled)
SELECT @kkxsz_source_id, t.rank_name, t.rank_type, t.rank_url, 0, 1, 0
FROM tmp_kkxsz_rank_sources t
WHERE @kkxsz_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM crawl_rank_source r
    WHERE r.source_id = @kkxsz_source_id
      AND r.rank_type = t.rank_type
  )
ORDER BY t.sort_no;
DROP TEMPORARY TABLE IF EXISTS tmp_kkxsz_rank_sources;

-- ---- 来自 sql/migrations/20260901_remove_authorized_book_review_flow.sql ----
-- 2) 关闭自动合并（授权源走审核队列；公开源由 20260901_23qb_direct_publish.sql 恢复）——幂等
UPDATE mini_novel_crawler.crawl_schedule SET auto_merge = 0, updated_at = NOW()
WHERE auto_merge = 1;

-- ---- 来自 sql/migrations/20260901_23qb_direct_publish.sql ----
-- 20260901 调整：23qb 公开源内容免审核、爬取后直接发布入库（恢复自动合并）
-- h528/69hnovel 授权源保持审核流（auto_merge=0，内容进 PENDING_REVIEW 队列）
-- 本迁移幂等，每次部署重跑安全。

-- 1) 公开源计划恢复自动合并——幂等
UPDATE mini_novel_crawler.crawl_schedule s
JOIN mini_novel_crawler.crawl_source src ON src.id = s.source_id
SET s.auto_merge = 1, s.updated_at = NOW()
WHERE s.enabled = 1 AND src.source_type = 'PUBLIC';
-- 2) "已发布"的待审章节自动转回 CONTENT_READY——幂等纠正
--    判断依据：novel_source_mapping + chapter_source_mapping 中已存在该章节的发布映射
--    （说明该章节内容已入库，无论公开源自动合并还是授权源审核通过，都无需再审核）。
--    未发布的新章节（无映射行）保持 PENDING_REVIEW，继续走审核。
UPDATE mini_novel_crawler.crawl_chapter_raw c
JOIN mini_novel_crawler.crawl_book_raw b ON b.id = c.book_raw_id
JOIN mini_novel.novel_source_mapping m
  ON m.source_code = b.source_code AND m.source_book_id = b.source_book_id
JOIN mini_novel.chapter_source_mapping csm
  ON csm.novel_mapping_id = m.id AND csm.source_chapter_id = c.source_chapter_id
SET c.content_status = 'CONTENT_READY', c.updated_at = NOW()
WHERE c.content_status = 'PENDING_REVIEW';

-- ============================================================================
-- §4  后续变更区（新变更写在这里，保持幂等）
-- ----------------------------------------------------------------------------
-- 模板：新增列
--   SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
--     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'novel' AND COLUMN_NAME = 'new_col');
--   SET @ddl := IF(@col_exists = 0, 'ALTER TABLE novel ADD COLUMN new_col VARCHAR(32) NULL', 'SELECT 1');
--   PREPARE ddl_stmt FROM @ddl; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;
--
-- 模板：新增索引
--   SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
--     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'novel' AND INDEX_NAME = 'idx_new');
--   SET @ddl := IF(@idx_exists = 0, 'ALTER TABLE novel ADD INDEX idx_new (title, author)', 'SELECT 1');
--   PREPARE ddl_stmt FROM @ddl; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;
-- ============================================================================

-- ============================================================================
-- §5  结构自检（只读，便于部署日志核对）
-- ============================================================================
SELECT 'mini_novel' AS db, COUNT(*) AS tables_now
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mini_novel'
UNION ALL
SELECT 'mini_novel_crawler', COUNT(*)
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mini_novel_crawler';
