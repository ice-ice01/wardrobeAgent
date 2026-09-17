package com.wardrobe.agent.tryon;

import com.wardrobe.agent.common.PrefixIds;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 通用生图只能提供低保真效果预览，不能声明为高保真虚拟试穿。 */
@Component
public class SpringAiImageTryOnProvider implements TryOnProvider {
    private final ObjectProvider<ImageModel> imageModels;
    private final String model;
    private final String apiKey;

    public SpringAiImageTryOnProvider(ObjectProvider<ImageModel> imageModels,
                                      @Value("${app.tryon.spring-ai-image.model:gpt-image-1}") String model,
                                      @Value("${spring.ai.openai.image.api-key:missing}") String apiKey) {
        this.imageModels = imageModels;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override public String code() { return "SPRING_AI_IMAGE"; }

    @Override public ProviderCapabilities capabilities() {
        return new ProviderCapabilities("Spring AI 效果预览", true,
                Set.of("INNER_TOP", "OUTER_TOP", "BOTTOM", "DRESS"), 10, 60);
    }

    @Override public boolean available() {
        return imageModels.getIfAvailable() != null && apiKey != null && !apiKey.isBlank() && !"missing".equals(apiKey);
    }

    @Override public ProviderSubmission submit(ProviderRequest request) {
        ImageModel imageModel = imageModels.getIfAvailable();
        if (!available() || imageModel == null) throw new IllegalStateException("Spring AI ImageModel is not configured");
        String prompt = "Create a photorealistic, full-body fashion preview of an adult model wearing the selected "
                + request.category() + " wardrobe item named '" + safe(request.itemName()) + "'. "
                + "Use a neutral studio background and natural pose. Preserve realistic garment construction and colors. "
                + "This is a fashion inspiration preview, not an identity-preserving virtual try-on.";
        ImageResponse response = imageModel.call(new ImagePrompt(prompt, ImageOptionsBuilder.builder()
                .model(model).n(1).responseFormat("b64_json").build()));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("Spring AI ImageModel returned an empty response");
        }
        var image = response.getResult().getOutput();
        ProviderResult result;
        if (image.getB64Json() != null && !image.getB64Json().isBlank()) {
            result = new ProviderResult(ProviderResult.State.SUCCEEDED, "completed",
                    "data:image/png;base64," + image.getB64Json(), null, null, null);
        } else {
            result = new ProviderResult(ProviderResult.State.FAILED, "failed", null, null,
                    "SPRING_AI_BASE64_REQUIRED", "Spring AI 生图未返回可持久化的 Base64 图片");
        }
        return new ProviderSubmission(PrefixIds.next("aipreview"), result.providerStatus(), result);
    }

    @Override public ProviderResult query(String providerTaskId) {
        return new ProviderResult(ProviderResult.State.FAILED, "failed", null, null,
                "SPRING_AI_SYNC_RESULT_MISSING", "Spring AI 同步生图结果不可恢复");
    }

    private String safe(String value) {
        if (value == null) return "wardrobe item";
        String sanitized = value.replaceAll("[^\\p{L}\\p{N} \\-_]", " ").replaceAll("\\s+", " ").trim();
        if (sanitized.isBlank()) return "wardrobe item";
        return sanitized.substring(0, Math.min(100, sanitized.length()));
    }
}
