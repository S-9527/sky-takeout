<template>
  <div ref="containerRef" class="chart-container" :style="containerStyle"></div>
</template>

<script setup lang="ts">
import {
  computed,
  onMounted,
  onScopeDispose,
  ref,
  shallowRef,
  watch
} from 'vue'
import { echarts, type EChartsOption } from '@/utils/echarts'

const props = withDefaults(
  defineProps<{
    option: EChartsOption
    height?: string | number
  }>(),
  {
    option: () => ({}),
    height: '300px'
  }
)

const containerRef = ref<HTMLDivElement>()
const chart = shallowRef<ReturnType<typeof echarts.init>>()

const containerStyle = computed(() => ({
  width: '100%',
  height: typeof props.height === 'number' ? `${props.height}px` : props.height
}))

function render() {
  if (!containerRef.value) {
    return
  }
  if (!chart.value) {
    chart.value = echarts.init(containerRef.value)
  }
  chart.value.setOption(props.option, { replaceMerge: ['series'] })
}

function resize() {
  chart.value?.resize()
}

let observer: ResizeObserver | undefined

onMounted(() => {
  render()
  if (containerRef.value) {
    observer = new ResizeObserver(resize)
    observer.observe(containerRef.value)
  }
})

watch(() => props.option, render, { deep: true })

onScopeDispose(() => {
  observer?.disconnect()
  observer = undefined
  chart.value?.dispose()
  chart.value = undefined
})

defineExpose({ resize })
</script>
