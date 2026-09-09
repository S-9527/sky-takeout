<template>
  <el-pagination
    v-if="total > 10"
    class="pageList"
    :page-sizes="[10, 20, 30, 40]"
    :page-size="pageSize"
    :current-page="page"
    layout="total, sizes, prev, pager, next, jumper"
    :total="total"
    @size-change="emit('size-change', $event)"
    @current-change="emit('current-change', $event)"
  />
</template>

<script setup lang="ts">
defineProps<{
  total: number
  page: number
  pageSize: number
}>()

const emit = defineEmits<{
  'size-change': [val: number]
  'current-change': [val: number]
}>()
</script>
