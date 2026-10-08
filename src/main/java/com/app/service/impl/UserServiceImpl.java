package com.app.service.impl;

import com.app.utils.UserHolder;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.app.dto.LoginFormDTO;
import com.app.dto.Result;
import com.app.dto.UserDTO;
import com.app.entity.User;
import com.app.mapper.UserMapper;
import com.app.service.IUserService;
import com.app.utils.*;
import com.app.utils.RegexUtils;
import com.app.utils.SystemConstants;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.app.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {

        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        String code= RandomUtil.randomNumbers(6);

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        session.setAttribute("code",code);

        log.debug("发送验证码成功:{}",code);


        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code=loginForm.getCode();
        if(!code.equals(cacheCode)){
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone", phone).one();

        if(user==null)
        {
            user=createUserWithPhone(phone);
        }


        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        String token= UUID.randomUUID().toString();

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(key,map);

        stringRedisTemplate.expire(key,LOGIN_USER_TTL,TimeUnit.MINUTES);

        return Result.ok(token);
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();

        String time = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key= USER_SIGN_KEY+userId+time;

        int mouth =now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key,mouth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();

        String time = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key= USER_SIGN_KEY+userId+time;

        int mouth =now.getDayOfMonth();

        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(mouth)).valueAt(0));
        if(result==null||result.isEmpty()) {
            Result.ok();
        }
        Long num=result.get(0);
        if(num==null||num==0) {
            Result.ok();
        }
        int count=0;
        while (true){
            if((num&1)==0){
                break;
            }else {
                count++;
            }
            num>>>=1;
        }


        return Result.ok(count);
    }

    @Override
    public void logout() {
        UserHolder.removeUser();
    }

    private User createUserWithPhone(String phone) {

        User user=User.builder().phone(phone).nickName(SystemConstants.USER_NICK_NAME_PREFIX+RandomUtil.randomString(10)).build();
        save(user);
        return user;
    }
}
