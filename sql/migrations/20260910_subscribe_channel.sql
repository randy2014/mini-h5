-- 订阅频道 + 快乐币 + 订阅关系（M1 订阅核心）
-- 幂等：全部 CREATE TABLE IF NOT EXISTS

USE mini_novel;

CREATE TABLE IF NOT EXISTS subscribe_channel (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL COMMENT '频道名称',
  sort INT NOT NULL DEFAULT 100 COMMENT '排序',
  status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE' COMMENT 'PUBLISHED=已发布 / OFFLINE=已下架',
  cover VARCHAR(512) DEFAULT NULL COMMENT '封面（非必填）',
  description VARCHAR(512) DEFAULT NULL COMMENT '简介（非必填）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订阅频道分类（独立于 vip_category）';

CREATE TABLE IF NOT EXISTS subscribe_channel_novel (
  id BIGINT NOT NULL AUTO_INCREMENT,
  channel_id BIGINT NOT NULL COMMENT '订阅频道 id',
  novel_id BIGINT NOT NULL COMMENT '小说 id',
  operator_id BIGINT DEFAULT NULL COMMENT '操作人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_channel_novel (channel_id, novel_id),
  KEY idx_novel (novel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说-订阅频道映射（加入频道）';

CREATE TABLE IF NOT EXISTS user_coin_balance (
  user_id BIGINT NOT NULL,
  balance BIGINT NOT NULL DEFAULT 0 COMMENT '快乐币余额',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='快乐币余额';

CREATE TABLE IF NOT EXISTS user_coin_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  change_amount BIGINT NOT NULL COMMENT '变动量（正=增加，负=扣减）',
  balance_after BIGINT NOT NULL COMMENT '变动后余额',
  biz_type VARCHAR(32) NOT NULL COMMENT 'RECHARGE=后台充值 / SUBSCRIBE=订阅扣费 / REFUND=冲正 / GRANT=赠送',
  biz_id VARCHAR(64) DEFAULT NULL COMMENT '业务 id（如订阅记录 id）',
  operator_id BIGINT DEFAULT NULL COMMENT '操作人（后台充值）',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='快乐币流水';

CREATE TABLE IF NOT EXISTS user_subscribe (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  channel_id BIGINT NOT NULL COMMENT '订阅频道 id',
  period_type VARCHAR(16) NOT NULL COMMENT 'WEEK / MONTH / QUARTER / YEAR',
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / EXPIRED / CANCELLED',
  cost_coins BIGINT NOT NULL DEFAULT 0 COMMENT '扣币数',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id, status),
  KEY idx_channel (channel_id, status),
  KEY idx_end_time (end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订阅关系';
