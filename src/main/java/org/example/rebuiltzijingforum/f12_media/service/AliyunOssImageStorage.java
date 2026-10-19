package org.example.rebuiltzijingforum.f12_media.service;

import com.aliyun.oss.OSS;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f12_media.config.StorageProperties;
import org.example.rebuiltzijingforum.f12_media.model.ImageKind;
import org.example.rebuiltzijingforum.f12_media.model.StoredImage;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/** 阿里云 OSS 实现：客户端文件名不会进入对象 key，异常也不会把云厂商细节返回给浏览器。 */
public class AliyunOssImageStorage implements ImageStorage {
    private static final DateTimeFormatter MONTH_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final ImageValidator validator;
    private final String bucket;
    private final String publicBaseUrl;
    private final OssClientFactory clients;
    private final Clock clock;

    public AliyunOssImageStorage(ImageValidator validator, StorageProperties properties,
                                 OssClientFactory clients, Clock clock) {
        this.validator = validator;
        this.bucket = properties.getOss().getBucket().trim();
        this.publicBaseUrl = properties.getOss().resolvedPublicBaseUrl();
        this.clients = clients;
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

        OSS client = clients.create();
        try (ByteArrayInputStream input = new ByteArrayInputStream(image.bytes())) {
            client.putObject(bucket, key, input);
            return new StoredImage(key, publicBaseUrl + "/" + key);
        } catch (Exception failure) {
            throw unavailable("OSS 图片上传失败");
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void delete(String objectKey) {
        if (!validKey(objectKey)) {
            throw new BusinessException("FILE_PATH_ERROR", "图片路径不合法", HttpStatus.BAD_REQUEST);
        }
        OSS client = clients.create();
        try {
            client.deleteObject(bucket, objectKey);
        } catch (Exception failure) {
            throw unavailable("OSS 图片删除失败");
        } finally {
            client.shutdown();
        }
    }

    @Override
    public Optional<String> objectKeyFromUrl(String url) {
        String prefix = publicBaseUrl + "/";
        if (url == null || !url.startsWith(prefix)) return Optional.empty();
        String key = url.substring(prefix.length());
        return validKey(key) ? Optional.of(key) : Optional.empty();
    }

    private boolean validKey(String key) {
        return key != null && !key.isBlank() && !key.contains("..") && !key.startsWith("/");
    }

    private BusinessException unavailable(String message) {
        return new BusinessException("STORAGE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
