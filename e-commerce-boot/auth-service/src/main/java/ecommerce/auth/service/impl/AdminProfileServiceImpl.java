package ecommerce.auth.service.impl;

import ecommerce.auth.dto.AdminPasswordUpdateDTO;
import ecommerce.auth.dto.AdminProfileUpdateDTO;
import ecommerce.auth.entity.Admin;
import ecommerce.auth.entity.AdminLoginLog;
import ecommerce.auth.mapper.AdminLoginLogMapper;
import ecommerce.auth.mapper.AdminMapper;
import ecommerce.auth.service.AdminProfileService;
import ecommerce.auth.support.PasswordUtil;
import ecommerce.cache.CacheKeys;
import ecommerce.exception.BusinessException;
import ecommerce.log.LogDetail;
import ecommerce.log.OperationLog;
import ecommerce.security.AdminTokenRevocationStore;
import ecommerce.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 管理员个人中心自助服务实现。
 * <p>
 * 所有方法都从 {@link UserContext#getUserId()} 取当前登录人，不接受外部传入的 id 参数，
 * 杜绝越权修改别人资料。
 */
@Slf4j
@Service
public class AdminProfileServiceImpl implements AdminProfileService {

    /** 登录日志默认返回条数 */
    private static final int DEFAULT_LOGIN_LOG_LIMIT = 10;

    private final AdminMapper adminMapper;
    private final AdminLoginLogMapper loginLogMapper;
    private final AdminTokenRevocationStore tokenRevocationStore;

    public AdminProfileServiceImpl(AdminMapper adminMapper,
                                   AdminLoginLogMapper loginLogMapper,
                                   AdminTokenRevocationStore tokenRevocationStore) {
        this.adminMapper = adminMapper;
        this.loginLogMapper = loginLogMapper;
        this.tokenRevocationStore = tokenRevocationStore;
    }

    @Override
    @Transactional
    @OperationLog(module = "profile", action = "PROFILE_UPDATE")
    public void updateProfile(AdminProfileUpdateDTO dto) {
        Long adminId = currentAdminId();
        Admin existing = requireActiveAdmin(adminId);
        LogDetail.add("修改个人资料：原姓名【" + safe(existing.getName()) + "】");

        // 姓名空串视为不修改
        String name = trimToNull(dto.getName());
        String phone = trimToNull(dto.getPhone());
        String avatar = trimToNull(dto.getAdminAvatar());

        int rows = adminMapper.updateProfile(adminId, name, phone, avatar, LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "账号不存在或已停用");
        }
    }

    @Override
    @Transactional
    @OperationLog(module = "profile", action = "PROFILE_PASSWORD_CHANGE", recordParams = false)
    public void updatePassword(AdminPasswordUpdateDTO dto) {
        Long adminId = currentAdminId();
        Admin existing = requireActiveAdmin(adminId);
        LogDetail.add("修改个人密码");

        // 1. 二次确认一致
        if (!Objects.equals(dto.getNewPassword(), dto.getConfirmPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "两次输入的新密码不一致");
        }
        // 2. 旧密码校验
        if (!PasswordUtil.matches(dto.getOldPassword(), existing.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "旧密码错误");
        }
        // 3. 新密码不能和旧密码相同
        if (PasswordUtil.matches(dto.getNewPassword(), existing.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "新密码不能和旧密码相同");
        }
        // 4. 写入新密码
        adminMapper.updatePassword(adminId,
                PasswordUtil.encode(dto.getNewPassword()), LocalDateTime.now());
        // 5. 强制该账号所有端重新登录
        tokenRevocationStore.invalidate(adminId);
    }

    @Override
    public List<AdminLoginLog> listMyLogins(int limit) {
        Long adminId = currentAdminId();
        int safeLimit = Math.max(1, Math.min(50, limit));
        return loginLogMapper.listByAdminId(adminId, safeLimit);
    }

    // ==================== 内部工具 ====================

    private Long currentAdminId() {
        Long id = UserContext.getUserId();
        if (id == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return id;
    }

    private Admin requireActiveAdmin(Long id) {
        Admin admin = adminMapper.findById(id);
        if (admin == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "账号不存在或已停用");
        }
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已停用");
        }
        return admin;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
