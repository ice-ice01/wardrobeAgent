package com.wardrobe.agent.agent;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "message", uniqueConstraints = @UniqueConstraint(name = "uk_message_client", columnNames = {"user_id", "client_message_id", "role"}))
/** 可审计的业务消息；它保存完整历史，和只面向模型窗口的 ChatMemory 用途不同。 */
public class Message extends AuditedEntity {
    @Column(name = "user_id", nullable = false, length = 40) private String userId;
    @Column(nullable = false, length = 40) private String conversationId;
    @Column(name = "client_message_id", nullable = false, length = 80) private String clientMessageId;
    @Column(nullable = false, length = 20) private String role;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(nullable = false, length = 20) private String status;
    @Column(length = 30) private String promptVersion;
    @Column(length = 80) private String modelName;
    @Column(length = 30) private String toolVersion;

    protected Message() {}
    public Message(String userId, String conversationId, String clientMessageId, String role, String content, String status) {
        super("msg");
        this.userId = userId;
        this.conversationId = conversationId;
        this.clientMessageId = clientMessageId;
        this.role = role;
        this.content = content;
        this.status = status;
    }
    /** 将 PROCESSING 助手消息更新为最终状态，并记录模型、Prompt 和工具版本。 */
    public void complete(String content, String modelName) {
        this.content = content; this.status = "COMPLETED"; this.promptVersion = "wardrobe-v1";
        this.toolVersion = "tools-v1"; this.modelName = modelName;
    }
    /** 记录无法完成的一轮助手消息。 */
    public void fail(String content) { this.content = content; this.status = "FAILED"; }
    public String getUserId() { return userId; }
    public String getConversationId() { return conversationId; }
    public String getClientMessageId() { return clientMessageId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
}
