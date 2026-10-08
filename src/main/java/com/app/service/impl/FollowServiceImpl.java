package com.app.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.app.dto.Result;
import com.app.dto.UserDTO;
import com.app.entity.Follow;
import com.app.mapper.FollowMapper;
import com.app.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.app.service.IUserService;
import com.app.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long id = UserHolder.getUser().getId();
        String key= "follows:"+id;
        if(isFollow){
            Follow follow=new Follow();
            follow.setUserId(id);
            follow.setFollowUserId(followUserId);
            boolean success = save(follow);
            if(success){

                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }

        }else {
            remove(new QueryWrapper<Follow>().eq("user_id",id).eq("follow_user_id",followUserId));
            stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long id = UserHolder.getUser().getId();
        Long count = query().eq("user_id", id).eq("follow_user_id", followUserId).count();;

        return Result.ok(count>0);
    }

    @Override
    public Result followCommons(Long followUserId) {
        Long id = UserHolder.getUser().getId();
        String key1="follows:"+id;
        String key2="follows:"+followUserId;
        Set<String> set = stringRedisTemplate.opsForSet().intersect(key1, key2);
        List<Long> collect = set.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> list = userService.listByIds(collect).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(list);

    }
}
