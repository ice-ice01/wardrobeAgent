package com.wardrobe.agent.retrieval;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.ai.retrieval")
// 阈值、top-k、MMR权重、历史降权等配置
public class WardrobeRetrievalProperties {
    private boolean enabled = true;
    @Min(1) private int wardrobeThreshold = 30;
    @Min(1) private int initialRecall = 60;
    @Min(1) private int candidatesPerSlot = 4;
    @DecimalMin("0.0") @DecimalMax("1.0") private double relevanceWeight = 0.7;
    @DecimalMin("0.0") @DecimalMax("1.0") private double diversityWeight = 0.3;
    @DecimalMin("0.0") private double recentItemPenalty = 0.25;
    @DecimalMin("1.0") private double explorePenaltyMultiplier = 1.5;
    @NotBlank private String model = "text-embedding-3-small";
    @NotBlank private String indexVersion = "wardrobe-text-v1";
    private boolean fallbackEnabled = true;
    @Min(1) private int fallbackCandidatesPerSlot = 12;

    /** 返回是否启用衣橱语义检索。关闭后直接使用全部候选或传统截取逻辑。 */
    public boolean isEnabled() { return enabled; }

    /** 设置是否启用衣橱语义检索。 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** 返回启用语义检索的衣橱数量阈值；候选数不超过该值时无需向量召回。 */
    public int getWardrobeThreshold() { return wardrobeThreshold; }

    /** 设置启用语义检索的衣橱数量阈值。 */
    public void setWardrobeThreshold(int wardrobeThreshold) { this.wardrobeThreshold = wardrobeThreshold; }

    /** 返回按查询向量相关性排序后，第一阶段最多召回的商品数量。 */
    public int getInitialRecall() { return initialRecall; }

    /** 设置第一阶段语义召回的最大商品数量。 */
    public void setInitialRecall(int initialRecall) { this.initialRecall = initialRecall; }

    /** 返回 MMR 重排后每个衣物位置最多保留的候选商品数量。 */
    public int getCandidatesPerSlot() { return candidatesPerSlot; }

    /** 设置每个衣物位置最终保留的候选商品数量。 */
    public void setCandidatesPerSlot(int candidatesPerSlot) { this.candidatesPerSlot = candidatesPerSlot; }

    /** 返回 MMR 评分中语义相关性的权重；值越大越偏向与用户场景相似的商品。 */
    public double getRelevanceWeight() { return relevanceWeight; }

    /** 设置 MMR 评分中语义相关性的权重。 */
    public void setRelevanceWeight(double relevanceWeight) { this.relevanceWeight = relevanceWeight; }

    /** 返回 MMR 评分中的相似商品惩罚权重；值越大越强调候选之间的多样性。 */
    public double getDiversityWeight() { return diversityWeight; }

    /** 设置 MMR 评分中的多样性权重。 */
    public void setDiversityWeight(double diversityWeight) { this.diversityWeight = diversityWeight; }

    /** 返回最近展示商品及其相似商品的基础降权值。 */
    public double getRecentItemPenalty() { return recentItemPenalty; }

    /** 设置最近展示商品及其相似商品的基础降权值。 */
    public void setRecentItemPenalty(double recentItemPenalty) { this.recentItemPenalty = recentItemPenalty; }

    /** 返回“换一套”操作对最近展示商品降权值的放大倍数。 */
    public double getExplorePenaltyMultiplier() { return explorePenaltyMultiplier; }

    /** 设置“换一套”操作使用的最近商品惩罚放大倍数。 */
    public void setExplorePenaltyMultiplier(double explorePenaltyMultiplier) { this.explorePenaltyMultiplier = explorePenaltyMultiplier; }

    /** 返回生成和查询衣物向量时声明使用的 Embedding 模型名称。 */
    public String getModel() { return model; }

    /** 设置 Embedding 模型名称，用于区分由不同模型生成的向量。 */
    public void setModel(String model) { this.model = model; }

    /** 返回衣物向量索引版本，用于识别 Prompt 或索引结构的变化。 */
    public String getIndexVersion() { return indexVersion; }

    /** 设置衣物向量索引版本；版本变化后应为衣物重新建立索引。 */
    public void setIndexVersion(String indexVersion) { this.indexVersion = indexVersion; }

    /** 返回语义检索失败时是否允许使用传统候选截取逻辑降级。 */
    public boolean isFallbackEnabled() { return fallbackEnabled; }

    /** 设置语义检索失败时是否启用传统候选截取回退。 */
    public void setFallbackEnabled(boolean fallbackEnabled) { this.fallbackEnabled = fallbackEnabled; }

    /** 返回降级回退时每个衣物位置最多截取的商品数量。 */
    public int getFallbackCandidatesPerSlot() { return fallbackCandidatesPerSlot; }

    /** 设置降级回退时每个衣物位置最多截取的商品数量。 */
    public void setFallbackCandidatesPerSlot(int fallbackCandidatesPerSlot) { this.fallbackCandidatesPerSlot = fallbackCandidatesPerSlot; }
}
