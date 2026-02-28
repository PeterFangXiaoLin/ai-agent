package com.example.aiagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aiagent.common.DeleteRequest;
import com.example.aiagent.model.dto.user.*;
import com.example.aiagent.model.entity.User;
import com.example.aiagent.model.vo.LoginUserVO;
import com.example.aiagent.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author helloworld
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2025-09-21 20:00:42
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param userRegisterRequest 用户注册请求体
     * @return 新用户 id
     */
    Long userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 用户登录
     *
     * @param userLoginRequest 用户登录请求体
     * @param request request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 获取当前登录的用户
     * @param request request
     * @return 当前登录的用户信息
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取当前登录的用户（不抛出异常）
     * @param request request
     * @return 当前登录的用户信息，未登录则返回null
     */
    User getLoginUserPermitNull(HttpServletRequest request);

    /**
     * 用户注销（退出登录）
     * @param request request
     * @return 是否成功
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 添加用户
     * @param userAddRequest 添加用户请求体
     * @return 新用户 id
     */
    Long addUser(UserAddRequest userAddRequest);

    /**
     * 删除用户
     * @param deleteRequest 删除请求体
     * @return 是否成功
     */
    boolean deleteUser(DeleteRequest deleteRequest);

    /**
     * 修改用户
     * @param userUpdateRequest 修改用户请求体
     * @return 是否成功
     */
    boolean updateUser(UserUpdateRequest userUpdateRequest);

    /**
     * 分页获取用户列表（仅管理员）
     * @param userQueryRequest 查询条件
     * @return 用户列表
     */
    Page<UserVO> getUserVOByPage(UserQueryRequest userQueryRequest);

    /**
     * 获取脱敏后的登录用户信息
     * @param user 用户
     * @return 脱敏后的登录用户信息
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏后的用户信息
     * @param user 用户
     * @return 脱敏后的用户信息
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏后的用户信息列表
     * @param userList 用户列表
     * @return 脱敏后的用户信息列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取查询条件
     * @param userQueryRequest 查询条件
     * @return 查询条件
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 加密密码
     * @param password 明文密码
     * @return 加密后密码
     */
    String getEncryptedPassword(String password);

    /**
     * 是否为管理员
     * @param user 用户
     * @return 是否为管理员
     */
    boolean isAdmin(User user);
}