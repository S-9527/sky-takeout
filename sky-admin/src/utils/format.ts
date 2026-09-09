// 数值格式化

// 金额。后端返回的金额是浮点,直接渲染会出现 38.000000001 这类尾巴,
// 原代码用 Number(x.toFixed(2)) * 100 / 100 做四舍五入(Math.round 等价且不含字符串乘法),
// 返回值仍是 number,模板里的显示形式(￥38 而非 ￥38.00)保持不变
export const money = (value: number | string | null | undefined): number => {
  const n = Number(value)
  return Number.isFinite(n) ? Math.round(n * 100) / 100 : 0
}

// 比例(0-1)转百分数字符串,带 % 后缀,digits 控制小数位
export const percent = (
  ratio: number | string | null | undefined,
  digits = 2
): string => `${(Number(ratio ?? 0) * 100).toFixed(digits)}%`
