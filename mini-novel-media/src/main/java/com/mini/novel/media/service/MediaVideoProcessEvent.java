package com.mini.novel.media.service;

/**
 * 视频待处理事件：{@code registerVideo} / {@code reprocess} 在事务内发布，由
 * {@link MediaVideoProcessListener} 在事务提交后消费，经 {@code @Async} 代理投递到
 * {@code mediaProcessExecutor} 线程池真正异步转码。
 *
 * <p>抽成独立事件类（原先为 Impl 内部 record）是为了让监听器与处理服务分属两个 bean，
 * 避免自调用导致 {@code @Async} 失效（见 {@link MediaVideoProcessListener} 类注释）。
 */
public record MediaVideoProcessEvent(Long assetId) {
}
