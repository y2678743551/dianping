package com.app.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.app.dto.Result;
import com.app.dto.ScrollResult;
import com.app.dto.UserDTO;
import com.app.entity.Blog;
import com.app.entity.Follow;
import com.app.entity.User;
import com.app.mapper.BlogMapper;
import com.app.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.app.service.IFollowService;
import com.app.service.IShopService;
import com.app.service.IUserService;
import com.app.utils.RedisConstants;
import com.app.utils.SystemConstants;
import com.app.utils.UserHolder;
import jakarta.annotation.Resource;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopService shopService;
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryBlogUser);
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {


        Long userId= UserHolder.getUser().getId();
        String key= RedisConstants.BLOG_LIKED_KEY+id;
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());
        if(score==null){
            boolean isSuccess=update().setSql("liked=liked+1").eq("id",id).update();
            if (isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else {
            boolean isSuccess=update().setSql("liked=liked-1").eq("id",id).update();
            if (isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {

        String key= RedisConstants.BLOG_LIKED_KEY+id;
        Set<String> set = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(set==null|| set.isEmpty()) {
            return Result.ok(" 无人点赞");
        }
        List<Long>ids=set.stream().map(Long::valueOf).collect(Collectors.toList());
        String join = StrUtil.join(",", ids);

        List<UserDTO> list = userService.query().in("id",ids).last("ORDER BY FIELD(id,"+join+")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(list);
    }

    @Override
    public Result saveBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean success = save(blog);
        if(success){
            return Result.fail("新增笔记失败");
        }
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow:follows){
            Long userId=follow.getUserId();
            String key=RedisConstants.FEED_KEY+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId= UserHolder.getUser().getId();
        String key=RedisConstants.FEED_KEY+userId;
        Set<ZSetOperations.@NonNull TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if(typedTuples==null||typedTuples.isEmpty())
            return Result.ok();
        List<Long> ids=new ArrayList<>(typedTuples.size());
        long minTime=0;
        int os=1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String idStr=typedTuple.getValue();
            ids.add(Long.valueOf(idStr));
            long time=typedTuple.getScore().longValue();
            if(minTime==time){
                os++;
            }else {
                os=1;
                minTime=time;
            }
        }
        String join = StrUtil.join(",", ids);

        List<Blog> list =query().in("id",ids).last("ORDER BY FIELD(id,"+join+")").list();
        for (Blog blog : list) {
            queryBlogUser(blog);

        }
        ScrollResult result=new ScrollResult();
        result.setList(list);
        result.setMinTime(minTime);
        result.setOffset(os);

        return Result.ok(result);
    }

    private void isLiked(Blog blog){
        Long userId= UserHolder.getUser().getId();
        String key= RedisConstants.BLOG_LIKED_KEY+blog.getId();
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());

        blog.setIsLike(score!=null);
    }
    private void queryBlogUser(Blog blog){
        UserDTO userDTO= UserHolder.getUser();
        if(userDTO==null)
        {
            return ;
        }
        Long userId=userDTO.getId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        isLiked(blog);
    }
}
