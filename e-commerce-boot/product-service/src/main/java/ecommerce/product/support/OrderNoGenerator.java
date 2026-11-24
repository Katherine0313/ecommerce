package ecommerce.product.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 单据号生成器：RK(入库) / CK(出库) / PD(调整) + yyyyMMdd + 4位序号。
 * <p>
 * 序号取当日同前缀单号的末尾 4 位最大值 +1；并发下可能撞唯一键，由调用方重新取号重试。
 */
@Component
public class OrderNoGenerator {
    private static final Map<String, String> PREFIX = Map.of(
        "RECEIPT", "RK",
        "SHIPMENT", "CK",
        "ADJUST",   "PD"
    );

    private final JdbcTemplate jdbc;

    public OrderNoGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String generate(String type) {
        String prefix = PREFIX.get(type);
        if (prefix == null) {
            throw new IllegalArgumentException("未知单据类型: " + type);
        }
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 取末尾 4 位而不是固定偏移：前缀/日期长度变化时不会错位
        Integer maxSeq = jdbc.queryForObject(
            "SELECT MAX(CAST(RIGHT(order_no, 4) AS SIGNED)) " +
            "FROM stock_order WHERE order_no LIKE ?",
            Integer.class, prefix + date + "%");
        int seq = (maxSeq == null ? 0 : maxSeq) + 1;
        return prefix + date + String.format("%04d", seq);
    }
}
