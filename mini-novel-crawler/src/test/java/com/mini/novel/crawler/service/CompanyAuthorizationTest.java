package com.mini.novel.crawler.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mini.novel.crawler.entity.CrawlerAuthorizedBook;
import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CompanyAuthorizationTest {
    @Test void onlyActiveAdultAgreementIsAcceptedAndManualCloseIsPreserved() {
        CrawlerSourceConfig source=new CrawlerSourceConfig();source.sourceType="AUTHORIZED_VIP";source.ruleConfigJson="{\"riskRules\":{},\"companyAuthorization\":{\"proofRef\":\"C-1\",\"validFrom\":\"2026-01-01\",\"validUntil\":\"2026-12-31\",\"allowDisplay\":true,\"allowVipDisplay\":true}}";
        ObjectNode agreement=CompanyAuthorization.read(source);assertTrue(CompanyAuthorization.isActive(source,agreement,LocalDate.of(2026,7,11)));
        CrawlerAuthorizedBook book=new CrawlerAuthorizedBook();book.authorizationStatus="AUTHORIZED";book.allowDisplay=false;book.allowVipDisplay=false;CompanyAuthorization.apply(book,agreement);assertFalse(book.allowDisplay);assertFalse(book.allowVipDisplay);
        source.sourceType="PUBLIC";assertFalse(CompanyAuthorization.isActive(source,agreement,LocalDate.of(2026,7,11)));
    }
}
