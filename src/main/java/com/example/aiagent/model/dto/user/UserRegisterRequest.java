package com.example.aiagent.model.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;

/**
 * 用户注册请求体
 */
@Data
@Schema(description = "用户注册请求体")
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = -3963916550858845010L;

    /**
     * 账号
     */
    @NotBlank(message = "用户账号不能为空")
    @Size(min = 4, message = "用户账号长度不能小于4")
    @Schema(description = "用户账号")
    private String userAccount;

    /**
     * 密码
     */
    @NotBlank(message = "用户密码不能为空")
    @Size(min = 6, max = 20, message = "用户密码长度需在6-20之间")
    @Schema(description = "用户密码")
    private String userPassword;

    /**
     * 确认密码
     */
    @NotBlank(message = "确认密码不能为空")
    @Size(min = 6, max = 20, message = "确认密码长度需在6-20之间")
    @Schema(description = "确认密码")
    private String checkPassword;
}
