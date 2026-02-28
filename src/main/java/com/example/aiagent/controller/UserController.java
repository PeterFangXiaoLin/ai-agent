package com.example.aiagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aiagent.annotation.AuthCheck;
import com.example.aiagent.common.BaseResponse;
import com.example.aiagent.common.DeleteRequest;
import com.example.aiagent.common.ResultUtils;
import com.example.aiagent.constant.UserConstant;
import com.example.aiagent.exception.ErrorCode;
import com.example.aiagent.exception.ThrowUtils;
import com.example.aiagent.model.dto.user.*;
import com.example.aiagent.model.entity.User;
import com.example.aiagent.model.vo.LoginUserVO;
import com.example.aiagent.model.vo.UserVO;
import com.example.aiagent.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "用户相关接口")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户注册
     * @param userRegisterRequest 用户注册请求体
     * @return 新用户id
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "用户注册")
    public BaseResponse<Long> userRegister(@Valid @RequestBody UserRegisterRequest userRegisterRequest) {
        return ResultUtils.success(userService.userRegister(userRegisterRequest));
    }

    /**
     * 用户登录
     * @param userLoginRequest 用户登录请求体
     * @param request 请求
     * @return 登录用户信息
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录")
    public BaseResponse<LoginUserVO> userLogin(@Validated @RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        return ResultUtils.success(userService.userLogin(userLoginRequest, request));
    }

    /**
     * 获取当前登录的用户信息
     * @param request 请求
     * @return 登录用户信息
     */
    @GetMapping("/get/login")
    @Operation(summary = "获取当前登录的用户信息", description = "获取当前登录的用户信息")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    /**
     * 用户注销（退出登录）
     * @param request 请求
     * @return 是否注销成功
     */
    @PostMapping("/logout")
    @Operation(summary = "用户注销（退出登录）", description = "用户注销（退出登录）")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        return ResultUtils.success(userService.userLogout(request));
    }

    /**
     * 添加用户
     * @param userAddRequest 用户添加请求体
     * @return 新用户id
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "添加用户", description = "添加用户")
    public BaseResponse<Long> addUser(@Valid @RequestBody UserAddRequest userAddRequest) {
        return ResultUtils.success(userService.addUser(userAddRequest));
    }

    /**
     * 根据 id 获取用户（仅管理员）
     * @param id id
     * @return 用户
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "根据 id 获取用户（仅管理员）", description = "根据 id 获取用户（仅管理员）")
    public BaseResponse<User> getUserById(@Parameter(description = "用户id") Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取脱敏后的用户
     * @param id id
     * @return 脱敏后的用户
     */
    @GetMapping("/get/vo")
    @Operation(summary = "根据 id 获取脱敏后的用户", description = "根据 id 获取脱敏后的用户")
    public BaseResponse<UserVO> getUserVOById(@Parameter(description = "用户id") Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户
     * @param deleteRequest 删除请求体
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "删除用户", description = "删除用户")
    public BaseResponse<Boolean> deleteUser(@Valid @RequestBody DeleteRequest deleteRequest) {
        return ResultUtils.success(userService.deleteUser(deleteRequest));
    }

    /**
     * 更新用户
     * @param userUpdateRequest 用户更新请求体
     * @return 是否更新成功
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "更新用户", description = "更新用户")
    public BaseResponse<Boolean> updateUser(@Valid @RequestBody UserUpdateRequest userUpdateRequest) {
        return ResultUtils.success(userService.updateUser(userUpdateRequest));
    }

    /**
     * 分页获取用户列表（仅管理员）
     * @param userQueryRequest 用户查询请求体
     * @return 用户列表
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "分页获取用户列表（仅管理员）", description = "分页获取用户列表（仅管理员）")
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        return ResultUtils.success(userService.getUserVOByPage(userQueryRequest));
    }
}
