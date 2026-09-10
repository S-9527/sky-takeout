// 拨打电话
export const call = (val: string) => {
  uni.makePhoneCall({
    phoneNumber: val,
    success() {
      // console(e)
    },
    fail() {
      // console(e)
    }
  })
}

// 分割电话号码
export const splitMobile = (mobile: string | number) => {
  return String(mobile).replace(/(?=(\d{4})+$)/g, '-')
}

// 手机号去掉横杠(替代 vue2 的 getPhoneNum 过滤器)
export const formatPhone = (str: string) => {
  return str.replace(/-/g, '')
}

// 判断地址标签
export const getLableVal = (item: string) => {
  switch (item) {
    case '1':
      return '公司'
    case '2':
      return '家'
    case '3':
      return '学校'
    default:
      return '其他'
  }
}

// 订单状态：1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
export const statusWord = (status: number, time?: number) => {
  if (time) {
    if (status === 1 && time > 0) {
      return '待付款'
    } else if (status === 5 || (time < 0 && status === 1)) {
      return '已完成'
    }
  }
  switch (status) {
    case 1:
      return '待付款'
    case 2:
      return '等待商家接单'
    case 3:
      return '商家已接单'
    case 4:
      return '派送中'
    case 5:
      return '已完成'
    case 6:
      return '已取消'
    default:
      return ''
  }
}

// 倒计时(原实现为空函数,保留占位)
export const runTimeBack = () => {
  // eslint-disable-next-line no-unused-expressions
  return
}

// 距离支付超时的剩余毫秒数(15 分钟窗口)
export const getOvertime = (time: string) => {
  const end = Date.parse(new Date(time.replace(/-/g, '/')).toString())
  const now = Date.parse(new Date().toString())
  const m15 = 15 * 60 * 1000
  const msec = m15 - (now - end)
  return msec
}

// 获取周几
export const getWeekDate = (date: string | number | Date) => {
  const now = new Date(date)
  const day = now.getDay()
  const weeks = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return weeks[day]
}

function addZero(s: number) {
  return s < 10 ? '0' + s : s
}

// 现在 +1 小时,格式 yyyy-MM-dd HH:mm:ss
export const presentFormat = () => {
  const date = new Date()
  date.setTime(date.getTime() + 3600000)
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = date.getHours()
  const minute = date.getMinutes()
  const second = date.getSeconds()
  return (
    year + '-' + addZero(month) + '-' + addZero(day) + ' ' + addZero(hour) + ':' + addZero(minute) + ':' + addZero(second)
  )
}

// 今天/明天 + 指定 HH:mm,补全到秒
export const dateFormat = (b: boolean, time: string) => {
  const t = new Date()
  const date = b ? new Date(t.setDate(t.getDate() + 1)) : t
  const y = date.getFullYear().toString()
  let m = (date.getMonth() + 1).toString()
  let d = date.getDate().toString()
  if (m.length < 2) {
    m = '0' + m
  }
  if (d.length < 2) {
    d = '0' + d
  }
  return y + '-' + m + '-' + d + ' ' + (time.length === 5 ? time + ':00' : time)
}
