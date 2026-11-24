package ecommerce.auth.mapper;

import ecommerce.auth.entity.Admin;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface AdminMapper {
    // 按用户名查管理员（登录用；已软删的账号查不到）
    Admin findByUsername(@Param("username") String username);

    // 按 id 查管理员（查当前登录用户用；已软删的账号查不到）
    Admin findById(@Param("id") Long id);

    // 修改密码
    int updatePassword(@Param("id") Long id, @Param("password") String password,
                       @Param("now") LocalDateTime now);

    /** 修改个人资料：name / phone / adminAvatar，null 字段保持原值 */
    int updateProfile(@Param("id") Long id,
                      @Param("name") String name,
                      @Param("phone") String phone,
                      @Param("adminAvatar") String adminAvatar,
                      @Param("now") LocalDateTime now);
}
