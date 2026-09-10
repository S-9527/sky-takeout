<template>
  <div class="avatar-wrapper">
    <div :class="open ? 'userInfo' : ''"
         @mouseenter="open = true"
         @mouseleave="open = false">
      <el-button type="primary"
                 :class="open ? 'active' : ''">
        {{ name }}<i class="el-icon-arrow-down" />
      </el-button>
      <div v-if="open"
           class="userList">
        <p class="amendPwdIcon"
           @click="emit('open-password')">
          修改密码<i />
        </p>
        <p class="outLogin"
           @click="emit('logout')">
          退出登录<i />
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps<{ name: string }>()
const emit = defineEmits<{
  (e: 'open-password'): void
  (e: 'logout'): void
}>()

const open = ref(false)
</script>

<style lang="scss" scoped>
.avatar-wrapper {
  margin-top: 14px;
  margin-left: 18px;
  position: relative;
  float: right;
  width: 120px;
  text-align: left;

  .el-button--primary {
    background: rgba(255, 255, 255, 0.52);
    border-radius: 4px;
    padding-top: 0px;
    padding-bottom: 0px;
    position: relative;
    width: 120px;
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
    &::before {
      content: '';
    }
  }

  .amendPwdIcon {
    i {
      width: 18px;
      height: 18px;
      background: url('./../../../assets/icons/btn_gaimi@2x.png') no-repeat;
      background-size: contain;
      margin-top: 8px;
    }
  }
  .outLogin {
    cursor: pointer;
    i {
      width: 18px;
      height: 18px;
      background: url('./../../../assets/icons/btn_close@2x.png') no-repeat
        100% 100%;
      background-size: contain;
      margin-top: 8px;
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
}
</style>