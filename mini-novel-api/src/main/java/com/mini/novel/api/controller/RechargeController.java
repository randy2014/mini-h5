package com.mini.novel.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.result.Result;
import com.mini.novel.vip.config.RechargeProperties;
import com.mini.novel.vip.entity.CoinRechargeOrder;
import com.mini.novel.vip.service.RechargeService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recharge")
public class RechargeController {
    private final RechargeService rechargeService;
    private final RechargeProperties properties;
    private final CurrentUserResolver currentUserResolver;

    public RechargeController(RechargeService rechargeService, RechargeProperties properties,
                              CurrentUserResolver currentUserResolver) {
        this.rechargeService = rechargeService;
        this.properties = properties;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/packages")
    public Result<Map<String, Object>> packages() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("packages", properties.getPackages());
        data.put("wxQrUrl", properties.getWxQrUrl());
        data.put("alipayQrUrl", properties.getAlipayQrUrl());
        data.put("notice", properties.getNotice());
        return Result.ok(data);
    }

    @PostMapping("/orders")
    public Result<CoinRechargeOrder> create(@RequestBody CreateRequest request) {
        Long userId = currentUserResolver.requireUser(null).getId();
        return Result.ok(rechargeService.createOrder(userId, request.packageId(), request.payMethod(), request.remark()));
    }

    @GetMapping("/orders/{orderNo}")
    public Result<CoinRechargeOrder> query(@PathVariable String orderNo) {
        Long userId = currentUserResolver.requireUser(null).getId();
        return Result.ok(rechargeService.queryOrder(userId, orderNo));
    }

    @GetMapping("/orders")
    public Result<Map<String, Object>> myOrders(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long pageSize) {
        Long userId = currentUserResolver.requireUser(null).getId();
        Page<CoinRechargeOrder> result = rechargeService.myOrders(userId, page, pageSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("pageSize", result.getSize());
        data.put("pages", result.getPages());
        data.put("hasMore", result.getCurrent() < result.getPages());
        return Result.ok(data);
    }

    @PostMapping("/orders/{orderNo}/paid-hint")
    public Result<CoinRechargeOrder> markPaid(@PathVariable String orderNo) {
        Long userId = currentUserResolver.requireUser(null).getId();
        return Result.ok(rechargeService.markPaidHint(userId, orderNo));
    }

    // ------------------------------------------------------------------
    // 邮件「点击审批通过」：GET 只展示确认页（不产生副作用，避免邮箱安全扫描预抓取误触发），
    // POST 才真正确认到账。接口本身不需要登录，安全性由邮件里的随机审批令牌保证。
    // ------------------------------------------------------------------

    @GetMapping(value = "/approve", produces = MediaType.TEXT_HTML_VALUE)
    public String approvePage(@RequestParam(required = false) String token) {
        try {
            CoinRechargeOrder order = rechargeService.findByApproveToken(token);
            if (order == null) {
                return page("审批链接无效", "<div class=\"info\">该审批链接无效或已失效，请到后台手动处理。</div>");
            }
            if (order.getStatus() == null || order.getStatus() != CoinRechargeOrder.STATUS_PENDING) {
                return page("订单已处理", "<div class=\"info\">该充值订单已处理（已到账或已取消），无需重复审批。</div>");
            }
            String info = "订单号：" + escape(order.getOrderNo())
                    + "<br>金额：¥" + escape(String.valueOf(order.getAmountYuan()))
                    + "<br>到账快乐币：" + order.getCoins()
                    + "<br>用户已付款："
                    + (order.getPaidHintAt() == null ? "-" : escape(String.valueOf(order.getPaidHintAt())));
            return page("充值审批",
                    "<div class=\"info\">" + info + "</div>"
                            + "<form method=\"post\" action=\"/api/recharge/approve\">"
                            + "<input type=\"hidden\" name=\"token\" value=\"" + escape(token) + "\">"
                            + "<button type=\"submit\" class=\"btn\">确认审批并发放 " + order.getCoins() + " 快乐币</button>"
                            + "</form>"
                            + "<p class=\"tip\">点击后将立即给用户发放快乐币，且不可撤销。</p>");
        } catch (Exception ex) {
            return page("审批链接无效", "<div class=\"info\">审批链接无效，请到后台手动处理。</div>");
        }
    }

    @PostMapping(value = "/approve", produces = MediaType.TEXT_HTML_VALUE)
    public String approveSubmit(@RequestParam(required = false) String token) {
        try {
            CoinRechargeOrder order = rechargeService.approveByToken(token);
            return page("审批成功",
                    "<div class=\"info\">订单 " + escape(order.getOrderNo())
                            + " 已审批通过，已发放 " + order.getCoins() + " 快乐币。</div>");
        } catch (BusinessException ex) {
            return page("审批失败", "<div class=\"info\">" + escape(ex.getMessage()) + "</div>");
        } catch (Exception ex) {
            return page("审批失败", "<div class=\"info\">审批失败，请到后台手动处理。</div>");
        }
    }

    private String page(String title, String bodyHtml) {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escape(title) + "</title>"
                + "<style>body{margin:0;background:#f2f3f7;font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;color:#1f2528}"
                + ".card{max-width:460px;margin:40px auto;background:#fff;border-radius:14px;padding:24px;box-shadow:0 8px 30px rgba(0,0,0,.06)}"
                + "h1{margin:0 0 16px;font-size:20px}"
                + ".info{font-size:14px;line-height:1.9;color:#555;background:#f7f8fa;border-radius:10px;padding:14px 16px;margin:0 0 20px;word-break:break-all}"
                + ".btn{display:block;width:100%;padding:14px;border:0;border-radius:10px;background:#1f6f64;color:#fff;font-size:16px;font-weight:700;cursor:pointer}"
                + ".btn:active{opacity:.85}"
                + ".tip{color:#999;font-size:12px;line-height:1.6;margin-top:14px}"
                + "</style></head><body><div class=\"card\"><h1>" + escape(title) + "</h1>" + bodyHtml + "</div></body></html>";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public record CreateRequest(Long packageId, Integer payMethod, String remark) {
    }
}
