package ecommerce.order.service;

import ecommerce.order.dto.FinanceDashboardQueryDTO;
import ecommerce.order.vo.FinanceDashboardVO;

/**
 * 财务看板只读聚合服务。
 */
public interface FinanceDashboardService {

    FinanceDashboardVO getDashboard(FinanceDashboardQueryDTO query);
}
