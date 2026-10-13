package org.example.rebuiltzijingforum.f02_profile.service;

import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f02_profile.dto.AvatarUploadDTO;
import org.example.rebuiltzijingforum.f02_profile.dto.UpdateProfileDTO;
import org.example.rebuiltzijingforum.f02_profile.vo.ProfileVO;

/**
 * 本文件要完成：
 * 定义资料查询、本人修改和头像上传能力。
 */
public interface ProfileService {

    /**
     * 模板                                              案例
     * 返回VO 查询方法(资源id)                           BookVO detail(long bookId)
     * 查询结果只包含允许公开的字段                      BookVO 不包含内部审核备注
     */
    ProfileVO getProfile(long userId);

    /**
     * 模板                                              案例
     * 返回VO 修改本人方法(可信用户, 修改DTO dto)        BookVO updateOwn(CurrentUserDTO user, BookDTO dto)
     * 身份取自服务端鉴权结果，不接收客户端用户id         user.userId() = 1001
     */
    ProfileVO updateOwnProfile(CurrentUserDTO currentUserDTO, UpdateProfileDTO updateProfileDTO);

    /**
     * 模板                                              案例
     * String 上传方法(可信用户, 文件DTO dto)            String upload(CurrentUserDTO user, FileDTO dto)
     * 返回服务端生成的受控访问地址                      return "/files/demo.png"
     */
    String uploadOwnAvatar(CurrentUserDTO currentUserDTO, AvatarUploadDTO avatarUploadDTO);
}
