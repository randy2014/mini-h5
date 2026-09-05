package com.mini.novel.api.job;

import com.mini.novel.api.service.SubscribeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscribeExpireJob {
    private static final Logger log = LoggerFactory.getLogger(SubscribeExpireJob.class);

    private final SubscribeService subscribeService;

    public SubscribeExpireJob(SubscribeService subscribeService) {
        this.subscribeService = subscribeService;
    }

    /** 每小时扫描一次到期订阅，自动回收访问权（不自动扣费）。 */
    @Scheduled(fixedDelay = 3600000, initialDelay = 120000)
    public void sweepExpiredSubscribes() {
        int expired = subscribeService.expireSweep();
        if (expired > 0) {
            log.info("expired subscribes swept: {}", expired);
        }
    }
}
