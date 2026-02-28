package com.example.aiagent.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户
 * @TableName user
 */
@TableName(value ="user")
@Data
@Schema(description = "用户")
public class User implements Serializable {
    /**
     * id
     */
    @TableId(value = "id")
    @Schema(description = "id")
    private Long id;

    /**
     * 账号
     */
    @TableField(value = "userAccount")
    @Schema(description = "账号")
    private String userAccount;

    /**
     * 密码
     */
    @TableField(value = "userPassword")
    @Schema(description = "密码")
    private String userPassword;

    /**
     * 用户昵称
     */
    @TableField(value = "userName")
    @Schema(description = "用户昵称")
    private String userName;

    /**
     * 用户头像
     */
    @TableField(value = "userAvatar")
    @Schema(description = "用户头像")
    private String userAvatar;

    /**
     * 用户简介
     */
    @TableField(value = "userProfile")
    @Schema(description = "用户简介")
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    @TableField(value = "userRole")
    @Schema(description = "用户角色：user/admin")
    private String userRole;

    /**
     * 编辑时间
     */
    @TableField(value = "editTime")
    @Schema(description = "编辑时间")
    private LocalDateTime editTime;

    /**
     * 创建时间
     */
    @TableField(value = "createTime")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "updateTime")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @TableField(value = "isDelete")
    @TableLogic
    @Schema(description = "是否删除")
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}