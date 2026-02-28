package com.example.aiagent.constant;

public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";
    
    // endregion

    /**
     * 最小密码长度
     */
    Integer MIN_PASSWORD_LENGTH = 6;

    /**
     * 最大密码长度
     */
    Integer MAX_PASSWORD_LENGTH = 20;

    /**
     * 最小账号长度
     */
    Integer MIN_ACCOUNT_LENGTH = 4;

    /**
     * 盐值，混淆密码
     */
    String SALT = "helloworld!@#2024$%^&*()_+";

    /**
     * 默认用户名
     */
    String DEFAULT_USER_NAME = "无名";

    /**
     * 默认密码
     */
    String DEFAULT_USER_PASSWORD = "12345678";
}
