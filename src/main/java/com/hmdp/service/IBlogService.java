package com.hmdp.service;

import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.User;
import com.hmdp.utils.BaseException;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    List<Blog> queryHotBlog(Integer current);

    Blog queryBlogById(Long id) throws BaseException;

    void likeBlog(Long id);

    List<UserDTO> queryBlogLikes(Long id);

    Long saveBlog(Blog blog) throws BaseException;

    ScrollResult querBlogOfFollw(Long max, Integer offset);
}
