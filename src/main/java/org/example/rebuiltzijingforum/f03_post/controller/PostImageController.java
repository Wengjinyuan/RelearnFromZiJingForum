package org.example.rebuiltzijingforum.f03_post.controller;

import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f12_media.model.ImageKind;
import org.example.rebuiltzijingforum.f12_media.model.StoredImage;
import org.example.rebuiltzijingforum.f12_media.service.ImageStorage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 富文本编辑器上传本机文件，URL 指向本机磁盘；以后可替换成对象存储。 */
@RestController
public class PostImageController {
    private final ImageStorage imageStorage;

    public PostImageController(ImageStorage imageStorage) {
        this.imageStorage = imageStorage;
    }

    @PostMapping(value = "/api/posts/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO currentUser,
            @RequestParam MultipartFile file) {
        StoredImage stored = imageStorage.store(file, ImageKind.POST, currentUser.userId());
        return Map.of("errno", 0, "data", Map.of("url", stored.url()));
    }
}
