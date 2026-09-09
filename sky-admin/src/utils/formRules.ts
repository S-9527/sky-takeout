import type { FormItemRule } from 'element-plus'

export type Validator = NonNullable<FormItemRule['validator']>

/** 2-20 个中英文或数字字符,用于分类/菜品/套餐名称 */
export const nameRule = (
  label: string,
  emptyMsg = `${label}不能为空`
): FormItemRule => ({
  required: true,
  validator: ((_rule, value, callback) => {
    if (!value) return callback(new Error(emptyMsg))
    if (!/^[A-Za-z0-9\u4e00-\u9fa5]{2,20}$/.test(value)) {
      return callback(new Error(`${label}输入不符，请输入2-20个字符`))
    }
    callback()
  }) as Validator,
  trigger: 'blur'
})

/** 金额:大于 0,最多两位小数 */
export const amountRule = (label: string): FormItemRule => ({
  required: true,
  validator: ((_rule, value, callback) => {
    const reg = /^([1-9]\d{0,5}|0)(\.\d{1,2})?$/
    if (!reg.test(value) || Number(value) <= 0) {
      return callback(
        new Error(`${label}格式有误，请输入大于零且最多保留两位小数的金额`)
      )
    }
    callback()
  }) as Validator,
  trigger: 'blur'
})

/** 排序:0-99 的整数 */
export const sortRule = (label: string): FormItemRule => ({
  required: true,
  validator: ((_rule, value, callback) => {
    const str = String(value ?? '')
    if (!str) return callback(new Error(`${label}不能为空`))
    if (!/^\d+$/.test(str)) return callback(new Error(`${label}只能输入数字`))
    if (Number(str) < 0 || Number(str) > 99) {
      return callback(new Error(`${label}范围在0-99范围内`))
    }
    callback()
  }) as Validator,
  trigger: 'blur'
})

/** 账号:3-20 位小写字母或数字 */
export const accountRule = (
  label: string,
  emptyMsg = `${label}不能为空`
): FormItemRule => ({
  required: true,
  validator: ((_rule, value, callback) => {
    if (!value) return callback(new Error(emptyMsg))
    if (!/^[a-z0-9]{3,20}$/.test(value)) {
      return callback(new Error(`${label}输入不符，请输入3-20个字符`))
    }
    callback()
  }) as Validator,
  trigger: 'blur'
})
