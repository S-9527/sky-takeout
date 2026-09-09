import { onUnmounted, ref, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'

/** 后端推送的消息类型,原先散落在 Navbar 里以裸数字 1/2 判断 */
export const MessageType = {
  /** 新订单待接单 */
  PendingOrder: 1,
  /** 用户催单 */
  Urging: 2,
} as const

export type MessageType = (typeof MessageType)[keyof typeof MessageType]

interface WsMessage {
  type: MessageType
  content: string
  orderId: number
}

/** 待接单与催单的提示语模板 */
function messageHtml(msg: WsMessage): string {
  return msg.type === MessageType.PendingOrder
    ? `<span>您有1个<span style=color:#419EFF>订单待处理</span>,${msg.content},请及时接单</span>`
    : `${msg.content}<span style='color:#419EFF;cursor: pointer'>去处理</span>`
}

export function useNotifications(audio: {
  /** 待接单提示音 */
  pending: Ref<HTMLAudioElement | undefined>
  /** 催单提示音 */
  urging: Ref<HTMLAudioElement | undefined>
}) {
  const router = useRouter()
  const websocket = ref<WebSocket | null>(null)
  const { pending, urging } = audio

  function connect() {
    if (typeof WebSocket === 'undefined') {
      ElNotification({
        title: '提示',
        message: '当前浏览器无法接收实时报警信息，请使用谷歌浏览器！',
        type: 'warning',
        duration: 0,
      })
      return
    }

    const clientId = Math.random().toString(36).substr(2)
    const socketUrl = `${import.meta.env.VITE_SOCKET_URL}${clientId}`
    websocket.value = new WebSocket(socketUrl)

    websocket.value.onmessage = (msg) => {
      const jsonMsg = JSON.parse(msg.data) as WsMessage
      // 先归零再播放,避免同一段提示音在原有播放位上续播
      if (pending.value) pending.value.currentTime = 0
      if (urging.value) urging.value.currentTime = 0
      if (jsonMsg.type === MessageType.PendingOrder) {
        pending.value?.play()
      } else if (jsonMsg.type === MessageType.Urging) {
        urging.value?.play()
      }

      ElNotification({
        title: jsonMsg.type === MessageType.PendingOrder ? '待接单' : '催单',
        duration: 0,
        dangerouslyUseHTMLString: true,
        onClick: () => {
          router.push(`/order?orderId=${jsonMsg.orderId}`)
          setTimeout(() => {
            location.reload()
          }, 100)
        },
        message: messageHtml(jsonMsg),
      })
    }

    websocket.value.onerror = () => {
      ElNotification({
        title: '错误',
        message: '服务器错误，无法接收实时报警信息',
        type: 'error',
        duration: 0,
      })
    }
  }

  // 挂载即建连,组件卸载时由下方 onUnmounted 关闭
  connect()

  onUnmounted(() => {
    websocket.value?.close()
  })

  return { websocket }
}
