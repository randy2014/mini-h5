package com.mini.novel.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ticket_reply")
public class TicketReply {
    public static final int REPLIER_USER = 1;
    public static final int REPLIER_ADMIN = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private String content;
    private Integer replierType;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getReplierType() { return replierType; }
    public void setReplierType(Integer replierType) { this.replierType = replierType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
