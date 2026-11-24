package ecommerce.auth.controller;

import ecommerce.auth.dto.AdminPasswordUpdateDTO;
import ecommerce.auth.dto.AdminProfileUpdateDTO;
import ecommerce.auth.entity.AdminLoginLog;
import ecommerce.auth.service.AdminProfileService;
import ecommerce.auth.service.AuthService;
import ecommerce.auth.vo.AdminUserVO;
import ecommerce.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员个人中心（自助）。
 * <p>
 * 所有接口都从 UserContext 取当前登录人，不接受路径参数或 body 里的 id，杜绝越权。
 * <p>
 * 不需要 @RequirePerm：只要登录了就有权改自己的资料和密码，这是登录身份的固有能力。
 */
@Tag(name = "个人中心", description = "管理员自助：改资料、改密码、查自己的登录日志")
@RequestMapping("/api/admin/profile")
@RestController
public class AdminProfileController {

    private final AdminProfileService profileService;
    private final AuthService authService;

    public AdminProfileController(AdminProfileService profileService, AuthService authService) {
        this.profileService = profileService;
        this.authService = authService;
    }

    @Operation(summary = "获取自己的资料")
    @GetMapping
    public Result<AdminUserVO> me() {
        return Result.success(authService.getInfo(ecommerce.utils.UserContext.getUserId()));
    }

    @Operation(summary = "修改自己的资料")
    @PutMapping
    public Result<Void> updateProfile(@Valid @RequestBody AdminProfileUpdateDTO dto) {
        profileService.updateProfile(dto);
        return Result.success();
    }

    @Operation(summary = "修改自己的密码（需旧密码）")
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody AdminPasswordUpdateDTO dto) {
        profileService.updatePassword(dto);
        return Result.success();
    }

    @Operation(summary = "查自己最近 N 条登录日志")
    @GetMapping("/logins")
    public Result<List<AdminLoginLog>> listMyLogins(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(profileService.listMyLogins(limit));
    }
}
