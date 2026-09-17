package com.wardrobe.agent.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** 用户的一轮消息；clientMessageId 用于幂等重放，锁定和排除集合约束搭配范围。 */
public record AgentMessageRequest(
        @NotBlank @Size(max = 80) String clientMessageId,
        @NotBlank @Size(max = 2000) String content,
        @Size(max = 2) Set<String> lockedItemIds,
        @Size(max = 20) Set<String> excludedItemIds,
        @Pattern(regexp = "EXPLORE_MORE") String action,
        @Size(max = 40) String sourceOutfitId
) {
    /** 将可选集合规范为空集合，并返回不可变副本供业务层安全使用。 */
    public Set<String> safeLocked() { return lockedItemIds == null ? Set.of() : Set.copyOf(lockedItemIds); }
    public Set<String> safeExcluded() { return excludedItemIds == null ? Set.of() : Set.copyOf(excludedItemIds); }
}
