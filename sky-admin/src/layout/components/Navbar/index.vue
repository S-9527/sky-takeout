<template>
  <div class="navbar">
    <div class="statusBox">
      <hamburger id="hamburger-container"
                 :is-active="sidebar.opened"
                 class="hamburger-container"
                 @toggle-click="toggleSideBar" />
      <span v-if="status === 1"
            class="businessBtn">营业中</span>
      <span v-else
            class="businessBtn closing">打烊中</span>
    </div>

    <div class="right-menu">
      <div class="rightStatus">
        <audio ref="audioVo"
               hidden>
          <source src="./../../../assets/preview.mp3" type="audio/mp3" />
        </audio>
        <audio ref="audioVo2"
               hidden>
          <source src="./../../../assets/reminder.mp3" type="audio/mp3" />
        </audio>
        <span class="navicon operatingState"
              @click="handleStatus"><i />营业状态设置</span>
      </div>
      <UserDropdown :name="userInfo.name ?? ''"
                    @open-password="handlePwd"
                    @logout="logout" />
    </div>

    <BusinessStatus v-model="dialogVisible"
                    :status="status"
                    @change="getStatus" />
    <Password :dialog-form-visible="dialogFormVisible"
              @handleclose="handlePwdClose" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import Hamburger from '@/components/Hamburger/index.vue'
import { useNotifications } from '@/composables/useNotifications'
import { getStatus as getApiStatus } from '@/api/users'
import BusinessStatus from '../components/BusinessStatus.vue'
import UserDropdown from '../components/UserDropdown.vue'
import Password from '../components/password.vue'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

// 提示音元素由组件持有,WebSocket 建连与消息分发交给 composable
const audioVo = ref<HTMLAudioElement>()
const audioVo2 = ref<HTMLAudioElement>()
useNotifications({ pending: audioVo, urging: audioVo2 })

const dialogVisible = ref(false)
const status = ref(1)
const dialogFormVisible = ref(false)

const sidebar = computed(() => appStore.sidebar)
const userInfo = computed(() => userStore.userInfo)

onMounted(() => {
  getStatus()
})

const toggleSideBar = () => {
  appStore.ToggleSideBar(false)
}

const logout = async () => {
  await userStore.LogOut()
  router.replace({ path: '/login' })
}

const getStatus = async () => {
  status.value = await getApiStatus()
}

const handleStatus = () => {
  dialogVisible.value = true
}

const handlePwd = () => {
  dialogFormVisible.value = true
}

const handlePwdClose = () => {
  dialogFormVisible.value = false
}
</script>

<style lang="scss" scoped>
.navbar {
  height: 60px;
  position: relative;
  background: #ffc100;

  .statusBox {
    float: left;
    height: 100%;
    align-items: center;
    display: flex;
  }
  .hamburger-container {
    padding: 0 12px 0 20px;
    cursor: pointer;
    transition: background 0.3s;
    -webkit-tap-highlight-color: transparent;

    &:hover {
      background: rgba(0, 0, 0, 0.025);
    }
  }
  .right-menu {
    float: right;
    margin-right: 20px;
    color: #333333;
    font-size: 14px;

    span {
      padding: 0 10px;
      width: 130px;
      display: inline-block;
      cursor: pointer;
      &:hover {
        background: rgba(255, 255, 255, 0.52);
      }
    }
  }
  .rightStatus {
    height: 100%;
    line-height: 60px;
    display: flex;
    align-items: center;
    float: left;
  }
  .businessBtn {
    height: 22px;
    line-height: 20px;
    background: #fd3333;
    border: 1px solid #ffffff;
    border-radius: 4px;
    display: inline-block;
    padding: 0 6px;
    color: #fff;
  }
  .closing {
    background: #6a6a6a;
  }
  .navicon {
    i {
      display: inline-block;
      width: 18px;
      height: 18px;
      vertical-align: sub;
      margin: 0 4px 0 0;
    }
  }
  .operatingState {
    i {
      background: url('./../../../assets/icons/time.png') no-repeat;
      background-size: contain;
    }
  }
}
</style>
<style lang="scss">
.el-notification {
  width: 419px !important;
  .el-notification__title {
    margin-bottom: 14px;
    color: #333;
  }
}
</style>