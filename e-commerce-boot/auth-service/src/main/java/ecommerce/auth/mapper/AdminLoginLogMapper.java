package ecommerce.auth.mapper;

import ecommerce.auth.entity.AdminLoginLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 登录日志 Mapper（auth-service 自己的只读查询，用于个人中心"我的登录日志"面板）。
 * <p>
 * system-service 的 AdminLoginLogMapper 负责写入和管理端查询，
 * 这里只加一个 listByAdminId 的只读方法。
 */
public interface AdminLoginLogMapper {

    void insert(AdminLoginLog log);

    /** 个人中心查自己最近 N 条登录日志 */
    List<AdminLoginLog> listByAdminId(@Param("adminId") Long adminId,
                                     @Param("limit") int limit);
}
