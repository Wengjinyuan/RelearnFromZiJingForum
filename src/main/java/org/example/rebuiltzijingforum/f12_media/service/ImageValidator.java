package org.example.rebuiltzijingforum.f12_media.service;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/** 只信任服务端验证过的图片字节和 MIME，不使用客户端文件名生成路径。 */
@Component
public class ImageValidator {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
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
                    : ImageIO.read(new ByteArrayInputStream(bytes)) != null;
            if (!realImage) {
                throw error("FILE_TYPE_ERROR", "上传文件不是真实图片");
            }
            return new ValidatedImage(bytes, contentType, extension);
        } catch (IOException failure) {
            throw error("FILE_READ_ERROR", "图片读取失败");
        }
    }

    private boolean hasWebpSignature(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
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
