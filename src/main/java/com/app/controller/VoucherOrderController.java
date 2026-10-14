package com.app.controller;


import com.app.annotation.RateLimit;
import com.app.dto.Result;
import com.app.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;


    @RateLimit(prefix = "seckill:user", count = 10, window = 1, dimension = RateLimit.Dimension.USER)
    @RateLimit(prefix = "seckill:voucher", count = 200, window = 1, dimension = RateLimit.Dimension.PARAM, paramIndex = 0)
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {

        return voucherOrderService.secondKillVocher(voucherId);
    }
}
