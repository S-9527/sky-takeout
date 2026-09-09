<template>
  <div class="navbar">
    <div class="statusBox">
      <hamburger id="hamburger-container"
                 :is-active="sidebar.opened"
                 class="hamburger-container"
                 @toggleClick="toggleSideBar" />
      <span v-if="status===1"
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
        <span class="navicon operatingState" @click="handleStatus"><i />营业状态设置</span>
      </div>
      <div class="avatar-wrapper">
        <div :class="shopShow?'userInfo':''"
             @mouseenter="toggleShow"
             @mouseleave="mouseLeaves">
          <el-button type="primary"
                     :class="shopShow?'active':''">
            {{ name }}<i class="el-icon-arrow-down" />
          </el-button>
          <div v-if="shopShow"
               class="userList">
            <p class="amendPwdIcon"
               @click="handlePwd">
              修改密码<i />
            </p>
            <p class="outLogin"
               @click="logout">
              退出登录<i />
            </p>
          </div>
        </div>
      </div>
    </div>
    <!-- 营业状态弹层 -->
    <el-dialog title="营业状态设置"
               v-model="dialogVisible"
               width="25%"
               :show-close="false">
      <el-radio-group v-model="setStatus">
        <el-radio :value="1">
          营业中
          <span>当前餐厅处于营业状态，自动接收任何订单，可点击打烊进入店铺打烊状态。</span>
        </el-radio>
        <el-radio :value="0">
          打烊中
          <span>当前餐厅处于打烊状态，仅接受营业时间内的预定订单，可点击营业中手动恢复营业状态。</span>
        </el-radio>
      </el-radio-group>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button type="primary"
                     @click="handleSave">确 定</el-button>
        </span>
      </template>
    </el-dialog>
    <!-- end -->
    <!-- 修改密码 -->
    <Password :dialog-form-visible="dialogFormVisible"
              @handleclose="handlePwdClose" />
    <!-- end -->
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import Hamburger from '@/components/Hamburger/index.vue'
import { getStatus as getApiStatus, setStatus as setApiStatus } from '@/api/users'
import Cookies from 'js-cookie'
import Password from '../components/password.vue'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const websocket = ref<WebSocket | null>(null)
const audioVo = ref<HTMLAudioElement>()
const audioVo2 = ref<HTMLAudioElement>()
const shopShow = ref(false)
const dialogVisible = ref(false)
const status = ref(1)
const setStatus = ref(1)
const dialogFormVisible = ref(false)

const sidebar = computed(() => appStore.sidebar)

const name = computed(() => {
  const user_info = Cookies.get('user_info')
  return userStore.userInfo.name
    ? userStore.userInfo.name
    : user_info
      ? (JSON.parse(user_info) as { name?: string }).name || ''
      : ''
})

onMounted(() => {
  document.addEventListener('click', handleClose)
  getStatus()
})

onUnmounted(() => {
  if (websocket.value) {
    websocket.value.close()
  }
})

