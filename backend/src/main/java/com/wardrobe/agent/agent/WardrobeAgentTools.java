package com.wardrobe.agent.agent;

import com.wardrobe.agent.outfit.OutfitView;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 暴露给大模型的 Java 工具集合。
 *
 * <p>模型只能决定“是否调用”和提供参数，工具内部仍执行受信任的 Java 业务逻辑。
 * 每次请求创建一个新实例，避免不同用户共享本轮工具状态。</p>
 */
public final class WardrobeAgentTools {
    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");
    private final OutfitGenerator outfitGenerator;
    private final Clock clock;
    private OutfitToolResult outfitResult;

    public WardrobeAgentTools(OutfitGenerator outfitGenerator) {
        this(outfitGenerator, Clock.system(USER_ZONE));
    }

    WardrobeAgentTools(OutfitGenerator outfitGenerator, Clock clock) {
        this.outfitGenerator = outfitGenerator;
        this.clock = clock;
    }

    @Tool(description = "仅当用户明确要求生成、推荐、调整穿搭或换一套时调用。工具会查询当前用户真实衣橱、执行权限和硬规则校验，并创建方案。普通问答禁止调用。")
    /**
     * 生成一次穿搭。缓存结果可以避免模型在同一轮重复调用工具而重复创建方案。
     */
    public OutfitToolResult generateOutfit(
            @ToolParam(description = "从当前消息和会话上下文提取的完整穿搭场景与约束") String scene) {
        if (outfitResult == null) outfitResult = outfitGenerator.generate(scene);
        return outfitResult;
    }

    @Tool(description = "当用户询问今天日期、当前时间或星期时调用。不得依靠模型知识猜测当前时间。")
    /** 从服务器时钟读取准确时间，避免模型根据训练数据猜测日期。 */
    public String getCurrentDateTime() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(USER_ZONE);
        return DATE_TIME.format(now) + "，星期" + chineseWeekday(now) + "，时区 Asia/Shanghai";
    }

    /** 返回本轮是否生成过搭配，AgentService 据此决定是否发送 outfit.created 事件。 */
    public OutfitToolResult outfitResult() { return outfitResult; }

    private String chineseWeekday(ZonedDateTime value) {
        return switch (value.getDayOfWeek()) {
            case MONDAY -> "一";
            case TUESDAY -> "二";
            case WEDNESDAY -> "三";
            case THURSDAY -> "四";
            case FRIDAY -> "五";
            case SATURDAY -> "六";
            case SUNDAY -> "日";
        };
    }

    @FunctionalInterface
    /** 把工具调用适配为 AgentService 中受权限保护的搭配生成流程。 */
    public interface OutfitGenerator { OutfitToolResult generate(String scene); }

    /** 工具返回给模型的业务状态、说明文字和前端搭配对象。 */
    public record OutfitToolResult(String status, String message, OutfitView outfit) {}
}
