package com.mini.novel.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ticket")
public class TicketController {
    private final TicketMapper ticketMapper;
    private final TicketReplyMapper ticketReplyMapper;
    private final CurrentUserResolver currentUserResolver;

    public TicketController(TicketMapper ticketMapper, TicketReplyMapper ticketReplyMapper,
                            CurrentUserResolver currentUserResolver) {
        this.ticketMapper = ticketMapper;
        this.ticketReplyMapper = ticketReplyMapper;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/list")
    public Result<List<Ticket>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AppUser user = currentUserResolver.requireUser(userId);
        return Result.ok(ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getUserId, user.getId())
                .orderByDesc(Ticket::getId)));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id,
                                              @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AppUser user = currentUserResolver.requireUser(userId);
        Ticket ticket = requireOwned(id, user.getId());
        List<TicketReply> replies = ticketReplyMapper.selectList(new LambdaQueryWrapper<TicketReply>()
                .eq(TicketReply::getTicketId, id)
                .orderByAsc(TicketReply::getId));
        return Result.ok(Map.of("ticket", ticket, "replies", replies));
    }

    @PostMapping
    public Result<Ticket> create(@RequestBody CreateRequest request,
                                 @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AppUser user = currentUserResolver.requireUser(userId);
        String title = request.title() == null ? "" : request.title().trim();
        String content = request.content() == null ? "" : request.content().trim();
        if (title.isEmpty() || content.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "标题和正文不能为空");
        }
        if (title.length() + content.length() > 300) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "标题和正文合计不能超过300字");
        }
        Ticket ticket = new Ticket();
        ticket.setUserId(user.getId());
        ticket.setTitle(title);
        ticket.setContent(content);
        ticket.setStatus(Ticket.STATUS_OPEN);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.insert(ticket);
        return Result.ok(ticket);
    }

    @PutMapping("/{id}/close")
    public Result<Ticket> close(@PathVariable Long id,
                                @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AppUser user = currentUserResolver.requireUser(userId);
        Ticket ticket = requireOwned(id, user.getId());
        ticket.setStatus(Ticket.STATUS_CLOSED);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return Result.ok(ticket);
    }

    private Ticket requireOwned(Long id, Long userId) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null || !ticket.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "工单不存在");
        }
        return ticket;
    }

    public record CreateRequest(String title, String content) {
    }
}
