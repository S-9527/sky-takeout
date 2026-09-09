import request from '@/utils/request'
import type { Message, MessagePageQuery, PageResult } from './types'
// 获取消息列表
export const getInformData = (params: MessagePageQuery) => {
  return request.get<PageResult<Message>>('/messages/page', { params })
}
// 获取未读数量
export const getCountUnread = () => {
  return request.get<number>('/messages/countUnread')
}
// 全部已读（批量标记）
export const batchMsg = (data: number[]) => {
  return request.put('/messages/batch', data)
}
// 标记已读
export const setStatus = (id: number) => {
  return request.put(`/messages/${id}`)
}