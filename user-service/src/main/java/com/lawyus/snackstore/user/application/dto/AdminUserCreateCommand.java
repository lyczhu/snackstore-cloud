package com.lawyus.snackstore.user.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理员建号命令（内测期自助注册关闭后的唯一开号入口）。
 * 与自助注册的差别：不校验短信验证码，角色可由管理员指定，缺省 USER。
 */
@Getter
@Setter
public class AdminUserCreateCommand {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度须为6-64位")
    private String password;

    @Size(max = 20, message = "昵称长度不能超过20位")
    private String nickname;

    /** 可选，缺省 USER；取值 USER / ADMIN（大小写不敏感） */
    @Pattern(regexp = "(?i)(USER|ADMIN)", message = "角色只能是 USER 或 ADMIN")
    private String role;
}
