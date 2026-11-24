package ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员修改自己密码 DTO。
 */
@Data
public class AdminPasswordUpdateDTO {

    /** 旧密码，必填，用于校验身份 */
    @NotBlank(message = "请输入旧密码")
    private String oldPassword;

    /** 新密码，6~64 位 */
    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 64, message = "新密码长度需在 6~64 位之间")
    private String newPassword;

    /** 二次确认 */
    @NotBlank(message = "请再次输入新密码")
    private String confirmPassword;
}
