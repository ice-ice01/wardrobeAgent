package com.wardrobe.agent.agent;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "conversation")
/** 用户与 Agent 的一个独立会话；消息和搭配通过 conversationId 与它关联。 */
public class Conversation extends AuditedEntity {
    @Column(nullable = false, length = 40) private String userId;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false) private boolean active;
    private Instant lastMessageAt;

    protected Conversation() {}
    public Conversation(String userId, String title) {
        super("conv"); this.userId = userId; this.title = title; this.active = true;
    }
    /** 新消息到达时更新时间，并在仍为默认标题时用消息内容生成简短标题。 */
    public void touch(String candidateTitle) {
        this.lastMessageAt = Instant.now();
        if ((title == null || "新对话".equals(title)) && candidateTitle != null && !candidateTitle.isBlank()) {
            this.title = candidateTitle.length() > 40 ? candidateTitle.substring(0, 40) : candidateTitle;
        }
    }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public boolean isActive() { return active; }
    public Instant getLastMessageAt() { return lastMessageAt; }
}
