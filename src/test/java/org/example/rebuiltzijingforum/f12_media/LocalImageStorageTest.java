package org.example.rebuiltzijingforum.f12_media;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f12_media.config.StorageProperties;
import org.example.rebuiltzijingforum.f12_media.model.ImageKind;
import org.example.rebuiltzijingforum.f12_media.model.StoredImage;
import org.example.rebuiltzijingforum.f12_media.service.ImageValidator;
import org.example.rebuiltzijingforum.f12_media.service.LocalImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageStorageTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/JxQAAAAASUVORK5CYII=");
    @TempDir Path temp;

    @Test
    void storesAvatarAndPostUnderServerControlledPaths() {
        LocalImageStorage storage = storage();

        StoredImage avatar = storage.store(file(), ImageKind.AVATAR, 42L);
        StoredImage post = storage.store(file(), ImageKind.POST, 42L);

        assertThat(avatar.objectKey()).startsWith("avatars/42/").endsWith(".png");
        assertThat(avatar.url()).isEqualTo("/uploads/" + avatar.objectKey());
        assertThat(temp.resolve(avatar.objectKey())).exists();
        assertThat(storage.objectKeyFromUrl(avatar.url())).contains(avatar.objectKey());
        assertThat(post.objectKey()).startsWith("posts/2026/10/").endsWith(".png");
        assertThat(temp.resolve(post.objectKey())).exists();

        storage.delete(post.objectKey());
        assertThat(temp.resolve(post.objectKey())).doesNotExist();
    }

    @Test
    void rejectsKeysThatEscapeConfiguredRoot() {
        assertThatThrownBy(() -> storage().delete("../../outside.txt"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("FILE_PATH_ERROR"));
        assertThat(storage().objectKeyFromUrl("https://example.com/avatar.png")).isEmpty();
        assertThat(storage().objectKeyFromUrl("/uploads/../../outside.txt")).isEmpty();
    }

    private LocalImageStorage storage() {
        StorageProperties properties = new StorageProperties();
        properties.setLocalRoot(temp.toString());
        return new LocalImageStorage(new ImageValidator(), properties,
                Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "client.png", "image/png", PNG);
    }
}
