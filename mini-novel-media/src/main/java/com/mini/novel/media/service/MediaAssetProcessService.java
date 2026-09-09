package com.mini.novel.media.service;

import com.mini.novel.media.entity.MediaAsset;
import java.io.IOException;
import java.io.InputStream;

/**
 * 多媒体素材处理与入库服务（图片同步压缩 / 视频异步转码）。
 */
public interface MediaAssetProcessService {

    /**
     * 处理图片素材：md5 去重 → 预算式压缩 main + thumb → 入库 READY。
     *
     * @return 已入库素材（若 md5 命中已存在 READY 素材则直接返回既有记录）
     */
    MediaAsset processImage(InputStream in, String originalName, Long operatorId) throws IOException;

    /** 登记视频素材（tmp 落盘 + PROCESSING 入库 + 触发异步转码）。 */
    MediaAsset registerVideo(InputStream in, String originalName, Long operatorId) throws IOException;

    /** 异步执行视频转码 + 封面抽帧 + 状态更新（成功后删除 tmp）。 */
    void processVideoAsync(Long assetId);

    /** 立即重试转码/重抽帧（对 FAILED 素材），成功则置 READY。 */
    MediaAsset reprocess(Long assetId) throws IOException;
}
