package org.example.rebuiltzijingforum.f12_media;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f12_media.service.ImageValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidatorTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/JxQAAAAASUVORK5CYII=");
    private final ImageValidator validator = new ImageValidator();

    @Test
    void acceptsRealPngAndReturnsTrustedMetadata() {
        var file = new MockMultipartFile("file", "../../client-name.exe", "image/png", PNG);

        ImageValidator.ValidatedImage image = validator.validate(file);

        assertThat(image.extension()).isEqualTo("png");
        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.bytes()).isEqualTo(PNG);
    }

    @Test
    void acceptsWebpOnlyWhenRiffSizeAndKnownImageChunkExist() {
        byte[] webp = {'R', 'I', 'F', 'F', 12, 0, 0, 0, 'W', 'E', 'B', 'P',
                'V', 'P', '8', ' ', 0, 0, 0, 0};
        var file = new MockMultipartFile("file", "demo.webp", "image/webp", webp);

        assertThat(validator.validate(file).extension()).isEqualTo("webp");

        byte[] headerOnly = {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertCode(new MockMultipartFile("file", "header.webp", "image/webp", headerOnly),
                "FILE_TYPE_ERROR");
    }

    @Test
    void rejectsEmptyOversizedAndForgedFilesWithStableCodes() {
        assertCode(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]), "FILE_EMPTY");
        assertCode(new MockMultipartFile("file", "large.png", "image/png", new byte[20 * 1024 * 1024 + 1]),
                "FILE_TOO_LARGE");
        assertCode(new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()),
                "FILE_TYPE_ERROR");
        assertCode(new MockMultipartFile("file", "fake.webp", "image/webp", "not a webp".getBytes()),
                "FILE_TYPE_ERROR");
    }

    @Test
    void rejectsSmallCompressedFilesWithDangerousPixelDimensions() throws Exception {
        BufferedImage tooWide = new BufferedImage(8193, 1, BufferedImage.TYPE_BYTE_GRAY);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(tooWide, "png", encoded);

        assertCode(new MockMultipartFile("file", "wide.png", "image/png", encoded.toByteArray()),
                "IMAGE_DIMENSIONS_TOO_LARGE");
    }

    private void assertCode(MockMultipartFile file, String expectedCode) {
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedCode));
    }
}
