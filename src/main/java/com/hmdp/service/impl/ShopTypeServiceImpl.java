package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> getTypeList() {
        String key = "cache:shop-type";
        List<ShopType> shopTypes = null;
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        if (shopTypeJson == null) {
            shopTypes = query().orderByAsc("sort").list();
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypes));
        } else {
            shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
        }
        return shopTypes;
    }
}
