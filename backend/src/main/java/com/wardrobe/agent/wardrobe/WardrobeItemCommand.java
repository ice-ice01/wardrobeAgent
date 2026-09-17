package com.wardrobe.agent.wardrobe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

//新增和修改衣物的参数
/** 创建或更新衣物的命令 DTO；Bean Validation 在 Controller 层拒绝非法属性。 */
public record WardrobeItemCommand(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 30) String category,
        @NotNull WardrobeSlot slot,
        @Size(max = 40) String color,
        @Size(max = 60) String material,
        @Size(max = 8) Set<@Size(max = 20) String> seasonTags,
        @Size(max = 12) Set<@Size(max = 30) String> sceneTags,
        @Size(max = 12) Set<@Size(max = 30) String> styleTags,
        @Min(1) @Max(5) Integer warmthLevel,
        @Min(1) @Max(5) Integer breathabilityLevel,
        String imageFileId
) {}
