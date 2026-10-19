package org.example.rebuiltzijingforum.f12_media.model;

/** objectKey 用于补偿删除，url 用于数据库和前端展示。 */
public record StoredImage(String objectKey, String url) {
}
