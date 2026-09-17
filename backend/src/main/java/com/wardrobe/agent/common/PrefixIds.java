package com.wardrobe.agent.common;

import java.util.UUID;

/** 生成带类型前缀的随机业务 ID，例如 msg_xxx、plan_xxx。 */
public final class PrefixIds {
    private PrefixIds() {}

    public static String next(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
