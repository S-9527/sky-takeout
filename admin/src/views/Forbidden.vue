<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <el-result icon="warning" data-testid="forbidden" title="无访问权限" sub-title="该功能仅管理员可用">
    <template #extra>
      <el-button type="primary" @click="router.replace({ name: 'workbench' })">返回工作台</el-button>
    </template>
  </el-result>
</template>
