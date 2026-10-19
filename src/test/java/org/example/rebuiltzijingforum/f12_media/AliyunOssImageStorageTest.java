package org.example.rebuiltzijingforum.f12_media;

import com.aliyun.oss.OSS;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f12_media.config.StorageProperties;
import org.example.rebuiltzijingforum.f12_media.model.ImageKind;
import org.example.rebuiltzijingforum.f12_media.service.AliyunOssImageStorage;
import org.example.rebuiltzijingforum.f12_media.service.ImageValidator;
import org.example.rebuiltzijingforum.f12_media.service.OssClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AliyunOssImageStorageTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/JxQAAAAASUVORK5CYII=");
    @Mock OSS oss;
    @Mock OssClientFactory clients;

    @Test
    void uploadsAndDeletesUsingBucketObjectKeyAndPublicBaseUrl() {
        when(clients.create()).thenReturn(oss);
        AliyunOssImageStorage storage = storage();

        var stored = storage.store(file(), ImageKind.AVATAR, 7L);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(oss).putObject(eq("forum-bucket"), key.capture(), any(InputStream.class));
        assertThat(key.getValue()).startsWith("avatars/7/").endsWith(".png");
        assertThat(stored.objectKey()).isEqualTo(key.getValue());
        assertThat(stored.url()).isEqualTo("https://cdn.example.test/" + key.getValue());
        verify(oss).shutdown();

        when(clients.create()).thenReturn(oss);
        storage.delete(stored.objectKey());
        verify(oss).deleteObject("forum-bucket", stored.objectKey());
        assertThat(storage.objectKeyFromUrl(stored.url())).contains(stored.objectKey());
    }

    @Test
    void mapsSdkFailureWithoutLeakingProviderDetails() {
        when(clients.create()).thenReturn(oss);
        when(oss.putObject(anyString(), anyString(), any(InputStream.class)))
                .thenThrow(new IllegalStateException("secret provider detail"));

        assertThatThrownBy(() -> storage().store(file(), ImageKind.POST, 7L))
                .isInstanceOfSatisfying(BusinessException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("STORAGE_UNAVAILABLE");
                    assertThat(failure.getMessage()).isEqualTo("OSS 图片上传失败");
                });
        verify(oss).shutdown();
    }

    private AliyunOssImageStorage storage() {
        StorageProperties properties = new StorageProperties();
        properties.getOss().setBucket("forum-bucket");
        properties.getOss().setPublicBaseUrl("https://cdn.example.test/");
        return new AliyunOssImageStorage(new ImageValidator(), properties, clients,
                Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "client.png", "image/png", PNG);
    }
}
