package com.wardrobe.agent.agent;

import com.wardrobe.agent.wardrobe.WardrobeItem;
import com.wardrobe.agent.wardrobe.WardrobeItemCommand;
import com.wardrobe.agent.wardrobe.WardrobeSlot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试 Java 侧的搭配安全边界。
 * AI 输出只能作为建议，最终商品选择仍必须由确定性的 Java 规则校验和补全。
 */
class AgentSelectionPolicyTest {
    @Test
    /** 模型即使“编造”了候选集之外的 ID，也不能让该商品进入最终搭配。 */
    void modelCannotSelectItemOutsideJavaCandidateWhitelist() {
        WardrobeItem top = item("上衣", WardrobeSlot.INNER_TOP);
        WardrobeItem bottom = item("下装", WardrobeSlot.BOTTOM);
        WardrobeItem shoes = item("鞋履", WardrobeSlot.SHOES);
        List<WardrobeItem> candidates = List.of(top, bottom, shoes);

        List<WardrobeItem> selected = AgentService.normalizeSelection(
                new AiProposal("方案", List.of("item_from_another_user", top.getId()), "reason"),
                candidates, Set.of(), "日常", 0);

        assertThat(selected).containsExactly(top, bottom, shoes);
        assertThat(selected).allMatch(candidates::contains);
    }

    private static WardrobeItem item(String name, WardrobeSlot slot) {
        WardrobeItemCommand command = new WardrobeItemCommand(name, slot.name(), slot, "蓝色", "棉",
                Set.of("四季"), Set.of("日常"), Set.of("休闲"), 3, 3, "file-test");
        return new WardrobeItem("user-a", command, "/api/files/test", "UPLOAD", "file-test");
    }
}
