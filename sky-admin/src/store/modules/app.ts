import { ref } from 'vue'
import { defineStore } from 'pinia'
import { setSidebarStatus } from '@/utils/cookies'

export const DeviceType = {
  Mobile: 'Mobile',
  Desktop: 'Desktop'
} as const

export type DeviceType = (typeof DeviceType)[keyof typeof DeviceType]

export const useAppStore = defineStore('app', () => {
  const device = ref<DeviceType>(DeviceType.Desktop)
  const sidebar = ref({
    opened: true,
    withoutAnimation: false
  })
  const statusNumber = ref(0)

  function ToggleSideBar(withoutAnimation: boolean) {
    sidebar.value.opened = !sidebar.value.opened
    sidebar.value.withoutAnimation = withoutAnimation
    if (sidebar.value.opened) {
      setSidebarStatus('opened')
    } else {
      setSidebarStatus('closed')
    }
  }

  function CloseSideBar(withoutAnimation: boolean) {
    sidebar.value.opened = false
    sidebar.value.withoutAnimation = withoutAnimation
    setSidebarStatus('closed')
  }

  function ToggleDevice(type: DeviceType) {
    device.value = type
  }

  function StatusNumber(val: number) {
    statusNumber.value = val
  }

  return {
    device,
    sidebar,
    statusNumber,
    ToggleSideBar,
    CloseSideBar,
    ToggleDevice,
    StatusNumber
  }
})