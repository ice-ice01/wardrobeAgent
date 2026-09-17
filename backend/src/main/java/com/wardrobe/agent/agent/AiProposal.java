package com.wardrobe.agent.agent;

import java.util.List;

/**
 * AI 的结构化中间输出。selectedItemIds 仍是不可信数据，必须由 AgentService 校验后才能入库。
 */
public record




AiProposal(String title, List<String> selectedItemIds, String reason) {}
