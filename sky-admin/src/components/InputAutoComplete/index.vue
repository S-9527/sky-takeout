<!--  -->
<template>
  <div class="input-auto-complete">
    <el-input
      v-model="input"
      :placeholder="placeholder"
      style="width: 250px"
      clearable
      @clear="init"
      @keyup.enter="init"
    >
      <template #prefix>
        <el-icon
          class="el-input__icon"
          style="cursor: pointer"
          @click="init"
        >
          <Search />
        </el-icon>
      </template>
    </el-input>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { PropType } from 'vue'
import { Search } from '@element-plus/icons-vue'

defineProps({
  data: { type: Array as PropType<any[]>, default: () => [] },
  placeholder: { type: String, default: '' },
  ObKey: { type: String, default: 'name' }
})

const emit = defineEmits(['init'])

const input = ref('')

const init = () => {
  emit('init', input.value)
}
</script>
<style scoped>
.input-auto-complete {
  display: inline-block;
}
</style>