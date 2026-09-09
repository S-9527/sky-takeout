// 消息通知相关类型

// 消息通知
export interface Message {
  id: number
  // 1 未读 2 已读
  status: number
  content: string
  details: string
  createTime?: string
  // 消息类型 1 待接单 2 急单 3 待派送 4 催单 5 闭店数据
  type?: number
}

// 消息分页查询
export interface MessagePageQuery {
  pageNum: number
  pageSize: number
  status: number
}
