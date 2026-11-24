package ecommerce.order.controller;

import ecommerce.common.PageVO;
import ecommerce.common.Result;
import ecommerce.common.annotation.RequirePerm;
import ecommerce.order.dto.AdminCouponRefundReviewDTO;
import ecommerce.order.service.AdminCouponRefundService;
import ecommerce.order.vo.AdminCouponRefundVO;
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
 * 后台秒杀券退款审核接口。
 */
@Tag(name = "后台秒杀券退款", description = "退款单列表、审核通过、拒绝")
@RestController
@RequirePerm("business:coupon:refund")
@RequestMapping("/api/admin/coupon-seckill-refunds")
public class AdminCouponRefundController {

    @Resource
    private AdminCouponRefundService adminCouponRefundService;

    @Operation(summary = "退款单列表")
    @GetMapping
    public Result<PageVO<AdminCouponRefundVO>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int pageSize,
                                                    @RequestParam(required = false) String status) {
        return Result.success(adminCouponRefundService.list(page, pageSize, status));
    }

    @Operation(summary = "审核通过并退款")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        adminCouponRefundService.approve(id);
        return Result.success();
    }

    @Operation(summary = "拒绝退款")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody(required = false) AdminCouponRefundReviewDTO dto) {
        adminCouponRefundService.reject(id, dto == null ? null : dto.getMerchantRemark());
        return Result.success();
    }
}
