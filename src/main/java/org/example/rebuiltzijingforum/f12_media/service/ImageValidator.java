package org.example.rebuiltzijingforum.f12_media.service;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** 只信任服务端验证过的图片字节和 MIME，不使用客户端文件名生成路径。 */
@Component
public class ImageValidator {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp");

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw error("FILE_EMPTY", "上传文件不能为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw error("FILE_TOO_LARGE", "图片不能超过20MB");
        }
        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw error("FILE_TYPE_ERROR", "只支持 JPG、PNG、GIF、WEBP 图片");
        }
        try {
            byte[] bytes = file.getBytes();
            boolean realImage = "image/webp".equals(contentType)
                    ? hasWebpSignature(bytes)
                    : isSafeDecodableImage(bytes, contentType);
            if (!realImage) {
                throw error("FILE_TYPE_ERROR", "上传文件不是真实图片");
            }
            return new ValidatedImage(bytes, contentType, extension);
        } catch (IOException failure) {
            throw error("FILE_READ_ERROR", "图片读取失败");
        }
    }

    private boolean hasWebpSignature(byte[] bytes) {
        if (bytes.length < 20
                || bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F'
                || bytes[8] != 'W' || bytes[9] != 'E' || bytes[10] != 'B' || bytes[11] != 'P') {
            return false;
        }
        long declaredRiffBytes = Integer.toUnsignedLong(
                Byte.toUnsignedInt(bytes[4])
                        | (Byte.toUnsignedInt(bytes[5]) << 8)
                        | (Byte.toUnsignedInt(bytes[6]) << 16)
                        | (Byte.toUnsignedInt(bytes[7]) << 24));
        boolean knownImageChunk = bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8'
                && (bytes[15] == ' ' || bytes[15] == 'L' || bytes[15] == 'X');
        return declaredRiffBytes == bytes.length - 8L && knownImageChunk;
    }

    private boolean isSafeDecodableImage(byte[] bytes, String contentType) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return false;
            Iterator<ImageReader> candidates = ImageIO.getImageReaders(input);
            if (!candidates.hasNext()) return false;
            ImageReader reader = candidates.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw error("IMAGE_DIMENSIONS_TOO_LARGE", "图片尺寸过大");
                }
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean formatMatches = switch (contentType) {
                    case "image/jpeg", "image/jpg" -> format.equals("jpeg") || format.equals("jpg");
                    case "image/png" -> format.equals("png");
                    case "image/gif" -> format.equals("gif");
                    default -> false;
                };
                if (!formatMatches) return false;
            } finally {
                reader.dispose();
            }
        }
        return ImageIO.read(new ByteArrayInputStream(bytes)) != null;
    }

    private BusinessException error(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }

    public record ValidatedImage(byte[] bytes, String contentType, String extension) {
        public ValidatedImage {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
