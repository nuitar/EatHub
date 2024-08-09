package com.hmdp;

import com.hmdp.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource
    private RedisWorker redisWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Autowired
    private RedissonClient redissonClient;
    @Test
    public void testRedisWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable task = ()->{
            for (int i = 0; i < 100; i++) {
                long id = redisWorker.nextId("order");
                System.out.println(id);
            }
            countDownLatch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0;i < 300;i ++){
            es.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("time  = " + (begin - end));
    }

    @Test
    public void testRedisson(){
//        redissonClient.getLock("")
    }

}
