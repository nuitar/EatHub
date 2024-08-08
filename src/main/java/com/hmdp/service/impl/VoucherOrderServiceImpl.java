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
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisWorker redisWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
//    @Transactional(rollbackFor = BaseException.class)
    public Long seckillVoucher(Long voucherId) throws BaseException {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        LocalDateTime now = LocalDateTime.now();
        if(seckillVoucher == null)
            throw new BaseException("优惠券不存在");
        if (seckillVoucher.getBeginTime().isAfter(now)) {
            throw new BaseException("秒杀未开始");
        }

        if (seckillVoucher.getEndTime().isBefore(now))
            throw new BaseException("秒杀已经结束");

        if (seckillVoucher.getStock() < 1)
            throw new BaseException("库存不足");


        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("voucher-order", stringRedisTemplate);
        try {

            if(!simpleRedisLock.tryLock(1200))
                throw new BaseException("禁止重复下单");
            IVoucherOrderService voucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
            Long orderId = voucherOrderService.doSeckillVoucher(seckillVoucher);
            return orderId;
        }finally {
            simpleRedisLock.unlock();
        }


    }

    @Transactional(rollbackFor = BaseException.class)
    @Override
    public Long doSeckillVoucher(SeckillVoucher seckillVoucher) throws BaseException {

        boolean status = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", seckillVoucher.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!status)
            throw new BaseException("库存不足 高并发");
        //一人一单
        int count = query()
                .eq("user_id", UserHolder.getUser().getId())
                .eq("voucher_id", seckillVoucher.getVoucherId())
                .count();
        if (count > 0)
            throw new BaseException("一人仅限一单");


        //创建订单
        long orderId = redisWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setCreateTime(LocalDateTime.now());
        voucherOrder.setVoucherId(seckillVoucher.getVoucherId());
        voucherOrder.setUserId(UserHolder.getUser().getId());

        save(voucherOrder);
        return orderId;
    }


}
