package ecommerce.auth.service;

import ecommerce.auth.dto.AdminPasswordUpdateDTO;
import ecommerce.auth.dto.AdminProfileUpdateDTO;
import ecommerce.auth.entity.AdminLoginLog;

import java.util.List;

/**
 * 管理员个人中心自助服务。
 * <p>
 * 和 system-service 的 AdminAccountService（管理别人）不同，
 * 这里的接口只针对"当前登录人"自己的资料和密码。
 */
public interface AdminProfileService {

    /** 修改自己的 name / phone / adminAvatar */
    void updateProfile(AdminProfileUpdateDTO dto);

    /** 修改自己的密码（需旧密码校验，完成后强制重新登录） */
    void updatePassword(AdminPasswordUpdateDTO dto);

    /** 查自己最近 N 条登录日志 */
    List<AdminLoginLog> listMyLogins(int limit);
}
