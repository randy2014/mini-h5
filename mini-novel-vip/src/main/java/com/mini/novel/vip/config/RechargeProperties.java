package com.mini.novel.vip.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 快乐币充值配置（人工确认到账方案）。
 * 对应 application.yml 中 app.coin.recharge 节点。
 */
@Component
@ConfigurationProperties(prefix = "app.coin.recharge")
public class RechargeProperties {

    private List<RechargePackage> packages = new ArrayList<>();
    private String wxQrUrl = "";
    private String alipayQrUrl = "";
    private String notice = "";

    public static class RechargePackage {
        private Long id;
        private String name;
        private Long coins;
        private BigDecimal amountYuan;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getCoins() { return coins; }
        public void setCoins(Long coins) { this.coins = coins; }
        public BigDecimal getAmountYuan() { return amountYuan; }
        public void setAmountYuan(BigDecimal amountYuan) { this.amountYuan = amountYuan; }
    }

    public List<RechargePackage> getPackages() { return packages; }
    public void setPackages(List<RechargePackage> packages) { this.packages = packages; }
    public String getWxQrUrl() { return wxQrUrl; }
    public void setWxQrUrl(String wxQrUrl) { this.wxQrUrl = wxQrUrl; }
    public String getAlipayQrUrl() { return alipayQrUrl; }
    public void setAlipayQrUrl(String alipayQrUrl) { this.alipayQrUrl = alipayQrUrl; }
    public String getNotice() { return notice; }
    public void setNotice(String notice) { this.notice = notice; }
}
