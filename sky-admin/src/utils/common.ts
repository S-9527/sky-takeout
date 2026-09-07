export const checkProcessEnv = () => {
  return import.meta.env.VITE_DELETE_PERMISSIONS === 'true'
}
export const debounce = (fn: (...args: any[]) => void, time = 200) => {
  // 定时器
  let timer: ReturnType<typeof setTimeout> | null = null
  return function (this: unknown, ...args: any[]) {
    if (timer) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      timer = null
      fn.apply(this, args)
    }, time)
  }
}
//节流
export const throttle = (fn: (...args: any[]) => void, time = 1000) => {
  let timer: ReturnType<typeof setTimeout> | null = null
  return function (this: unknown, ...args: any[]) {
    if (timer) {
      return
    }
    timer = setTimeout(() => {
      timer = null
    }, time)
    fn.apply(this, args)
  }
}
// 判断正、负
export const strIncrease = (str: string) => {
  if (str.slice(0, 1) === '-') {
    return true
  }
}