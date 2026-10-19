package org.example.rebuiltzijingforum.f02_profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.javassist.NotFoundException;
import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.entity.UserAccountEntity;
import org.example.rebuiltzijingforum.f01_account.mapper.UserAccountMapper;
import org.example.rebuiltzijingforum.f02_profile.dto.AvatarUploadDTO;
import org.example.rebuiltzijingforum.f02_profile.dto.UpdateProfileDTO;
import org.example.rebuiltzijingforum.f02_profile.entity.UserProfileEntity;
import org.example.rebuiltzijingforum.f02_profile.mapper.UserProfileMapper;
import org.example.rebuiltzijingforum.f02_profile.service.ProfileService;
import org.example.rebuiltzijingforum.f02_profile.utils.AvatarFileUtils;
import org.example.rebuiltzijingforum.f02_profile.vo.ProfileVO;
import org.example.rebuiltzijingforum.f08_search.service.SearchSyncService;
import org.example.rebuiltzijingforum.f12_media.model.ImageKind;
import org.example.rebuiltzijingforum.f12_media.model.StoredImage;
import org.example.rebuiltzijingforum.f12_media.service.ImageStorage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;


/**
 * 本文件要完成：
 * 实现资料查询、所有权校验、资料事务和头像文件保存。
 */
@Slf4j
@Service
public class ProfileServiceImpl implements ProfileService {

    private final UserProfileMapper userProfileMapper;
    private final UserAccountMapper userAccountMapper;
    private final ObjectProvider<SearchSyncService> searchSync;
    private final ImageStorage imageStorage;

    public ProfileServiceImpl(UserProfileMapper userProfileMapper, UserAccountMapper userAccountMapper,
                              ObjectProvider<SearchSyncService> searchSync, ImageStorage imageStorage) {
        this.userProfileMapper = userProfileMapper;
        this.userAccountMapper = userAccountMapper;
        this.searchSync = searchSync;
        this.imageStorage = imageStorage;
    }

    /**
     * 模板                                              案例
     * Entity entity = mapper.selectById(id)             Book book = bookMapper.selectById(1001L)
     * entity == null → 抛未找到异常                      null → BOOK_NOT_FOUND
     * 只复制公开字段到 VO                               new BookVO(book.getId(), book.getTitle())
     */
    @Override
    @Transactional(readOnly = true)
    public ProfileVO getProfile(long userId) {
        // TODO 1. 按 userId 查询  2. 处理资料不存在  3. 只映射公开字段
        UserProfileEntity entity = userProfileMapper.selectById(userId);
        if(entity == null){
            entity = createDefaultProfile(userId);
        }
        return new  ProfileVO(entity.getUserId(),entity.getDisplayName(), entity.getAvatarUrl(), entity.getGender(), entity.getSignature(), entity.getMajor(), entity.getBirthday(), entity.getUpdatedAt());
//        throw new UnsupportedOperationException("TODO: 完成公开资料查询");
    }

    private UserProfileEntity createDefaultProfile(long userId) {
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        userProfileMapper.insert(entity);
        return entity;
    }

