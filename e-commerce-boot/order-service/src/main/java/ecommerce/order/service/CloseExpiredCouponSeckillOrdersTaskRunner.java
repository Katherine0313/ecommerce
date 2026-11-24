package ecommerce.order.service;

import ecommerce.task.MonitoredTaskRunner;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 关闭超时未支付的秒杀券订单：手动执行入口，定时触发由 admin_monitor_task 动态调度。
 */
@Component
public class CloseExpiredCouponSeckillOrdersTaskRunner implements MonitoredTaskRunner {

    @Resource
    private CouponSeckillService couponSeckillService;

    @Override
    public String taskName() {
        return "closeExpiredCouponSeckillOrders";
    }

    @Override
    public void run() {
        couponSeckillService.closeExpired();
    }
}
