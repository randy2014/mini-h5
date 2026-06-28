package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.vip.entity.VipOrder;
import com.mini.novel.vip.entity.VipPlan;
import com.mini.novel.vip.mapper.VipOrderMapper;
import com.mini.novel.vip.mapper.VipPlanMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/vip")
public class AdminVipController {
    private final VipPlanMapper vipPlanMapper;
    private final VipOrderMapper vipOrderMapper;

    public AdminVipController(VipPlanMapper vipPlanMapper, VipOrderMapper vipOrderMapper) {
        this.vipPlanMapper = vipPlanMapper;
        this.vipOrderMapper = vipOrderMapper;
    }

    @GetMapping("/plans")
    public Result<List<VipPlan>> plans() {
        return Result.ok(vipPlanMapper.selectList(new LambdaQueryWrapper<VipPlan>()
                .orderByAsc(VipPlan::getSort)
                .orderByDesc(VipPlan::getUpdatedAt)));
    }

    @PostMapping("/plans")
    public Result<VipPlan> createPlan(@RequestBody VipPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        plan.setEnabled(plan.getEnabled() == null || plan.getEnabled());
        plan.setSort(plan.getSort() == null ? 0 : plan.getSort());
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        vipPlanMapper.insert(plan);
        return Result.ok(plan);
    }

    @PutMapping("/plans/{id}")
    public Result<VipPlan> updatePlan(@PathVariable("id") Long id, @RequestBody VipPlan plan) {
        plan.setId(id);
        plan.setUpdatedAt(LocalDateTime.now());
        vipPlanMapper.updateById(plan);
        return Result.ok(vipPlanMapper.selectById(id));
    }

    @PutMapping("/plans/{id}/enabled")
    public Result<VipPlan> enabled(@PathVariable("id") Long id, @RequestBody EnabledRequest request) {
        VipPlan plan = new VipPlan();
        plan.setId(id);
        plan.setEnabled(Boolean.TRUE.equals(request.enabled()));
        plan.setUpdatedAt(LocalDateTime.now());
        vipPlanMapper.updateById(plan);
        return Result.ok(vipPlanMapper.selectById(id));
    }

    @GetMapping("/orders")
    public Result<List<VipOrder>> orders() {
        return Result.ok(vipOrderMapper.selectList(new LambdaQueryWrapper<VipOrder>()
                .orderByDesc(VipOrder::getCreatedAt)
                .last("LIMIT 200")));
    }

    @PutMapping("/orders/{id}/paid")
    public Result<VipOrder> paid(@PathVariable("id") Long id) {
        VipOrder order = vipOrderMapper.selectById(id);
        if (order != null) {
            order.setPayStatus(1);
            order.setPaidAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            vipOrderMapper.updateById(order);
        }
        return Result.ok(vipOrderMapper.selectById(id));
    }

    public record EnabledRequest(Boolean enabled) {
    }
}
