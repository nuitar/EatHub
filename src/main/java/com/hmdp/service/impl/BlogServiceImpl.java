package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.BaseException;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import io.netty.channel.AbstractEventLoop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final String REDIS_LIKE_USER_SET_KEY = "blog:liked";

    @Autowired
    private IFollowService followService;

    @Override
    public List<Blog> queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            setBlogIsLiked(blog);

        });
        return records;
    }

    @Override
    public Blog queryBlogById(Long id) throws BaseException {
        Blog blog = getById(id);
        if (blog == null)
            throw new BaseException("博客不存在");
        queryBlogUser(blog);
        setBlogIsLiked(blog);
        return blog;
    }

    @Override
    public void likeBlog(Long id) {
        // 修改点赞数量
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score != null) {
            // 取消点赞
            boolean success = update()
                    .setSql("liked = liked - 1").eq("id", id).update();
            if (success)
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        } else {
            boolean success = update()
                    .setSql("liked = liked + 1").eq("id", id).update();
            if (success)
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        }
    }

    @Override
    public List<UserDTO> queryBlogLikes(Long id) {
        Set<String> ids = stringRedisTemplate.opsForZSet().range(REDIS_LIKE_USER_SET_KEY + id, 0, 4);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        String idStr = StrUtil.join(",", ids);
        List<User> users = userService.query()
                .in("id", ids)
                .last("order by field (id," + idStr + ")").list();
        List<UserDTO> result = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return result;
    }

    @Override
    public Long saveBlog(Blog blog) throws BaseException {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean success = save(blog);
        if (!success)
            throw new BaseException("新增笔记失败");
        //查询作者的所有粉丝
        List<Follow> fans = followService.query().eq("follow_user_id", blog.getUserId()).list();

//        String key = "feed:" + blog.getUserId();
        //将所有粉丝id推送到feed流种
        for (Follow fan : fans) {
            Long userId = fan.getUserId();
            String key = "feed:" + userId;

            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString()
                    , System.currentTimeMillis());
        }

        // 保存探店博文
        // 返回id
        return blog.getId();
    }

    @Override
    public ScrollResult querBlogOfFollw(Long max, Integer offset) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        ;
        // 获取推荐流
        String key = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> blogIdWithScores = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 3L);
        if (blogIdWithScores == null || blogIdWithScores.isEmpty()) {
            return null;
        }

        List<Long> ids = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        double m = -1;
        int nextOffset = 0;

        for (ZSetOperations.TypedTuple<String> blogIdWithScore : blogIdWithScores) {
            String blogId = blogIdWithScore.getValue();
            ids.add(Long.valueOf(blogId));
            Double score = blogIdWithScore.getScore();
            scores.add(score);

            if (m == score) {
                nextOffset++;
            } else {
                m = score;
                nextOffset = 1;
            }
        }
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query()
                .in("id", ids)
                .last("order by field (id," + idStr + ")").list();

        blogs.forEach(blog -> {
            queryBlogUser(blog);
            setBlogIsLiked(blog);
        });
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(nextOffset);
        scrollResult.setMinTime((long) m);
        return scrollResult;
    }

    private void setBlogIsLiked(Blog blog) {
        if (UserHolder.getUser() == null)
            return;
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
