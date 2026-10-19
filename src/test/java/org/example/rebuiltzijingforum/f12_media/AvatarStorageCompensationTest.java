package org.example.rebuiltzijingforum.f12_media;

import org.example.rebuiltzijingforum.common.exception.BusinessException;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.mapper.UserAccountMapper;
import org.example.rebuiltzijingforum.f02_profile.dto.AvatarUploadDTO;
import org.example.rebuiltzijingforum.f02_profile.entity.UserProfileEntity;
import org.example.rebuiltzijingforum.f02_profile.mapper.UserProfileMapper;
import org.example.rebuiltzijingforum.f02_profile.service.impl.ProfileServiceImpl;
import org.example.rebuiltzijingforum.f08_search.service.SearchSyncService;
import org.example.rebuiltzijingforum.f12_media.model.ImageKind;
import org.example.rebuiltzijingforum.f12_media.model.StoredImage;
import org.example.rebuiltzijingforum.f12_media.service.ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvatarStorageCompensationTest {
    @Mock UserProfileMapper profiles;
    @Mock UserAccountMapper accounts;
    @Mock ObjectProvider<SearchSyncService> searchSync;
    @Mock ImageStorage storage;

    @Test
    void deletesNewObjectWhenDatabaseUpdateFails() {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(7L);
        profile.setAvatarUrl("/uploads/avatars/7/old.png");
        var file = new MockMultipartFile("file", "new.png", "image/png", new byte[]{1});
        when(profiles.selectById(7L)).thenReturn(profile);
        when(storage.store(file, ImageKind.AVATAR, 7L))
                .thenReturn(new StoredImage("avatars/7/new.png", "/uploads/avatars/7/new.png"));
        when(profiles.updateById(profile)).thenReturn(0);
        ProfileServiceImpl service = new ProfileServiceImpl(profiles, accounts, searchSync, storage);

        assertThatThrownBy(() -> service.uploadOwnAvatar(
                new CurrentUserDTO(7L, "alice", "USER"), new AvatarUploadDTO(file)))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo("AVATAR_UPDATE_FAIL"));

        verify(storage).delete("avatars/7/new.png");
        verify(storage, never()).delete("avatars/7/old.png");
    }
}
