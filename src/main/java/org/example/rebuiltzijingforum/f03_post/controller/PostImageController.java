package org.example.rebuiltzijingforum.f03_post.controller;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f02_profile.utils.AvatarFileUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/** 富文本编辑器上传本机文件，URL 指向本机磁盘；以后可替换成对象存储。 */
@RestController
public class PostImageController {
    @PostMapping(value = "/api/posts/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam MultipartFile file) throws IOException {
        AvatarFileUtils.validate(file);
        String generated = AvatarFileUtils.generateFileName(0, file.getContentType());
        String name = generated.substring(generated.indexOf('/') + 1);
        Path root = Paths.get("uploads", "posts").toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) throw new BusinessException("FILE_PATH_ERROR", "图片路径不合法", HttpStatus.BAD_REQUEST);
        file.transferTo(target);
        return Map.of("errno", 0, "data", Map.of("url", "/uploads/posts/" + name));
    }
}
