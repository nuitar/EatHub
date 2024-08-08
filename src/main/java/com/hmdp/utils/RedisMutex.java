package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class RedisMutex {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final String MUTEX_VALUE = "1";
    public RedisMutex(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Boolean  tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, MUTEX_VALUE, 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    public void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
