USE mini_novel;

ALTER TABLE app_user ADD COLUMN vip_source VARCHAR(32) NULL AFTER vip_expire_time;
ALTER TABLE app_user ADD COLUMN vip_activated_at DATETIME NULL AFTER vip_source;
ALTER TABLE app_user ADD COLUMN vip_disabled_at DATETIME NULL AFTER vip_activated_at;

ALTER TABLE user_vip ADD COLUMN source_type VARCHAR(32) NULL AFTER source_order_id;
ALTER TABLE user_vip ADD COLUMN source_ref_id BIGINT NULL AFTER source_type;
ALTER TABLE user_vip ADD COLUMN operator_id BIGINT NULL AFTER source_ref_id;
ALTER TABLE user_vip ADD COLUMN remark VARCHAR(255) NULL AFTER operator_id;

CREATE TABLE IF NOT EXISTS vip_invitation_code (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  code VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  total_quota INT NOT NULL DEFAULT 3,
  used_quota INT NOT NULL DEFAULT 0,
  remaining_quota INT NOT NULL DEFAULT 3,
  is_current TINYINT(1) NOT NULL DEFAULT 1,
  generated_at DATETIME NOT NULL,
  enabled_at DATETIME NULL,
  disabled_at DATETIME NULL,
  revoked_at DATETIME NULL,
  last_used_at DATETIME NULL,
  replaced_by_code_id BIGINT NULL,
  operator_id BIGINT NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code),
  KEY idx_owner_current (owner_user_id, is_current),
  KEY idx_status (status),
  KEY idx_owner_status (owner_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vip_invitation_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  invitation_code_id BIGINT NOT NULL,
  code_snapshot VARCHAR(32) NOT NULL,
  inviter_user_id BIGINT NOT NULL,
  invitee_user_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVATED',
  activated_at DATETIME NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_invitee_user (invitee_user_id),
  KEY idx_inviter_created (inviter_user_id, created_at),
  KEY idx_code_id (invitation_code_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vip_operation_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  action VARCHAR(64) NOT NULL,
  target_user_id BIGINT NULL,
  target_code_id BIGINT NULL,
  target_invitation_record_id BIGINT NULL,
  before_json JSON NULL,
  after_json JSON NULL,
  operator_id BIGINT NULL,
  reason VARCHAR(255) NULL,
  request_id VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_request_id (request_id),
  KEY idx_target_user_created (target_user_id, created_at),
  KEY idx_target_code_created (target_code_id, created_at),
  KEY idx_action_created (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE app_user
  MODIFY COLUMN vip_status TINYINT NOT NULL DEFAULT 0 COMMENT 'VIP 状态：0=非 VIP，1=有效期 VIP，2=永久 VIP',
  MODIFY COLUMN vip_source VARCHAR(32) NULL COMMENT 'VIP 来源：INVITATION/ADMIN/ORDER',
  MODIFY COLUMN vip_activated_at DATETIME NULL COMMENT 'VIP 最近激活时间',
  MODIFY COLUMN vip_disabled_at DATETIME NULL COMMENT 'VIP 最近停用/降级时间';

ALTER TABLE user_vip
  MODIFY COLUMN source_type VARCHAR(32) NULL COMMENT '权益来源类型：INVITATION/ADMIN/ORDER',
  MODIFY COLUMN source_ref_id BIGINT NULL COMMENT '来源记录 ID，如邀请记录或订单 ID',
  MODIFY COLUMN operator_id BIGINT NULL COMMENT '后台操作人 ID',
  MODIFY COLUMN remark VARCHAR(255) NULL COMMENT '权益备注';
