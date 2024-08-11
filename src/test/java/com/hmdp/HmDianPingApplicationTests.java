package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource
    private RedisWorker redisWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IShopService shopService;

    @Autowired
    private RedissonClient redissonClient;

    @Test
    public void testRedisWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisWorker.nextId("order");
                System.out.println(id);
            }
            countDownLatch.countDown();

        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("time  = " + (begin - end));
    }

    @Test
    public void testRedisson() {
//        redissonClient.getLock("")
    }

    @Test
    public void loadData() {
        // 查询店铺
        List<Shop> list = shopService.list();
        // 店铺分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //存入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for (Shop shop : shops) {
                locations.add(new RedisGeoCommands.GeoLocation<String>(shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));
            }
            String key = "shop:geo:" + typeId;
            stringRedisTemplate.opsForGeo().add(key, locations);
        }

    }

    @Test
    public void  testHyperLog(){
        String[] userLogs = new String[1000];
        for (int i = 0; i < 1000000; i++) {
            userLogs[i % 1000] = "user_" + i;
            if (i % 1000 == 999)
                stringRedisTemplate.opsForHyperLogLog().add("testHyperLog",userLogs);
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("testHyperLog");
        System.out.println(count);
    }
}
