package org.example.rebuiltzijingforum.f12_media.service;

import org.example.rebuiltzijingforum.f12_media.model.ImageKind;
import org.example.rebuiltzijingforum.f12_media.model.StoredImage;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public interface ImageStorage {
    StoredImage store(MultipartFile file, ImageKind kind, long ownerId);

    void delete(String objectKey);

    Optional<String> objectKeyFromUrl(String url);
}
