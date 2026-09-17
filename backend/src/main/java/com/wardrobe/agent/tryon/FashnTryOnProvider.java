package com.wardrobe.agent.tryon;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Duration;

@Component
public class FashnTryOnProvider implements TryOnProvider {
    private final RestClient client;
    private final String apiKey;

    @Autowired
    public FashnTryOnProvider(RestClient.Builder builder,
                              @Value("${app.tryon.fashn.base-url:https://api.fashn.ai}") String baseUrl,
                              @Value("${app.tryon.fashn.api-key:}") String apiKey,
                              @Value("${app.tryon.http-connect-timeout:PT10S}") Duration connectTimeout,
                              @Value("${app.tryon.http-read-timeout:PT2M}") Duration readTimeout) {
        this(TryOnHttpClients.withTimeouts(builder, connectTimeout, readTimeout), baseUrl, apiKey);
    }

    FashnTryOnProvider(RestClient.Builder builder, String baseUrl, String apiKey) {
        this.client = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Override public String code() { return "FASHN_V1_6"; }
    @Override public ProviderCapabilities capabilities() {
        return new ProviderCapabilities("FASHN 真实试穿", true,
                Set.of("INNER_TOP", "OUTER_TOP", "BOTTOM", "DRESS"), 5, 20);
    }

    @Override public ProviderSubmission submit(ProviderRequest request) {
        requireKey();
        RunResponse response = client.post().uri("/v1/run")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(Map.of("model_name", "tryon-v1.6", "inputs", Map.of(
                        "model_image", request.modelImage(),
                        "garment_image", request.garmentImage(),
                        "category", request.category(),
                        "mode", "balanced",
                        "num_samples", 1,
                        "output_format", "jpeg",
                        "return_base64", true,
                        "moderation_level", "conservative",
                        "garment_photo_type", "auto")))
                .retrieve().body(RunResponse.class);
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new IllegalStateException("FASHN did not return a prediction id");
        }
        return new ProviderSubmission(response.id(), "starting");
    }

    @Override public ProviderResult query(String providerTaskId) {
        requireKey();
        StatusResponse response = client.get().uri("/v1/status/{id}", providerTaskId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve().body(StatusResponse.class);
        if (response == null || response.status() == null) {
            throw new IllegalStateException("FASHN returned an empty status response");
        }
        return switch (response.status()) {
            case "starting", "in_queue", "processing" -> new ProviderResult(
                    ProviderResult.State.PROCESSING, response.status(), null, null, null, null);
            case "completed" -> completed(response);
            case "failed" -> new ProviderResult(ProviderResult.State.FAILED, response.status(), null, null,
                    response.error() == null ? "FASHN_GENERATION_FAILED" : response.error().name(),
                    response.error() == null ? "FASHN 生成失败" : response.error().message());
            default -> throw new IllegalStateException("Unknown FASHN status: " + response.status());
        };
    }

    private ProviderResult completed(StatusResponse response) {
        String output = response.output() == null || response.output().isEmpty() ? null : response.output().getFirst();
        if (output == null || output.isBlank()) {
            return new ProviderResult(ProviderResult.State.FAILED, response.status(), null, null,
                    "FASHN_EMPTY_OUTPUT", "FASHN 未返回生成图片");
        }
        if (output.startsWith("data:image/")) {
            return new ProviderResult(ProviderResult.State.SUCCEEDED, response.status(), output, null, null, null);
        }
        return new ProviderResult(ProviderResult.State.SUCCEEDED, response.status(), null, output, null, null);
    }

    private void requireKey() {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("FASHN_API_KEY is not configured");
    }

    record RunResponse(String id, Object error) {}
    record StatusResponse(String id, String status, List<String> output, ProviderError error) {}
    record ProviderError(String name, String message) {}
}
