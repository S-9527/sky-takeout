import { onBeforeMount, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore, DeviceType } from '@/store/modules/app'

const WIDTH = 992; // refer to Bootstrap's responsive design

export default function useResize() {
  const appStore = useAppStore()

  const isMobile = () => {
    const rect = document.body.getBoundingClientRect()
    return rect.width - 1 < WIDTH
  }

  const resizeHandler = () => {
    if (!document.hidden) {
      const mobile = isMobile()
      appStore.ToggleDevice(mobile ? DeviceType.Mobile : DeviceType.Desktop)
      if (mobile) {
        appStore.CloseSideBar(true)
      }
    }
  }

  onBeforeMount(() => {
    window.addEventListener('resize', resizeHandler)
  })

  onMounted(() => {
    if (isMobile()) {
      appStore.ToggleDevice(DeviceType.Mobile)
      appStore.CloseSideBar(true)
    }
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', resizeHandler)
  })

  const route = useRoute()
  watch(
    () => route.path,
    () => {
      if (appStore.device === DeviceType.Mobile && appStore.sidebar.opened) {
        appStore.CloseSideBar(false)
      }
    }
  )

  return {
    isMobile
  }
}