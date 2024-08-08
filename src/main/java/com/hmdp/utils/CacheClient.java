package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisMutex redisMutex;
    private static final String MUTEX_PREFIX = "mutex-";

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }


    public <ID, T> T get(String keyPrefix, ID id, Class<T> type, Function<ID, T> getById, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String lockKey = MUTEX_PREFIX + key;
        String json = stringRedisTemplate.opsForValue().get(key);

        T result = null;
        if (StrUtil.isNotBlank(json)) {
            result = JSONUtil.toBean(json, type);
            return result;
        } else if ("".equals(json)) { // 空值
            return null;
        } else {
            try { // 获取互斥锁 防止缓存击穿
                if (!redisMutex.tryLock(lockKey)) {
                    return get(keyPrefix, id, type, getById, time, unit);
                }
                result = getById.apply(id);

                if (result == null) { // 缓存空值
                    stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                log.info("query by mysql");
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(result));
            } finally {
                redisMutex.unLock(lockKey);
            }
            return result;
        }

    }
}
