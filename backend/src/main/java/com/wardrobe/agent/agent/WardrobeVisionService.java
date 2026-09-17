package com.wardrobe.agent.agent;

import com.wardrobe.agent.media.MediaStorageService;
import com.wardrobe.agent.wardrobe.WardrobeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@Service
/**
 * 为多模态搭配 Prompt 准备安全、尺寸受控的衣物图片。
 *
 * <p>这里不调用视觉模型；它只读取当前用户可访问的图片、缩放并压缩为 JPEG，
 * 随后由 SpringAiGateway 使用 {@code user.media(...)} 发送给聊天模型。</p>
 */
public class WardrobeVisionService {
    private static final Logger log = LoggerFactory.getLogger(WardrobeVisionService.class);
    private final MediaStorageService mediaStorage;
    private final boolean enabled;
    private final int maxImages;
    private final int maxWidth;
    private final int maxHeight;
    private final float jpegQuality;

    public WardrobeVisionService(MediaStorageService mediaStorage,
                                 @Value("${app.ai.multimodal.enabled:true}") boolean enabled,
                                 @Value("${app.ai.multimodal.max-images:30}") int maxImages,
                                 @Value("${app.ai.multimodal.max-width:512}") int maxWidth,
                                 @Value("${app.ai.multimodal.max-height:640}") int maxHeight,
                                 @Value("${app.ai.multimodal.jpeg-quality:0.82}") float jpegQuality) {
        this.mediaStorage = mediaStorage;
        this.enabled = enabled;
        this.maxImages = requirePositive(maxImages, "max-images");
        this.maxWidth = requirePositive(maxWidth, "max-width");
        this.maxHeight = requirePositive(maxHeight, "max-height");
        if (jpegQuality <= 0 || jpegQuality > 1) {
            throw new IllegalArgumentException("app.ai.multimodal.jpeg-quality must be in (0, 1]");
        }
        this.jpegQuality = jpegQuality;
    }

    /**
     * 按候选顺序准备最多 maxImages 张图片。单张失败会跳过，不阻断纯文本搭配。
     */
    public List<PromptImage> prepare(List<WardrobeItem> candidates) {
        if (!enabled || candidates == null || candidates.isEmpty()) return List.of();
        List<PromptImage> result = new ArrayList<>();
        int totalBytes = 0;
        for (WardrobeItem item : candidates) {
            if (result.size() >= maxImages) break;
            try {
                if (!"READY".equals(item.getImageStatus())) continue;
                Resource source = resolve(item);
                byte[] thumbnail = thumbnail(source);
                int ordinal = result.size() + 1;
                result.add(new PromptImage(item.getId(), ordinal, MimeTypeUtils.IMAGE_JPEG,
                        new NamedByteArrayResource(thumbnail, "wardrobe-" + ordinal + ".jpg"), thumbnail.length));
                totalBytes += thumbnail.length;
            } catch (Exception exception) {
                log.warn("Skipping multimodal image for wardrobe item {}: {}", item.getId(), exception.getMessage());
            }
        }
        log.info("Prepared {}/{} wardrobe images for multimodal prompt ({} bytes, limit {})",
                result.size(), candidates.size(), totalBytes, maxImages);
        return List.copyOf(result);
    }

    /**
     * 只解析受控资源：用户上传文件必须通过所有权校验，种子图必须满足白名单路径。
     */
    private Resource resolve(WardrobeItem item) {
        String fileId = firstNonBlank(item.getProcessedFileId(), item.getOriginalFileId());
        if (fileId != null) {
            return mediaStorage.load(mediaStorage.requireOwned(fileId, item.getUserId()));
        }
        String imageUrl = item.getImageUrl();
        String source = item.getImageSource() == null ? "" : item.getImageSource().toUpperCase(Locale.ROOT);
        if (("CATALOG".equals(source) || "SEED".equals(source))
                && imageUrl != null && imageUrl.matches("/assets/seed/[A-Za-z0-9._-]+")) {
            Resource resource = new ClassPathResource("static" + imageUrl);
            if (resource.exists()) return resource;
        }
        throw new IllegalArgumentException("No controlled image resource is available");
    }

    /** 缩放图片并统一转成 RGB JPEG，降低多模态请求体积和模型输入成本。 */
    private byte[] thumbnail(Resource resource) throws Exception {
        BufferedImage source;
        try (InputStream input = resource.getInputStream()) {
            source = ImageIO.read(input);
        }
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            throw new IllegalArgumentException("Image cannot be decoded");
        }
        double scale = Math.min(1.0, Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IllegalStateException("JPEG encoder is unavailable");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(jpegQuality);
            }
            writer.write(null, new IIOImage(target, null, null), parameters);
            imageOutput.flush();
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second == null || second.isBlank() ? null : second;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException("app.ai.multimodal." + name + " must be positive");
        return value;
    }

    /** 一张待附加到 Prompt 的图片；ordinal 用来和候选文本中的“第 N 张”对应。 */
    public record PromptImage(String itemId, int ordinal, MimeType mimeType, Resource resource, int byteSize) {}

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override public String getFilename() { return filename; }
    }
}
