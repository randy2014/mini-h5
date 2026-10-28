package com.mini.novel.media.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 视频转码的异步触发入口（事务提交后投递到 mediaProcessExecutor）。
 *
 * <p><b>为什么必须是独立 bean：</b>此前这段监听逻辑写在 {@code MediaAssetProcessServiceImpl} 内部，
 * 通过 {@code this.processVideoAsync(...)} 自调用，绕过了 Spring 的 {@code @Async} 代理，
 * 转码实际跑在「上传请求的线程」上 —— 上传接口要等 ffmpeg 转码 + 抽帧全部跑完才返回，
 * 47MB 短视频就要 20s 以上，前端 15s 超时（以及网关/nginx 超时）即由此产生。
 *
 * <p>现在监听器持有 {@link MediaAssetProcessService} 的<b>代理</b>引用，调用
 * {@code processVideoAsync} 会经过代理，{@code @Async("mediaProcessExecutor")} 正常生效，
 * 上传接口登记完素材立刻返回 {@code PROCESSING}。
 */
@Component
public class MediaVideoProcessListener {
    private static final Logger log = LoggerFactory.getLogger(MediaVideoProcessListener.class);

    /** 注入的是 Spring 代理，@Async 才不会被自调用绕过。 */
    private final MediaAssetProcessService processService;

    public MediaVideoProcessListener(MediaAssetProcessService processService) {
        this.processService = processService;
    }

    /**
     * AFTER_COMMIT 保证 worker 读到的 asset 记录已提交；同步返回，不做任何耗时操作，
     * 避免拖住发布事件的上传请求线程。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVideoReady(MediaVideoProcessEvent event) {
        Long assetId = event.assetId();
        try {
            processService.processVideoAsync(assetId);
        } catch (Exception e) {
            // 投递失败（线程池拒绝等）不应影响已提交的上传事务
            log.error("dispatch video transcode failed assetId={}", assetId, e);
        }
    }
}
