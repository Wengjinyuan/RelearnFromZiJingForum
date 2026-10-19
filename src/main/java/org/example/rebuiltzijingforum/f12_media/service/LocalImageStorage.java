package org.example.rebuiltzijingforum.f12_media.service;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f12_media.config.StorageProperties;
import org.example.rebuiltzijingforum.f12_media.model.ImageKind;
import org.example.rebuiltzijingforum.f12_media.model.StoredImage;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

public class LocalImageStorage implements ImageStorage {
    private static final DateTimeFormatter MONTH_PATH = DateTimeFormatter.ofPattern("yyyy/MM");
    private final ImageValidator validator;
    private final Path root;
    private final Clock clock;

    public LocalImageStorage(ImageValidator validator, StorageProperties properties, Clock clock) {
        this.validator = validator;
        this.root = Paths.get(properties.getLocalRoot()).toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Override
    public StoredImage store(MultipartFile file, ImageKind kind, long ownerId) {
        ImageValidator.ValidatedImage image = validator.validate(file);
        String key = switch (kind) {
            case AVATAR -> "avatars/" + ownerId + "/" + UUID.randomUUID() + "." + image.extension();
            case POST -> "posts/" + YearMonth.now(clock).format(MONTH_PATH)
                    + "/" + UUID.randomUUID() + "." + image.extension();
        };
        Path target = resolveKey(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, image.bytes(), StandardOpenOption.CREATE_NEW);
            return new StoredImage(key, "/uploads/" + key.replace('\\', '/'));
        } catch (IOException failure) {
            throw unavailable("图片保存失败", failure);
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolveKey(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException failure) {
            throw unavailable("图片删除失败", failure);
        }
    }

    @Override
    public Optional<String> objectKeyFromUrl(String url) {
        if (url == null || !url.startsWith("/uploads/")) return Optional.empty();
        String key = url.substring("/uploads/".length()).replace('\\', '/');
        if (key.isBlank() || key.contains("..")) return Optional.empty();
        try {
            resolveKey(key);
            return Optional.of(key);
        } catch (BusinessException invalid) {
            return Optional.empty();
        }
    }

    private Path resolveKey(String key) {
        if (key == null || key.isBlank() || key.contains("..") || Paths.get(key).isAbsolute()) {
            throw pathError();
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw pathError();
        return target;
    }

    private BusinessException pathError() {
        return new BusinessException("FILE_PATH_ERROR", "图片路径不合法", HttpStatus.BAD_REQUEST);
    }

    private BusinessException unavailable(String message, Exception cause) {
        return new BusinessException("STORAGE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
