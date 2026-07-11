package com.mini.novel.crawlerservice.service;

import static org.junit.jupiter.api.Assertions.*;
import com.mini.novel.crawler.entity.CrawlerAuthorizedBook;
import org.junit.jupiter.api.Test;

class AuthorizedBookPolicyTest {
    @Test void unauthorizedCannotCrawlChapters() { assertThrows(IllegalArgumentException.class, () -> AuthorizedBookPolicy.validate(book("PENDING", "PENDING", "LOW", true, false, false, false))); }
    @Test void blockedCannotBeAuthorizedOrUsed() { assertThrows(IllegalArgumentException.class, () -> AuthorizedBookPolicy.validate(book("AUTHORIZED", "APPROVED", "BLOCKED", false, false, false, false))); }
    @Test void vipRequiresAuthorizedApprovedAndDisplay() { assertThrows(IllegalArgumentException.class, () -> AuthorizedBookPolicy.validate(book("AUTHORIZED", "PENDING", "LOW", false, false, true, true))); }
    @Test void validAuthorizedPermissionsPass() { assertDoesNotThrow(() -> AuthorizedBookPolicy.validate(book("AUTHORIZED", "APPROVED", "LOW", true, true, true, true))); }
    private CrawlerAuthorizedBook book(String a,String r,String risk,boolean chapters,boolean store,boolean display,boolean vip){CrawlerAuthorizedBook b=new CrawlerAuthorizedBook();b.authorizationStatus=a;b.reviewStatus=risk.equals("BLOCKED")?r:r;b.riskLevel=risk;b.allowCrawlChapters=chapters;b.allowStore=store;b.allowDisplay=display;b.allowVipDisplay=vip;return b;}
}
