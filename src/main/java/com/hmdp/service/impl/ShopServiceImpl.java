package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisMutex;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.Bidi;
import java.util.concurrent.TimeUnit;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    RedisMutex redisMutex;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    private final String CACHE_SHOP_KEY_PREFIX = "cache:shop:";

    @Override
    public Result queryById(Long id) throws InterruptedException {
        Shop shop = cacheClient.get(CACHE_SHOP_KEY_PREFIX,id,Shop.class,this::getById,10L,TimeUnit.SECONDS);
        return Result.ok(shop);
    }

    @Override
    public void updateShop(Shop shop) {
        stringRedisTemplate.delete(CACHE_SHOP_KEY_PREFIX + shop.getId());
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY_PREFIX + shop.getId());
    }
}
