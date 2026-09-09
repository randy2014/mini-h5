package com.mini.novel.media.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.media.entity.MediaAsset;
import com.mini.novel.media.entity.MediaPost;
import java.util.List;

/**
 * 内容帖（草稿/已发布）领域服务：帖 CRUD、发布/下架状态机、素材关联与引用管理。
 * 频道存在性/发布状态校验由调用方（admin/api 层）完成并传入 channelId。
 */
public interface MediaPostService {

    /** 创建草稿（title 必填；assetIds 顺序=素材 seq）。 */
    MediaPost createDraft(String title, List<Long> assetIds, Long operatorId);

    /** 编辑草稿（仅 DRAFT；PUBLISHED 抛业务异常）。素材必须全部 READY。 */
    MediaPost updateDraft(Long postId, String title, List<Long> assetIds, Long operatorId);

    /** 发布：挂载频道。校验 DRAFT、标题非空、≥1 素材、素材全部 READY、视频 ≤1。 */
    MediaPost publish(Long postId, Long channelId, Long operatorId);

    /** 下架：PUBLISHED → DRAFT，保留 channel_id。 */
    MediaPost unpublish(Long postId);

    /** 删除草稿（仅 DRAFT）：解引用并物理删除无引用素材文件。 */
    void deleteDraft(Long postId);

    /** 草稿分页。 */
    Page<MediaPost> pageDrafts(long page, long pageSize);

    /** 已发布分页（按频道筛选可选，published_at 倒序）。 */
    Page<MediaPost> pagePublished(Long channelId, long page, long pageSize);

    /** 帖详情（含有序素材与封面）。 */
    MediaPostDetail getDetail(Long postId);

    /** 频道内已发布媒体帖分页（C 端用；倒序），含封面与素材摘要。 */
    Page<MediaPostDetail> pageChannelPublished(Long channelId, long page, long pageSize);

    /** 删除素材（无任何帖引用才允许；同时物理删除文件）。 */
    void deleteAsset(Long assetId);

    /** 查询素材（admin 素材库/去重用）。 */
    Page<MediaAsset> pageAssets(String type, String status, String keyword, long page, long pageSize);

    /** 按 id 查素材（不存在抛业务异常），供流式/详情使用。 */
    MediaAsset requireAsset(Long assetId);

    /** 素材是否被「该频道的已发布帖」引用（C 端流式越权校验）。 */
    boolean isAssetInPublishedPostOfChannel(Long assetId, Long channelId);

    /** 详情 VO。 */
    record MediaPostDetail(MediaPost post, List<MediaAsset> assets, MediaAsset cover) {
    }
}
