package com.app.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.app.dto.LoginFormDTO;
import com.app.dto.Result;
import com.app.entity.User;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

    void logout();
}
