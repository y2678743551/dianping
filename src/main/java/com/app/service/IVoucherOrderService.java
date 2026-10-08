package com.app.service;

import com.app.dto.Result;
import com.app.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result secondKillVocher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherId);
}
