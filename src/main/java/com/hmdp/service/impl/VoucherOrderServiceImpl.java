package com.hmdp.service.impl;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.BaseException;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisWorker redisWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private IVoucherOrderService proxy;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    private final static ExecutorService SECKILL_ORDER_EXECUTOR =  Executors.newSingleThreadExecutor();
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            while (true){
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    proxy.createVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("订单异常",e);
                }

            }
        }
    }
    @PostConstruct
    public void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    @Override
//    @Transactional(rollbackFor = BaseException.class)
    public Long seckillVoucher(Long voucherId) throws BaseException {
        Long userId = UserHolder.getUser().getId();
        Long orderStatus = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        int orderStatusInt = orderStatus.intValue();
        if(orderStatusInt != 0){
            throw new BaseException(orderStatusInt == 1 ? "库存不足" : "禁止重复下单");
        }

        Long orderId =redisWorker.nextId("order");

        // TODO 增加新订单到消息队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setCreateTime(LocalDateTime.now());
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        orderTasks.add(voucherOrder);

        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return orderId;
    }
    
//
//    @Override
////    @Transactional(rollbackFor = BaseException.class)
//    public Long seckillVoucher(Long voucherId) throws BaseException {
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        LocalDateTime now = LocalDateTime.now();
//        if(seckillVoucher == null)
//            throw new BaseException("优惠券不存在");
//        if (seckillVoucher.getBeginTime().isAfter(now)) {
//            throw new BaseException("秒杀未开始");
//        }
//
//        if (seckillVoucher.getEndTime().isBefore(now))
//            throw new BaseException("秒杀已经结束");
//
//        if (seckillVoucher.getStock() < 1)
//            throw new BaseException("库存不足");
//
//
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("voucher-order", stringRedisTemplate);
//        RLock lock = redissonClient.getLock("voucher-order");
//        try {
//
//            if(!lock.tryLock())
//                throw new BaseException("禁止重复下单");
//            IVoucherOrderService voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
//            Long orderId = voucherOrderService.doSeckillVoucher(seckillVoucher);
//            return orderId;
//        }finally {
//            lock.unlock();
//        }
//    }

    @Transactional(rollbackFor = BaseException.class)
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) throws BaseException {

        boolean status = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!status)
            throw new BaseException("库存不足 高并发");
        //一人一单
        int count = query()
                .eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        if (count > 0)
            throw new BaseException("一人仅限一单");

        save(voucherOrder);
    }


}
