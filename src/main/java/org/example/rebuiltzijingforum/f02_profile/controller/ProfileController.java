package org.example.rebuiltzijingforum.f02_profile.controller;

import jakarta.validation.Valid;
import org.example.rebuiltzijingforum.common.api.ApiResponse;
import org.example.rebuiltzijingforum.f01_account.dto.CurrentUserDTO;
import org.example.rebuiltzijingforum.f01_account.interceptor.AuthenticationInterceptor;
import org.example.rebuiltzijingforum.f02_profile.dto.AvatarUploadDTO;
import org.example.rebuiltzijingforum.f02_profile.dto.UpdateProfileDTO;
import org.example.rebuiltzijingforum.f02_profile.service.ProfileService;
import org.example.rebuiltzijingforum.f02_profile.vo.ProfileVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本文件要完成：
 * 接收资料查看、本人修改和头像上传请求并调用 Service。
 */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * 获取资料
     * 模板                                              案例
     * @GetMapping("/{路径变量}")                       @GetMapping("/{bookId}")
     * Result<VO> 方法名(@PathVariable 类型 id)          Result<BookVO> detail(@PathVariable long bookId)
     * return Result.success(service.查询(id))           return Result.success(bookService.detail(bookId))
     */
    @GetMapping("/{userId}")
    public ApiResponse<ProfileVO> getProfile(@PathVariable long userId) {
        return ApiResponse.success(profileService.getProfile(userId));
    }

    /**
     * 改资料
     * 模板                                              案例
     * @PutMapping("/me")                               @PutMapping("/me")
     * 方法名(@RequestAttribute 可信用户, @RequestBody DTO dto) update(CurrentUserDTO user, BookDTO dto)
     * service.修改本人(可信用户, dto)                    bookService.updateOwn(user, dto)
     */
    @PutMapping("/me")
    public ApiResponse<ProfileVO> updateOwnProfile(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO currentUserDTO,
            @Valid @RequestBody UpdateProfileDTO updateProfileDTO) {
        return ApiResponse.success(profileService.updateOwnProfile(currentUserDTO, updateProfileDTO));
    }

    /**
     * 传头像
     * 模板                                              案例
     * @PostMapping(value="路径", consumes=文件表单类型)    @PostMapping(value="/cover", consumes=MULTIPART_FORM_DATA_VALUE)
     * 方法名(@RequestAttribute 可信用户, @ModelAttribute 文件DTO dto)    upload(CurrentUserDTO user, FileDTO dto)
     * return Result.success(service.上传(可信用户, dto))    return Result.success(bookService.upload(user, dto))
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> uploadOwnAvatar(
            @RequestAttribute(AuthenticationInterceptor.CURRENT_USER) CurrentUserDTO currentUserDTO,
            @Valid @ModelAttribute AvatarUploadDTO avatarUploadDTO) {
        return ApiResponse.success(profileService.uploadOwnAvatar(currentUserDTO, avatarUploadDTO));
    }
}
