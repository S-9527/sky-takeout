<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <el-result icon="info" title="404" sub-title="页面不存在或已被移除">
    <template #extra>
      <el-button type="primary" @click="router.replace({ name: 'workbench' })">返回工作台</el-button>
    </template>
  </el-result>
</template>
