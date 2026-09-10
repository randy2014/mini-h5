package com.mini.novel.media.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.media.entity.MediaAsset;
import com.mini.novel.media.mapper.MediaAssetMapper;
import com.mini.novel.media.mapper.MediaPostAssetMapper;
import com.mini.novel.media.support.MediaFileStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 媒体磁盘兜底清理任务（见 docs/media-pool-design.md R2）。
 *
 * 覆盖三类残留（前端未清理或异常中断时产生）：
 * 1. tmp 目录中转文件（上传未完成/转码中断）—— 超过 {@code retentionHours} 删除；
 * 2. 无任何内容帖引用的孤儿素材（例如上传后未保存草稿、保存失败）—— 超过保留期删除记录与文件；
 * 3. 长时间处于 PROCESSING 的卡死素材 —— 标记 FAILED，便于运营重试或清理。
 *
 * 每日 03:30 执行；阈值可经 app.media.cleanup.* 调整。删除动作均落日志，便于审计与磁盘核对。
 */
@Component
public class MediaCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

    private final MediaAssetMapper assetMapper;
    private final MediaPostAssetMapper postAssetMapper;
    private final MediaFileStorage storage;

    /** 孤儿素材保留小时数（默认 24h：给运营留足编辑时间）。 */
    private final long orphanRetentionHours;
    /** tmp 中转文件保留小时数（默认 24h）。 */
    private final long tmpRetentionHours;
    /** PROCESSING 超时小时数（默认 2h，超过视为卡死）。 */
    private final long processingTimeoutHours;

    public MediaCleanupJob(MediaAssetMapper assetMapper,
                           MediaPostAssetMapper postAssetMapper,
                           MediaFileStorage storage,
                           @Value("${app.media.cleanup.orphan-retention-hours:24}") long orphanRetentionHours,
                           @Value("${app.media.cleanup.tmp-retention-hours:24}") long tmpRetentionHours,
                           @Value("${app.media.cleanup.processing-timeout-hours:2}") long processingTimeoutHours) {
        this.assetMapper = assetMapper;
        this.postAssetMapper = postAssetMapper;
        this.storage = storage;
        this.orphanRetentionHours = orphanRetentionHours;
        this.tmpRetentionHours = tmpRetentionHours;
        this.processingTimeoutHours = processingTimeoutHours;
    }

    @Scheduled(cron = "${app.media.cleanup.cron:0 30 3 * * ?}")
    public void cleanup() {
        int tmpRemoved = cleanTmpFiles();
        int orphanRemoved = cleanOrphanAssets();
        int staleMarked = markStaleProcessing();
        if (tmpRemoved + orphanRemoved + staleMarked > 0) {
            log.info("media cleanup done: tmp={} orphanAssets={} staleProcessing={}",
                    tmpRemoved, orphanRemoved, staleMarked);
        }
    }

    /** 清理 tmp 目录中过期的中转文件。 */
    int cleanTmpFiles() {
        Path tmpDir = storage.root().resolve(MediaFileStorage.SUB_TMP);
        if (!Files.isDirectory(tmpDir)) {
            return 0;
        }
        long deadline = System.currentTimeMillis() - Duration.ofHours(tmpRetentionHours).toMillis();
        int removed = 0;
        try (Stream<Path> files = Files.list(tmpDir)) {
            for (Path file : files.toList()) {
                try {
                    if (Files.isRegularFile(file) && Files.getLastModifiedTime(file).toMillis() < deadline) {
                        Files.deleteIfExists(file);
                        removed++;
                    }
                } catch (IOException e) {
                    log.warn("cleanup tmp file failed: {}", file, e);
                }
            }
        } catch (IOException e) {
            log.warn("cleanup tmp dir failed: {}", tmpDir, e);
        }
        return removed;
    }

    /**
     * 删除无任何内容帖引用、且超过保留期的孤儿素材（记录 + 磁盘文件）。
     * 注意：仅处理"零引用"素材，任何被草稿/已发布内容引用的素材都不会被触碰。
     */
    int cleanOrphanAssets() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(orphanRetentionHours);
        List<MediaAsset> candidates = assetMapper.selectList(new LambdaQueryWrapper<MediaAsset>()
                .lt(MediaAsset::getCreatedAt, deadline)
                .notInSql(MediaAsset::getId, "SELECT asset_id FROM media_post_asset"));
        int removed = 0;
        for (MediaAsset asset : candidates) {
            Long refs = postAssetMapper.selectCount(new LambdaQueryWrapper<com.mini.novel.media.entity.MediaPostAsset>()
                    .eq(com.mini.novel.media.entity.MediaPostAsset::getAssetId, asset.getId()));
            if (refs != null && refs > 0) {
                continue; // 双重保险：查询与删除之间若被引用则跳过
            }
            assetMapper.deleteById(asset.getId());
            storage.delete(asset.getMainPath());
            storage.delete(asset.getThumbPath());
            storage.delete(asset.getPosterPath());
            removed++;
            log.info("removed orphan media asset id={} name={}", asset.getId(), asset.getOriginalName());
        }
        return removed;
    }

    /** 将长时间 PROCESSING 的素材标记为 FAILED（转码中断/容器重启等）。 */
    int markStaleProcessing() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(processingTimeoutHours);
        List<MediaAsset> stale = assetMapper.selectList(new LambdaQueryWrapper<MediaAsset>()
                .eq(MediaAsset::getStatus, MediaAsset.STATUS_PROCESSING)
                .lt(MediaAsset::getUpdatedAt, deadline));
        for (MediaAsset asset : stale) {
            asset.setStatus(MediaAsset.STATUS_FAILED);
            asset.setFailReason("处理超时（超过 " + processingTimeoutHours + " 小时未完成），可重新处理或删除");
            assetMapper.updateById(asset);
            log.warn("marked stale processing asset as FAILED id={}", asset.getId());
        }
        return stale.size();
    }
}
