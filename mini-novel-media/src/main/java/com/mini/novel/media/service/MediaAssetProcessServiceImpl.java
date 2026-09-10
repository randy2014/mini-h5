package com.mini.novel.media.service;

import com.mini.novel.media.entity.MediaAsset;
import com.mini.novel.media.mapper.MediaAssetMapper;
import com.mini.novel.media.support.ImageCompressor;
import com.mini.novel.media.support.MediaFileStorage;
import com.mini.novel.media.support.VideoProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 图片同步压缩入库；视频 tmp 落盘 → PROCESSING → 异步转码+抽帧 → READY/FAILED。
 */
@Service
public class MediaAssetProcessServiceImpl implements MediaAssetProcessService {
    private static final Logger log = LoggerFactory.getLogger(MediaAssetProcessServiceImpl.class);

    private final MediaAssetMapper assetMapper;
    private final MediaFileStorage storage;
    private final ImageCompressor imageCompressor;
    private final VideoProcessor videoProcessor;
    private final ApplicationEventPublisher eventPublisher;

    public MediaAssetProcessServiceImpl(MediaAssetMapper assetMapper,
                                        MediaFileStorage storage,
                                        ImageCompressor imageCompressor,
                                        VideoProcessor videoProcessor,
                                        ApplicationEventPublisher eventPublisher) {
        this.assetMapper = assetMapper;
        this.storage = storage;
        this.imageCompressor = imageCompressor;
        this.videoProcessor = videoProcessor;
        this.eventPublisher = eventPublisher;
    }

    /** 视频处理异步触发事件（@TransactionalEventListener 在事务提交后执行）。 */
    public record VideoReadyToProcessEvent(Long assetId) {
    }

