package ecommerce.order.support;

import java.util.regex.Pattern;

/**
 * 收货手机号格式校验。
 * <p>
 * 接受两种格式：
 * <ul>
 *   <li><b>11 位明文</b>：{@code 13800008001}，正常新增的地址都是这种；</li>
 *   <li><b>脱敏格式</b>：{@code 138****8001} —— 存量地址数据本身就是脱敏存储的，
 *       下单/改地址时按原样放行，不在这里拦，避免存量数据无法成单。</li>
 * </ul>
 * 脱敏号只是能过校验，实际发货拿不到真实号码，所以调用方应配合 {@link #isMasked(String)} 打日志提示。
 */
public final class PhoneValidator {

    /** 11 位明文手机号 */
    private static final Pattern PLAIN = Pattern.compile("^\\d{11}$");

    /** 脱敏手机号：前 3 位 + 4 个星号 + 后 4 位，如 {@code 138****8001} */
    private static final Pattern MASKED = Pattern.compile("^\\d{3}\\*{4}\\d{4}$");

    private PhoneValidator() {
    }

    /** 是否为可用的收货手机号（明文或脱敏格式） */
    public static boolean isValid(String phone) {
        if (phone == null) {
            return false;
        }
        String value = phone.trim();
        return PLAIN.matcher(value).matches() || MASKED.matcher(value).matches();
    }

    /** 是否为脱敏格式手机号（这种号码下单了也发不出去，需要提示运营补全） */
    public static boolean isMasked(String phone) {
        return phone != null && MASKED.matcher(phone.trim()).matches();
    }
}
