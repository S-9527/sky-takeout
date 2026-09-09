// 路由 query 上的数值解析
// 直接 Number(route.query.x) 在参数缺失时得到 NaN,会被原样打进请求;
// 这里统一把缺省、非法值(abc)、小数、数组都归为 undefined,由调用方兜底
export const parseQueryNumber = (value: unknown): number | undefined => {
  const n = Number(value)
  return Number.isInteger(n) ? n : undefined
}
