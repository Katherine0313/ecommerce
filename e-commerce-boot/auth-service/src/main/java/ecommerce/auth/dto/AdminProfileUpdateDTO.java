package ecommerce.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员个人资料更新 DTO（自助）。
 * <p>
 * 只能改 name / phone / adminAvatar，不接受 id、username、role 等字段。
 */
@Data
public class AdminProfileUpdateDTO {

    /** 姓名，可选；null 时保持原值 */
    @Size(max = 32, message = "姓名最长 32 个字符")
    private String name;

    /** 手机号，可选；null 时保持原值 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 头像 URL，可选；null 时保持原值 */
    @Size(max = 512, message = "头像地址过长")
    private String adminAvatar;
}
