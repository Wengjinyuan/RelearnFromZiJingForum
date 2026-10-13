package org.example.rebuiltzijingforum.f02_profile.utils;

import lombok.Getter;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本文件要完成：
 * 校验头像大小和真实类型，并生成服务端文件名。
 */

public final class AvatarFileUtils {
    public AvatarFileUtils() {}
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final  Set<String> ALLOW_CONTENTS_TYPE = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp"
            );

    /**
     * 模板                                              案例
     * if (file为空) 抛参数异常                          empty.png → FILE_EMPTY
     * if (file.size > 最大字节数) 抛大小异常            3MB > 2MB → FILE_TOO_LARGE
     * MIME + 图片解码结果共同判断真实类型               text.txt 改名 demo.png → 拒绝
     */
    public static void validate(MultipartFile file) {
        // TODO 依次完成非空、大小、MIME 和图片解码校验。
        if(file.isEmpty() || file.getSize() <= 0){
            throw new BusinessException("AVATAR_NOT_FOUND","上传文件不能为空", HttpStatus.NOT_FOUND);
        }
        if(file.getSize() > MAX_FILE_SIZE){
            throw new BusinessException("FILE_TO_BIG","头像文件不能超过20MB",HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOW_CONTENTS_TYPE.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("FILE_TYPE_ERROR", "只支持 JPG、PNG、GIF、WEBP 图片", HttpStatus.BAD_REQUEST);
        }
        try
        {
            byte[] signature = file.getInputStream().readNBytes(12);
            boolean webp = signature.length >= 12 && signature[0]=='R' && signature[1]=='I'
                    && signature[2]=='F' && signature[3]=='F' && signature[8]=='W'
                    && signature[9]=='E' && signature[10]=='B' && signature[11]=='P';
            if(("image/webp".equalsIgnoreCase(contentType) && !webp)
                    || (!"image/webp".equalsIgnoreCase(contentType) && ImageIO.read(file.getInputStream())==null))
            {
                throw new BusinessException("INVALID_IMAGE","上传的不是真实图片",HttpStatus.BAD_REQUEST);
            }
        } catch (IOException e) {
            throw new RuntimeException("图片读取失败");
        }
    }

    /**
     * 模板                                              案例
     * String ext = 根据真实类型选择受控扩展名           image/png → ".png"
     * return 用户目录 + 随机唯一值 + ext                return "1001/" + UUID.randomUUID() + ".png"
     * 不把客户端原文件名拼入保存路径                    "../../a.png" 不参与路径生成
     */
    public static String generateFileName(long userId, String detectedContentType) {
        // TODO 根据已验证的真实类型选择扩展名，并使用 userId 与 UUID 生成文件名。
        if(detectedContentType==null)
        {
            throw new RuntimeException("无法识别图片类型");
        }
        String extension = switch (detectedContentType.toLowerCase(Locale.ROOT))
        {
            case "image/jpeg" -> "jpeg";
            case "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> throw new BusinessException("FILE_TYPE_ERROR","不支持类型",HttpStatus.BAD_REQUEST);
        };
        return userId + "/" + UUID.randomUUID() + "." + extension;
        //throw new UnsupportedOperationException("TODO: 完成头像文件名生成");
    }

}
