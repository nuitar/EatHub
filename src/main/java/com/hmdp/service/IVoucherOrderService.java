package com.hmdp.service;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.utils.BaseException;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Long seckillVoucher(Long voucherId) throws BaseException;

    void createVoucherOrder(VoucherOrder voucherOrder) throws BaseException;

}
