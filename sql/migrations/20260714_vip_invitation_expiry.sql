USE mini_novel;

ALTER TABLE vip_invitation_code
  ADD COLUMN IF NOT EXISTS expires_at DATETIME NULL AFTER last_used_at;