    @Override
    @Transactional
    public MediaAsset processImage(InputStream in, String originalName, Long operatorId) throws IOException {
        Path tmp = storage.saveTmp(in);
        try {
            byte[] bytes = Files.readAllBytes(tmp);
            String md5 = md5(bytes);
            // md5 去重：命中 READY 直接复用
            MediaAsset existing = findByMd5(md5);
            if (existing != null && MediaAsset.STATUS_READY.equals(existing.getStatus())) {
                return existing;
            }
            String uuid = storage.newId();

            ImageCompressor.Result main = imageCompressor.compress(bytes);
            ImageCompressor.Result thumb = imageCompressor.thumbnail(bytes);

            Path mainFile = storage.imageMain(uuid);
            Path thumbFile = storage.thumbTarget(uuid);
            Files.write(mainFile, main.getJpeg());
            Files.write(thumbFile, thumb.getJpeg());

            MediaAsset asset = new MediaAsset();
            asset.setFileType(MediaAsset.TYPE_IMAGE);
            asset.setOriginalName(StringUtils.hasText(originalName) ? originalName : uuid);
            asset.setMd5(md5);
            asset.setSizeBytes((long) bytes.length);
            asset.setWidth(main.getWidth());
            asset.setHeight(main.getHeight());
            asset.setMainPath(MediaFileStorage.relOf(storage.root(), mainFile));
            asset.setThumbPath(MediaFileStorage.relOf(storage.root(), thumbFile));
            asset.setStatus(MediaAsset.STATUS_READY);
            asset.setOperatorId(operatorId);
            asset.setCreatedAt(LocalDateTime.now());
            assetMapper.insert(asset);
            return asset;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    @Transactional
    public MediaAsset registerVideo(InputStream in, String originalName, Long operatorId) throws IOException {
        Path tmp = storage.saveTmp(in);
        try {
            String md5 = md5OfFile(tmp); // 流式计算，避免大视频全量读入内存
            MediaAsset existing = findByMd5(md5);
            if (existing != null && MediaAsset.STATUS_READY.equals(existing.getStatus())) {
                Files.deleteIfExists(tmp);
                return existing;
            }
            MediaAsset asset = new MediaAsset();
            asset.setFileType(MediaAsset.TYPE_VIDEO);
            asset.setOriginalName(StringUtils.hasText(originalName) ? originalName : "video");
            asset.setMd5(md5);
            asset.setSizeBytes(Files.size(tmp));
            asset.setStatus(MediaAsset.STATUS_PROCESSING);
            asset.setOperatorId(operatorId);
            asset.setCreatedAt(LocalDateTime.now());
            assetMapper.insert(asset);
            // 以 assetId 命名暂存原片，供异步任务定位；事务提交后触发异步转码
            Path staged = storage.tmpFile().getParent().resolve(asset.getId() + ".tmp");
            Files.move(tmp, staged, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            eventPublisher.publishEvent(new VideoReadyToProcessEvent(asset.getId()));
            return asset;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Async("mediaProcessExecutor")
    @Override
    public void processVideoAsync(Long assetId) {
        MediaAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            return;
        }
        try {
            Path staged = findStaged(asset.getId());
            if (staged == null) {
                throw new IOException("未找到待处理原文件: assetId=" + assetId);
            }
            String uuid = storage.newId();
            VideoProcessor.ProbeInfo info = videoProcessor.probe(staged);
            if (info.width() <= 0 || info.height() <= 0) {
                throw new IOException("视频无可视流");
            }
            Path mp4 = videoProcessor.transcode(staged, rootOf(storage.videoMain(uuid)), uuid);
            Path poster = videoProcessor.extractPoster(mp4, rootOf(storage.posterTarget(uuid)), uuid, info.durationMs());
            // poster 缩略：直接复用封面帧为 thumb（再压一张小图）
            ImageCompressor.Result posterThumb = imageCompressor.thumbnail(Files.readAllBytes(poster));
            Path thumbFile = storage.thumbTarget(uuid);
            Files.write(thumbFile, posterThumb.getJpeg());

            asset.setWidth(info.width());
            asset.setHeight(info.height());
            asset.setDurationMs(info.durationMs());
            asset.setMainPath(MediaFileStorage.relOf(storage.root(), mp4));
            asset.setPosterPath(MediaFileStorage.relOf(storage.root(), poster));
            asset.setThumbPath(MediaFileStorage.relOf(storage.root(), thumbFile));
            asset.setStatus(MediaAsset.STATUS_READY);
            asset.setFailReason(null);
            assetMapper.updateById(asset);
            Files.deleteIfExists(staged);
            log.info("video processed assetId={} mp4={}ms={}ms", assetId, info.durationMs(), asset.getDurationMs());
        } catch (Exception e) {
            log.error("video process failed assetId={}", assetId, e);
            asset.setStatus(MediaAsset.STATUS_FAILED);
            asset.setFailReason(e.getMessage() == null ? e.getClass().getSimpleName() : abbreviate(e.getMessage()));
            assetMapper.updateById(asset);
        }
    }

    @Override
    @Transactional
    public MediaAsset reprocess(Long assetId) throws IOException {
        MediaAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            return null;
        }
        if (!MediaAsset.TYPE_VIDEO.equals(asset.getFileType())) {
            return asset;
        }
        asset.setStatus(MediaAsset.STATUS_PROCESSING);
        asset.setFailReason(null);
        assetMapper.updateById(asset);
        eventPublisher.publishEvent(new VideoReadyToProcessEvent(assetId));
        return asset;
    }

    /** 事务提交后触发异步转码（规避事务内异步自调用失效）。 */
    @org.springframework.transaction.event.TransactionalEventListener(
            phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void onVideoReady(VideoReadyToProcessEvent event) {
        processVideoAsync(event.assetId());
    }

    private Path findStaged(Long assetId) {
        // 约定: tmp/{assetId}.tmp 保留原始输入（registerVideo 已移动）。这里用 asset.id 命名更可靠，
        // 因此 registerVideo 需以 assetId 命名 —— 见 registerVideo 内同步调整。
        Path byId = storage.root().resolve("tmp").resolve(assetId + ".tmp");
        return Files.exists(byId) ? byId : null;
    }

    private Path rootOf(Path child) {
        return child.getParent();
    }

    private MediaAsset findByMd5(String md5) {
        return assetMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getMd5, md5).last("LIMIT 1")).stream().findFirst().orElse(null);
    }

    private static String md5(byte[] data) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("MD5 计算失败", e);
        }
    }

    /** 流式计算文件 md5（大视频不占内存）。 */
    private static String md5OfFile(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (var in = Files.newInputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    md.update(buf, 0, n);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("MD5 计算失败", e);
        }
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) : s);
    }
}
