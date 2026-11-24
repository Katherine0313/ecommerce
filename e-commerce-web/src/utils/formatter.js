/**
 * 格式化金额
 * @param value
 * @param fixed
 * @returns
 */
export function formatMoney(value, fixed = 0) {
  let unit = value < 10000 ? '' : value < 100000000 ? 'w' : '亿';
  value =
    value < 10000
      ? value
      : value < 100000000
      ? value / 10000
      : value / 100000000;
  let format = value.toFixed(fixed);
  const _val = format.split('.');
  const _int = _val[0],
    _dec = _val[1];
  return `${_val}${unit}`;
}
/**
 * 千位格式化
 * @param value
 * @param fixed
 * @returns
 */
export function formatThousand(value, fixed = 0) {
  // Number() 兜底：pay_amount 是 REAL 列但可能以字符串返回，直接 .toFixed 会抛错
  const [int, dec] = Number(value).toFixed(fixed).split('.');
  // 保留原有行为：小数部分为 0 时不显示（52.00 → 52）
  const tail = dec && parseInt(dec) !== 0 ? `.${dec}` : '';
  // 正则分组替换。原来的 for 循环在「整数位是 3 的倍数」时会多出一个前导逗号：
  // 138 → ',138'、138000 → ',138,000'（演示数据恰好都是 4/5 位数，所以一直没暴露）
  return int.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + tail;
}
