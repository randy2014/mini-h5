-- 修复：media_asset.main_path / thumb_path 允许为空
-- 原因：视频素材在 registerVideo 阶段为 PROCESSING（尚未转码），此时无成品与缩略图路径，
--       原 NOT NULL 定义会导致插入失败（Field 'main_path' doesn't have a default value）。
-- 幂等：ALTER 重复执行无副作用。

USE mini_novel;

ALTER TABLE media_asset
  MODIFY COLUMN main_path VARCHAR(512) DEFAULT NULL COMMENT '成品相对路径(图片jpg/视频mp4；视频转码完成前为空)',
  MODIFY COLUMN thumb_path VARCHAR(512) DEFAULT NULL COMMENT '缩略图相对路径(图片必生成；视频转码完成后生成)';
