package com.wardrobe.agent.media;

import com.wardrobe.agent.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Service
/** 用户图片的受控存储服务：校验类型和尺寸、真实解码、重新编码并限制存储路径。 */
public class MediaStorageService {
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final MediaFileRepository files;
    private final Path root;
    private final int maxWidth;
    private final int maxHeight;
    private final long maxPixels;

    public MediaStorageService(MediaFileRepository files,
                               @Value("${app.media.root}") String root,
                               @Value("${app.media.max-width}") int maxWidth,
                               @Value("${app.media.max-height}") int maxHeight,
                               @Value("${app.media.max-pixels}") long maxPixels) {
        this.files = files;
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.maxPixels = maxPixels;
    }

    @Transactional
    /** 不信任浏览器声明的文件内容；解码后统一重编码为 PNG，并保存安全元数据。 */
    public MediaFileView store(String userId, String purpose, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "请选择图片");
        }
        String claimedType = String.valueOf(upload.getContentType()).toLowerCase(Locale.ROOT);
        if (!TYPES.contains(claimedType)) {
            throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_IMAGE_TYPE", "仅支持 JPEG、PNG 或 WebP");
        }
        try {
            byte[] input = upload.getBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(input));
            if (image == null) {
                throw invalidImage("文件不是可解码的图片");
            }
            long pixels = (long) image.getWidth() * image.getHeight();
            if (image.getWidth() > maxWidth || image.getHeight() > maxHeight || pixels > maxPixels) {
                throw invalidImage("图片尺寸或总像素超出限制");
            }
            return storeDecoded(userId, purpose, image);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "图片读取失败");
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_STORE_FAILED", "图片保存失败");
        }
    }

    /** 通过 ownerId 和 READY 状态校验文件是否可被当前用户引用。 */
    public MediaFile requireOwned(String id, String userId) {
        return files.findByIdAndOwnerIdAndStatus(id, userId, "READY")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "图片不存在"));
    }

    /** 将受控磁盘文件包装成 Spring Resource，供 HTTP 或多模态 Prompt 读取。 */
    public Resource load(MediaFile media) {
        try {
            Path path = root.resolve(media.getStoragePath()).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "图片不存在");
            }
            return new UrlResource(path.toUri());
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "图片不存在");
        }
    }

    /** 将受控项目图片清洗后编码成 Data URI，真实 Provider 不会看到本地路径或永久公网地址。 */
    public String toDataUri(String userId, String imageUrl) {
        try (var input = resolveControlled(userId, imageUrl).getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw invalidImage("图片无法解码");
            ByteArrayOutputStream clean = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", clean)) throw invalidImage("图片无法重新编码");
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(clean.toByteArray());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "试穿图片读取失败");
        }
    }

    /** 解码 Provider 返回的 Data URI，重新编码后写入当前用户的项目存储。 */
    @Transactional
    public MediaFileView storeGeneratedDataUri(String userId, String dataUri) {
        if (dataUri == null || !dataUri.matches("^data:image/(png|jpeg|jpg);base64,.+")) {
            throw invalidImage("Provider 返回的图片格式无效");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw invalidImage("Provider 返回的图片无法解码");
            return storeDecoded(userId, "TRYON_RESULT", image);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalidImage("Provider 返回的 Base64 无效");
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_STORE_FAILED", "试穿结果保存失败");
        }
    }

    private Resource resolveControlled(String userId, String imageUrl) {
        if (imageUrl != null && imageUrl.matches("/assets/seed/[A-Za-z0-9._-]+")) {
            Resource resource = new ClassPathResource("static" + imageUrl);
            if (resource.exists()) return resource;
        }
        if (imageUrl != null && imageUrl.matches("/api/files/[A-Za-z0-9_-]+")) {
            String fileId = imageUrl.substring("/api/files/".length());
            return load(requireOwned(fileId, userId));
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "UNCONTROLLED_IMAGE", "试穿只能使用项目受控图片");
    }

    private MediaFileView storeDecoded(String userId, String purpose, BufferedImage image) throws Exception {
        long pixels = (long) image.getWidth() * image.getHeight();
        if (image.getWidth() > maxWidth || image.getHeight() > maxHeight || pixels > maxPixels) {
            throw invalidImage("图片尺寸或总像素超出限制");
        }
        ByteArrayOutputStream clean = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", clean)) throw invalidImage("图片无法重新编码");
        byte[] output = clean.toByteArray();
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(output));
        Path userDir = root.resolve(userId).normalize();
        if (!userDir.startsWith(root)) throw invalidImage("非法存储路径");
        Files.createDirectories(userDir);
        String fileName = com.wardrobe.agent.common.PrefixIds.next("img") + ".png";
        Path target = userDir.resolve(fileName);
        Files.write(target, output);
        String relative = root.relativize(target).toString().replace('\\', '/');
        MediaFile media = files.save(new MediaFile(userId, normalizePurpose(purpose), "image/png", output.length, relative, sha));
        return view(media);
    }

    private MediaFileView view(MediaFile media) {
        return new MediaFileView(media.getId(), media.getPurpose(), media.getMimeType(), media.getSizeBytes(), "/api/files/" + media.getId());
    }

    private String normalizePurpose(String purpose) {
        String normalized = purpose == null ? "WARDROBE" : purpose.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("WARDROBE", "USER_MODEL", "TRYON_RESULT").contains(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_PURPOSE", "不支持的图片用途");
        }
        return normalized;
    }

    private BusinessException invalidImage(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", message);
    }
}
