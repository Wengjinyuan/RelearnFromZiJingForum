package org.example.rebuiltzijingforum.f02_profile.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 旧版上传生成了重复的用户目录；已有 H2 头像继续可读。 */
@RestController
public class LegacyAvatarController {
    @GetMapping("/upload/avatar/{owner}/{folder}/{name}")
    public ResponseEntity<FileSystemResource> legacy(@PathVariable long owner, @PathVariable long folder,
            @PathVariable String name) {
        if (owner != folder || !name.matches("[a-fA-F0-9-]{36}\\.(png|jpg|jpeg|gif|webp)"))
            return ResponseEntity.notFound().build();
        Path root = Paths.get("update", "avatars").toAbsolutePath().normalize();
        Path target = root.resolve(Long.toString(folder)).resolve(name).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) return ResponseEntity.notFound().build();
        MediaType type = name.endsWith("png") ? MediaType.IMAGE_PNG : name.endsWith("gif") ? MediaType.IMAGE_GIF : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).body(new FileSystemResource(target));
    }
}
