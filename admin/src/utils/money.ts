/**
 * 金额工具。
 *
 * 契约规定所有金额字段都是**整数分**(`_cents` 后缀),前端只在展示时除以 100,
 * 绝不参与浮点运算后再回传 —— 回传的一律是整数分(D1)。
 */

/** 分 → 元数字,仅用于展示/图表,不用于回传 */
export function centsToYuan(cents: number | null | undefined): number {
  return (cents ?? 0) / 100
}

/** 分 → `¥12.34`;空值显示为 `-`,避免把"没有金额"画成 0 元 */
export function formatCents(cents: number | null | undefined): string {
  if (cents === null || cents === undefined) return '-'
  const negative = cents < 0
  const abs = Math.abs(cents)
  const yuan = Math.floor(abs / 100)
  const fen = String(abs % 100).padStart(2, '0')
  return `${negative ? '-' : ''}¥${yuan}.${fen}`
}

/**
 * 元输入 → 分。表单里用户填的是元(如 `48`、`48.5`、`48.55`)。
 *
 * 用字符串拆分而不是 `Math.round(parseFloat(x) * 100)`:后者对 `48.55` 这类值
 * 会先产生 `4854.999...` 的浮点误差,虽然 `Math.round` 多半能救回来,
 * 但拆分法没有"多半"。
 *
 * @returns 整数分;输入非法返回 `null`
 */
export function yuanToCents(input: string | number | null | undefined): number | null {
  if (input === null || input === undefined) return null
  const text = String(input).trim()
  if (text === '') return null
  const match = /^(-)?(\d+)(?:\.(\d{1,2}))?$/.exec(text)
  if (!match) return null
  const [, sign, yuanPart, fenPart = ''] = match
  const cents = Number(yuanPart) * 100 + Number(fenPart.padEnd(2, '0'))
  return sign ? -cents : cents
}

/** 分 → 元字符串(表单回填用,如 `4855` → `48.55`) */
export function centsToYuanText(cents: number | null | undefined): string {
  if (cents === null || cents === undefined) return ''
  const negative = cents < 0
  const abs = Math.abs(cents)
  return `${negative ? '-' : ''}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}
