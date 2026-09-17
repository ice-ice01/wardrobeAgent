package com.wardrobe.agent.media;

import com.wardrobe.agent.security.CurrentUser;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/files")
/** 用户图片上传和读取入口；文件读取同样执行所有权校验，不暴露真实磁盘路径。 */
public class MediaController {
    private final MediaStorageService media;

    public MediaController(MediaStorageService media) { this.media = media; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MediaFileView upload(@RequestParam(defaultValue = "WARDROBE") String purpose,
                         @RequestPart("file") MultipartFile file) {
        return media.store(CurrentUser.require().id(), purpose, file);
    }

    @GetMapping("/{id}")
    ResponseEntity<Resource> get(@PathVariable String id) {
        var record = media.requireOwned(id, CurrentUser.require().id());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(record.getMimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(media.load(record));
    }
}
