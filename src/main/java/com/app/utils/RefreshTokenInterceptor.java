package com.app.utils;
import com.app.dto.UserDTO;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.app.utils.RedisConstants.LOGIN_USER_TTL;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor{
    StringRedisTemplate stringRedisTemplate;
    public RefreshTokenInterceptor(StringRedisTemplate template){
        stringRedisTemplate=template;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");

        if (StrUtil.isBlank(token)) {
            return true;
        }

        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);

        if(map==null)
        {
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(),false);
        UserHolder.saveUser((UserDTO) userDTO);
        stringRedisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {

        UserHolder.removeUser();
    }
}