// 添加新订单提示弹窗
const webSocket = () => {
  const clientId = Math.random().toString(36).substr(2)
  const socketUrl = import.meta.env.VITE_SOCKET_URL + clientId
  if (typeof WebSocket === 'undefined') {
    ElNotification({
      title: '提示',
      message: '当前浏览器无法接收实时报警信息，请使用谷歌浏览器！',
      type: 'warning',
      duration: 0
    })
  } else {
    websocket.value = new WebSocket(socketUrl)
    // 监听socket消息接收
    websocket.value.onmessage = function (msg) {
      if (audioVo.value) audioVo.value.currentTime = 0
      if (audioVo2.value) audioVo2.value.currentTime = 0

      const jsonMsg = JSON.parse(msg.data)
      if (jsonMsg.type === 1) {
        audioVo.value?.play()
      } else if (jsonMsg.type === 2) {
        audioVo2.value?.play()
      }
      ElNotification({
        title: jsonMsg.type === 1 ? '待接单' : '催单',
        duration: 0,
        dangerouslyUseHTMLString: true,
        onClick: () => {
          router.push(`/order?orderId=${jsonMsg.orderId}`)
          setTimeout(() => {
            location.reload()
          }, 100)
        },
        message: `${
          jsonMsg.type === 1
            ? `<span>您有1个<span style=color:#419EFF>订单待处理</span>,${jsonMsg.content},请及时接单</span>`
            : `${jsonMsg.content}<span style='color:#419EFF;cursor: pointer'>去处理</span>`
        }`
      })
    }
    // 监听socket错误
    websocket.value.onerror = function () {
      ElNotification({
        title: '错误',
        message: '服务器错误，无法接收实时报警信息',
        type: 'error',
        duration: 0
      })
    }
  }
}
webSocket()

const toggleSideBar = () => {
  appStore.ToggleSideBar(false)
}
// 退出
const logout = async () => {
  userStore.LogOut().then(() => {
    router.replace({ path: '/login' })
  })
}
// 营业状态
const getStatus = async () => {
  const data = await getApiStatus()
  status.value = data
  setStatus.value = status.value
}
// 下拉菜单显示
const toggleShow = () => {
  shopShow.value = true
}
// 下拉菜单隐藏
const mouseLeaves = () => {
  shopShow.value = false
}
// 触发空白处下来菜单关闭
const handleClose = () => {
  // shopShow.value = false
}
// 设置营业状态
const handleStatus = () => {
  dialogVisible.value = true
}
// 营业状态设置
const handleSave = async () => {
  await setApiStatus(setStatus.value)
  dialogVisible.value = false
  getStatus()
}
// 修改密码
const handlePwd = () => {
  dialogFormVisible.value = true
}
// 关闭密码编辑弹层
const handlePwdClose = () => {
  dialogFormVisible.value = false
}
</script>

<style lang="scss" scoped>
.navbar {
  height: 60px;
  // overflow: hidden;
  position: relative;
  background: #ffc100;

  // box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  .statusBox {
    float: left;
    height: 100%;
    align-items: center;
    display: flex;
  }
  .hamburger-container {
    // line-height: 54px;

    padding: 0 12px 0 20px;
    cursor: pointer;
    transition: background 0.3s;
    -webkit-tap-highlight-color: transparent;

    &:hover {
      background: rgba(0, 0, 0, 0.025);
    }
  }

  .breadcrumb-container {
    float: left;
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
    .amendPwdIcon {
      i {
        width: 18px;
        height: 18px;
        background: url(./../../../assets/icons/btn_gaimi@2x.png) no-repeat;
        background-size: contain;
        margin-top: 8px;
      }
    }
    .outLogin {
      i {
        width: 18px;
        height: 18px;
        background: url(./../../../assets/icons/btn_close@2x.png) no-repeat 100%
          100%;
        background-size: contain;
        margin-top: 8px;
      }
    }
    .outLogin {
      cursor: pointer;
    }

    &:focus {
      outline: none;
    }

    .right-menu-item {
      display: inline-block;
      padding: 0 8px;
      height: 100%;
      font-size: 18px;
      color: #5a5e66;
      vertical-align: text-bottom;

      &.hover-effect {
        cursor: pointer;
        transition: background 0.3s;

        &:hover {
          background: rgba(0, 0, 0, 0.025);
        }
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
  .avatar-wrapper {
    margin-top: 14px;
    margin-left: 18px;
    position: relative;
    // vertical-align: middle;
    float: right;
    width: 120px;
    text-align: left;
    .user-avatar {
      cursor: pointer;
      width: 40px;
      height: 40px;
      border-radius: 10px;
    }

    .el-icon-caret-bottom {
      cursor: pointer;
      position: absolute;
      right: -20px;
      top: 25px;
      font-size: 12px;
    }

    .el-button--primary {
      // height: 32px;
      background: rgba(255, 255, 255, 0.52);
      border-radius: 4px;
      padding-top: 0px;
      padding-bottom: 0px;
      position: relative;
      // top: -15px;
      width: 120px;
      // padding: 11px 12px 10px;
      padding-left: 12px;
      text-align: left;
      justify-content: flex-start;
      border: 0 none;
      height: 32px;
      line-height: 32px;
      &.active {
        background: rgba(250, 250, 250, 0);
        border: 0 none;
        .el-icon-arrow-down {
          transform: rotate(-180deg);
        }
      }
    }
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
  .mesCenter {
    i {
      background: url('./../../../assets/icons/msg.png') no-repeat;
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
.navbar {
  .el-dialog {
    padding: 0;
  }
  .el-dialog__header {
    height: 61px;
    line-height: 60px;
    background: #fbfbfa;
    padding: 0 30px;
    font-size: 16px;
    color: #333;
    border: 0 none;
  }
  .el-dialog__body {
    padding: 10px 30px 30px;
    .el-radio,
    .el-radio__input {
      white-space: normal;
    }
    .el-radio__label {
      padding-left: 5px;
      color: #333;
      font-weight: 700;
      span {
        display: block;
        line-height: 20px;
        padding-top: 12px;
        color: #666;
        font-weight: normal;
      }
    }
    .el-radio__input.is-checked .el-radio__inner {
      &::after {
        background: #333;
      }
    }
    /* EP2 把 group/radio 改成 flex,描述文字会被挤进 label 宽度并缩进;
       改回 Element UI 的 inline-block 以对齐原版布局 */
    .el-radio-group {
      display: inline-block;
      width: 100%;
      & > .is-checked {
        border: 1px solid #ffc200;
      }
    }
    .el-radio {
      display: inline-block;
      line-height: 1;
      width: 100%;
      height: auto;
      margin-right: 0;
      background: #fbfbfa;
      border: 1px solid #e5e4e4;
      border-radius: 4px;
      padding: 14px 22px;
      margin-top: 20px;
    }
  }
  .el-badge__content.is-fixed {
    top: 24px;
    right: 2px;
    width: 18px;
    height: 18px;
    font-size: 10px;
    line-height: 16px;
    border-radius: 50%;
    padding: 0;
  }
  .badgeW {
    .el-badge__content.is-fixed {
      width: 30px;
      border-radius: 20px;
    }
  }
}
.el-icon-arrow-down {
  background: url('./../../../assets/icons/up.png') no-repeat 50% 50%;
  background-size: contain;
  width: 8px;
  height: 8px;
  transform: rotate(0eg);
  margin-left: 16px;
  position: absolute;
  right: 16px;
  top: 12px;
  &:before {
    content: '';
  }
}

.userInfo {
  background: #fff;
  position: absolute;
  top: 0px;
  left: 0;
  z-index: 99;
  box-shadow: 0 2px 4px 0 rgba(0, 0, 0, 0.14);
  width: 100%;
  border-radius: 4px;
  line-height: 32px;
  padding: 0 0 5px;
  height: 105px;
  .userList {
    width: 95%;
    padding-left: 5px;
  }
  p {
    cursor: pointer;
    height: 32px;
    line-height: 32px;
    padding: 0 5px 0 7px;
    i {
      margin-left: 10px;
      vertical-align: middle;
      margin-top: 4px;
      float: right;
    }
    &:hover {
      background: #f6f1e1;
    }
  }
}
.msgTip {
  color: #419eff;
  padding: 0 5px;
}
</style>