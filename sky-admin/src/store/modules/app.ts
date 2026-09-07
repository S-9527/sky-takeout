import { defineStore } from 'pinia'
import { setSidebarStatus } from '@/utils/cookies'

export const DeviceType = {
  Mobile: 'Mobile',
  Desktop: 'Desktop'
} as const

export type DeviceType = (typeof DeviceType)[keyof typeof DeviceType]

export interface IAppState {
  device: DeviceType
  sidebar: {
    opened: boolean
    withoutAnimation: boolean
  }
  statusNumber: number
}

export const useAppStore = defineStore('app', {
  state: (): IAppState => ({
    sidebar: {
      opened: true,
      withoutAnimation: false
    },
    device: DeviceType.Desktop,
    statusNumber: 0
  }),
  actions: {
    ToggleSideBar(withoutAnimation: boolean) {
      this.sidebar.opened = !this.sidebar.opened
      this.sidebar.withoutAnimation = withoutAnimation
      if (this.sidebar.opened) {
        setSidebarStatus('opened')
      } else {
        setSidebarStatus('closed')
      }
    },
    CloseSideBar(withoutAnimation: boolean) {
      this.sidebar.opened = false
      this.sidebar.withoutAnimation = withoutAnimation
      setSidebarStatus('closed')
    },
    ToggleDevice(device: DeviceType) {
      this.device = device
    },
    StatusNumber(device: any) {
      this.statusNumber = device
    }
  }
})