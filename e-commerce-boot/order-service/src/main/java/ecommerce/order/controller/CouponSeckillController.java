package ecommerce.order.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.order.dto.SeckillPayDTO;
import ecommerce.order.dto.SeckillRefundApplyDTO;
import ecommerce.order.service.CouponSeckillService;
import ecommerce.order.vo.SeckillCouponVO;
import ecommerce.order.vo.SeckillOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端优惠券秒杀接口。
 */
@Tag(name = "优惠券秒杀", description = "秒杀券列表 / 下单 / 支付 / 取消 / 退款申请")
@RestController
@RequestMapping("/api/coupons/seckill")
public class CouponSeckillController {

    @Resource
    private CouponSeckillService couponSeckillService;

    @Operation(summary = "秒杀券列表（分页）")
    @GetMapping
    public Result<PageVO<SeckillCouponVO>> list(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(couponSeckillService.list(page, pageSize));
    }

    @Operation(summary = "秒杀券详情")
    @GetMapping("/{couponId}")
    public Result<SeckillCouponVO> detail(@PathVariable Long couponId) {
        return Result.success(couponSeckillService.detail(couponId));
    }

    @Operation(summary = "创建秒杀订单")
    @PostMapping("/{couponId}/orders")
    public Result<SeckillOrderVO> createOrder(@PathVariable Long couponId) {
        return Result.success(couponSeckillService.createOrder(couponId));
    }

    @Operation(summary = "查询自己的秒杀订单")
    @GetMapping("/orders/{orderNo}")
    public Result<SeckillOrderVO> getOrder(@PathVariable String orderNo) {
        return Result.success(couponSeckillService.getOrder(orderNo));
    }

    @Operation(summary = "我的秒杀订单列表（分页，status 可选）")
    @GetMapping("/orders")
    public Result<PageVO<SeckillOrderVO>> myOrders(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int pageSize,
                                                   @RequestParam(required = false) Integer status) {
        return Result.success(couponSeckillService.myOrders(page, pageSize, status));
    }

    @Operation(summary = "取消待支付订单")
    @PostMapping("/orders/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo) {
        couponSeckillService.cancel(orderNo);
        return Result.success();
    }

    @Operation(summary = "支付订单（模拟支付）")
    @PostMapping("/orders/{orderNo}/pay")
    public Result<SeckillOrderVO> pay(@PathVariable String orderNo,
                                      @RequestBody(required = false) SeckillPayDTO dto) {
        return Result.success(couponSeckillService.pay(orderNo, dto == null ? null : dto.getPayType()));
    }

    @Operation(summary = "申请退款")
    @PostMapping("/orders/{orderNo}/refund")
    public Result<Void> refund(@PathVariable String orderNo,
                               @RequestBody(required = false) SeckillRefundApplyDTO dto) {
        couponSeckillService.applyRefund(orderNo, dto);
        return Result.success();
    }
}