    /**
     * 模板                                              案例
     * long ownerId = 可信用户.userId()                  long ownerId = currentUser.userId()
     * entity.set允许字段(dto.字段())                    book.setTitle(dto.title())
     * mapper.updateById(entity)                         bookMapper.updateById(book)
     * affected != 1 → 抛业务异常                        0 → BOOK_UPDATE_FAILED
     */
    @Override
    @Transactional
    public ProfileVO updateOwnProfile(CurrentUserDTO currentUserDTO, UpdateProfileDTO updateProfileDTO) {
        // TODO 1. 用可信 userId 查询  2. 只复制 DTO 白名单字段  3. 更新并检查影响行数  4. 返回 VO
        UserProfileEntity entity = userProfileMapper.selectById(currentUserDTO.userId());
        if(entity == null){
            entity = createDefaultProfile(currentUserDTO.userId());
        }
        entity.setDisplayName(updateProfileDTO.displayName());
        entity.setGender(updateProfileDTO.gender());
        entity.setSignature(updateProfileDTO.signature());
        entity.setMajor(updateProfileDTO.major());
        entity.setBirthday(updateProfileDTO.birthday());
        entity.setUpdatedAt(LocalDateTime.now());
        // throw new UnsupportedOperationException("TODO: 完成本人资料修改");
        int updated = userProfileMapper.updateById(entity);
        if(updated != 1){
            throw new BusinessException("PROFILE_UPDATE_FAIL","用户资料更新失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        SearchSyncService sync = searchSync.getIfAvailable();
        if (sync != null) sync.userChanged(currentUserDTO.userId());
        return new ProfileVO(entity.getUserId(), entity.getDisplayName(),  entity.getAvatarUrl(), entity.getGender(), entity.getSignature(), entity.getMajor(), entity.getBirthday(), entity.getUpdatedAt());
    }

    /**
     * 模板                                              案例
     * 文件工具.校验(dto.file())                         ImageUtils.validate(file)
     * String name = 文件工具.生成名(id, 原文件名)       String name = ImageUtils.name(1001L, "demo.png")
     * 先保存新文件 → 更新数据库 → 再处理旧文件           save(new) → update(url) → delete(old)
     * 中途失败时执行补偿                                DB 失败 → 删除刚保存的新文件
     */
    @Override
    @Transactional
    public String uploadOwnAvatar(CurrentUserDTO currentUserDTO, AvatarUploadDTO avatarUploadDTO) {
        long userId = currentUserDTO.userId();
        MultipartFile file = avatarUploadDTO.file();
        UserProfileEntity entity = userProfileMapper.selectById(userId);
        if (entity == null) entity = createDefaultProfile(userId);
        String oldUrl = entity.getAvatarUrl();
        StoredImage stored = imageStorage.store(file, ImageKind.AVATAR, userId);
        boolean synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizedTransaction) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) deleteManagedUrl(oldUrl);
                    else deleteKeyQuietly(stored.objectKey());
                }
            });
        }
        try {
            entity.setAvatarUrl(stored.url());
            entity.setUpdatedAt(LocalDateTime.now());
            if (userProfileMapper.updateById(entity) != 1) {
                throw new BusinessException("AVATAR_UPDATE_FAIL", "头像更新失败", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            if (!synchronizedTransaction) deleteManagedUrl(oldUrl);
            return stored.url();
        } catch (RuntimeException failure) {
            if (!synchronizedTransaction) deleteKeyQuietly(stored.objectKey());
            throw failure;
        }
    }

    private void deleteManagedUrl(String url) {
        imageStorage.objectKeyFromUrl(url).ifPresent(this::deleteKeyQuietly);
    }

    private void deleteKeyQuietly(String key) {
        try {
            imageStorage.delete(key);
        } catch (RuntimeException cleanupFailure) {
            log.warn("图片补偿清理失败, objectKey={}", key, cleanupFailure);
        }
    }

//    private static String getExtension(AvatarUploadDTO avatarUploadDTO, MultipartFile file) {
//        if(file.isEmpty() || file.getSize() <= 0){
//            throw new BusinessException("AVATAR_NOT_FOUND","上传文件不能为空", HttpStatus.NOT_FOUND);
//        }
//        if(avatarUploadDTO.file().getSize() > 20 * 1024 * 1024){
//            throw new BusinessException("FILE_TO_BIG","头像文件不能超过20MB",HttpStatus.BAD_REQUEST);
//        }
//        String originalFileName = avatarUploadDTO.file().getOriginalFilename();
//        if (
//                originalFileName == null || !originalFileName.toLowerCase().endsWith(".*\\.(jpg|jpeg|png|gif|webp)$")
//        ) {
//            throw  new RuntimeException("只支持JPG,JPEG,PNG,GIF,WEBG格式的文件");
//        }
//        return originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase();
//    }
}
