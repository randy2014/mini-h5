package com.mini.novel.media.support;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 媒体基础设施 bean 装配。
 */
@Configuration
public class MediaConfig {

    /** 存储根目录（容器内 volume，见 docs §9）；可通过 env MEDIA_ROOT 覆盖。 */
    @Bean
    public MediaFileStorage mediaFileStorage(
            @Value("${app.media.root:${MEDIA_ROOT:/data/media}}") String root) {
        return new MediaFileStorage(Path.of(root));
    }

    @Bean
    public ImageCompressor imageCompressor() {
        return new ImageCompressor();
    }

    @Bean
    public VideoProcessor videoProcessor() {
        return new VideoProcessor();
    }
}
