package com.wardrobe.agent.tryon;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.wardrobe.agent.common.PrefixIds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.time.Duration;

/** OpenAI-compatible Images Edit adapter. Inputs stay in memory and are never logged. */
@Component
public class GptImageEditTryOnProvider implements TryOnProvider {
    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final String size;
    private final String quality;

    @Autowired
    public GptImageEditTryOnProvider(RestClient.Builder builder,
                                     @Value("${app.tryon.gpt-image-edit.base-url:${AI_IMAGE_URL:${AI_BASE_URL:https://api.openai.com}}}") String baseUrl,
                                     @Value("${app.tryon.gpt-image-edit.api-key:}") String apiKey,
                                     @Value("${app.tryon.gpt-image-edit.model:gpt-image-1}") String model,
                                     @Value("${app.tryon.gpt-image-edit.size:1024x1024}") String size,
                                     @Value("${app.tryon.gpt-image-edit.quality:low}") String quality,
                                     @Value("${app.tryon.http-connect-timeout:PT10S}") Duration connectTimeout,
                                     @Value("${app.tryon.http-read-timeout:PT2M}") Duration readTimeout) {
        this(TryOnHttpClients.withTimeouts(builder, connectTimeout, readTimeout),
                baseUrl, apiKey, model, size, quality);
    }

    GptImageEditTryOnProvider(RestClient.Builder builder,
                              String baseUrl,
                              String apiKey,
                              String model,
                              String size,
                              String quality) {
        this.client = builder.baseUrl(trimTrailingSlash(baseUrl)).build();
        this.apiKey = apiKey;
        this.model = model;
        this.size = size;
        this.quality = quality;
    }

    @Override public String code() { return "GPT_IMAGE_EDIT"; }

    @Override public ProviderCapabilities capabilities() {
        return new ProviderCapabilities("GPT Image 双图编辑", true,
                Set.of("INNER_TOP", "OUTER_TOP", "BOTTOM", "DRESS"), 10, 90);
    }

    @Override public boolean available() {
        return notBlank(apiKey) && !"missing".equals(apiKey) && notBlank(model);
    }

    @Override public ProviderSubmission submit(ProviderRequest request) {
        if (!available()) throw new IllegalStateException("GPT Image Edit is not configured");
        DataImage modelImage = decode(request.modelImage(), "model.png");
        DataImage garmentImage = decode(request.garmentImage(), "garment.png");
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("model", model);
        body.part("image[]", resource(modelImage)).contentType(modelImage.mediaType());
        body.part("image[]", resource(garmentImage)).contentType(garmentImage.mediaType());
        body.part("prompt", prompt(request));
        body.part("size", size);
        body.part("quality", quality);

        EditResponse response = client.post().uri("/v1/images/edits")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve().body(EditResponse.class);
        EditImage image = response == null || response.data() == null || response.data().isEmpty()
                ? null : response.data().getFirst();
        String base64 = image == null ? null : image.b64_json();
        if (!notBlank(base64) && image != null && notBlank(image.url())) {
            base64 = downloadAsPngDataUri(image.url());
        }
        ProviderResult result = notBlank(base64)
                ? new ProviderResult(ProviderResult.State.SUCCEEDED, "completed",
                        base64.startsWith("data:") ? base64 : "data:image/png;base64," + base64,
                        null, null, null)
                : new ProviderResult(ProviderResult.State.FAILED, "failed", null, null,
                        "GPT_IMAGE_EMPTY_OUTPUT", "GPT Image 编辑未返回可持久化的 Base64 图片");
        return new ProviderSubmission(PrefixIds.next("gptedit"), result.providerStatus(), result);
    }

    @Override public ProviderResult query(String providerTaskId) {
        return new ProviderResult(ProviderResult.State.FAILED, "failed", null, null,
                "GPT_IMAGE_SYNC_RESULT_MISSING", "GPT Image 同步编辑结果不可恢复");
    }

    private String prompt(ProviderRequest request) {
        return "Use the first image as the current person reference and the second image as the garment reference. "
                + "Edit the first image so the person wears the referenced " + safe(request.category())
                + " item named '" + safe(request.itemName()) + "'. Preserve the person's identity, body, pose, "
                + "background, and all clothing from earlier stages. Preserve the new garment's color, pattern, "
                + "construction, and fit as closely as possible. Produce one full-body fashion preview.";
    }

    private DataImage decode(String dataUri, String filename) {
        if (dataUri == null || !dataUri.matches("^data:image/(png|jpeg|jpg);base64,.+")) {
            throw new IllegalArgumentException("GPT Image Edit requires a PNG or JPEG Data URI");
        }
        int separator = dataUri.indexOf(',');
        String subtype = dataUri.substring("data:image/".length(), dataUri.indexOf(';'));
        try {
            byte[] bytes = Base64.getDecoder().decode(dataUri.substring(separator + 1));
            if (bytes.length == 0) throw new IllegalArgumentException("Image Data URI is empty");
            MediaType type = "png".equals(subtype) ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return new DataImage(bytes, filename, type);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("GPT Image Edit received invalid Base64 image data", exception);
        }
    }

    private ByteArrayResource resource(DataImage image) {
        return new ByteArrayResource(image.bytes()) {
            @Override public String getFilename() { return image.filename(); }
        };
    }

    /** 兼容只返回临时 URL 的网关；结果立即下载并转成项目可持久化的 PNG Data URI。 */
    private String downloadAsPngDataUri(String value) {
        if (value.startsWith("data:image/")) {
            if (!value.matches("^data:image/(png|jpeg|jpg);base64,.+")) {
                throw new IllegalArgumentException("GPT Image Edit returned an unsupported image Data URI");
            }
            return value;
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("GPT Image Edit returned an invalid image URL", exception);
        }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("GPT Image Edit returned an unsupported image URL");
        }
        ResponseEntity<byte[]> response = client.get().uri(uri)
                .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
                .retrieve().toEntity(byte[].class);
        byte[] bytes = response.getBody();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("GPT Image Edit returned an empty image URL response");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new IllegalArgumentException("GPT Image Edit URL did not contain an image");
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", png)) throw new IllegalArgumentException("GPT Image Edit image could not be encoded");
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalStateException("GPT Image Edit image URL could not be decoded", exception);
        }
    }

    private String safe(String value) {
        if (!notBlank(value)) return "wardrobe";
        String sanitized = value.replaceAll("[^\\p{L}\\p{N} \\-_]", " ").replaceAll("\\s+", " ").trim();
        return sanitized.substring(0, Math.min(100, sanitized.length()));
    }

    private boolean notBlank(String value) { return value != null && !value.isBlank(); }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "https://api.openai.com";
        String normalized = value.trim().replaceAll("/+$", "");
        // OpenAI-compatible gateways commonly expose either the API root or its /v1 root.
        return normalized.endsWith("/v1") ? normalized.substring(0, normalized.length() - 3) : normalized;
    }

    private record DataImage(byte[] bytes, String filename, MediaType mediaType) {}
    record EditResponse(List<EditImage> data) {}
    record EditImage(@JsonAlias({"b64Json", "base64"}) String b64_json,
                     @JsonAlias({"image_url", "imageUrl"}) String url) {}
}
