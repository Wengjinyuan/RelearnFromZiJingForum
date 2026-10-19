package org.example.rebuiltzijingforum.f12_media.service;

import com.aliyun.oss.OSS;

/** 每次云端操作创建一个短生命周期 OSS 客户端，调用方负责 shutdown。 */
@FunctionalInterface
public interface OssClientFactory {
    OSS create();
}
