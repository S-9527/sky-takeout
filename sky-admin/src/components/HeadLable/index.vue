<template>
  <div class="HeadLable">
    <span
      v-if="goback"
      class="goBack"
      @click="goBack()"
    ><img
      src="@/assets/icons/btn_back@2x.png"
      alt=""
    > 返回</span>
    <span>{{ title }}</span>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

defineProps({
  goback: { type: Boolean, default: false },
  title: { type: String, default: '' }
})

const router = useRouter()

const goBack = () => {
  router.go(-1)
}
</script>

<style lang="scss" scoped>
/* 与 Vue2 原版一致的页头:64px 行高、16px 加粗标题、18px 返回图标、
   返回与标题间有 1px #D8DDE3 竖线分隔 */
.HeadLable {
  display: flex;
  align-items: center;
  height: 64px;
  font-size: 16px;
  font-weight: 700;
  color: #333;

  .goBack {
    display: flex;
    align-items: center;
    font-weight: 400;
    padding-right: 14px;
    margin-right: 14px;
    border-right: 1px solid #d8dde3;
    cursor: pointer;

    img {
      width: 18px;
      height: 18px;
      margin-right: 5px;
    }
  }
}
</style>