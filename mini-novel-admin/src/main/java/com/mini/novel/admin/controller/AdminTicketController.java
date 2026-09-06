package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.Ticket;
import com.mini.novel.user.entity.TicketReply;
import com.mini.novel.user.mapper.TicketMapper;
import com.mini.novel.user.mapper.TicketReplyMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ticket")
public class AdminTicketController {
    private final TicketMapper ticketMapper;
    private final TicketReplyMapper ticketReplyMapper;

    public AdminTicketController(TicketMapper ticketMapper, TicketReplyMapper ticketReplyMapper) {
        this.ticketMapper = ticketMapper;
        this.ticketReplyMapper = ticketReplyMapper;
    }

    @GetMapping("/list")
    public Result<List<Ticket>> list() {
        return Result.ok(ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .orderByDesc(Ticket::getId)));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "工单不存在");
        }
        List<TicketReply> replies = ticketReplyMapper.selectList(new LambdaQueryWrapper<TicketReply>()
                .eq(TicketReply::getTicketId, id)
                .orderByAsc(TicketReply::getId));
        return Result.ok(Map.of("ticket", ticket, "replies", replies));
    }

    @PostMapping("/{id}/reply")
    public Result<TicketReply> reply(@PathVariable Long id, @RequestBody ReplyRequest request) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "工单不存在");
        }
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "回复内容不能为空");
        }
        TicketReply reply = new TicketReply();
        reply.setTicketId(id);
        reply.setContent(content);
        reply.setReplierType(TicketReply.REPLIER_ADMIN);
        reply.setCreatedAt(LocalDateTime.now());
        ticketReplyMapper.insert(reply);
        return Result.ok(reply);
    }

    public record ReplyRequest(String content) {
    }
}
