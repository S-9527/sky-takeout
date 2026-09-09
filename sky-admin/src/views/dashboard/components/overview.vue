<template>
  <div class="container">
    <h2 class="homeTitle">
      今日数据<i>{{ days[1] }}</i
      ><span><router-link to="statistics">详细数据</router-link></span>
    </h2>
    <div class="overviewBox">
      <ul>
        <li>
          <p class="tit">营业额</p>
          <p class="num">¥ {{ overviewData.turnover }}</p>
        </li>
        <li>
          <p class="tit">有效订单</p>
          <p class="num">{{ overviewData.validOrderCount }}</p>
        </li>
        <li>
          <p class="tit">订单完成率</p>
          <p class="num">
            {{ percent(overviewData.orderCompletionRate, 0) }}
          </p>
        </li>
        <li>
          <p class="tit">平均客单价</p>
          <p class="num">¥ {{ overviewData.unitPrice }}</p>
        </li>

        <li>
          <p class="tit">新增用户</p>
          <p class="num">{{ overviewData.newUsers }}</p>
        </li>
      </ul>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { getday } from '@/utils/formValidate'
import { percent } from '@/utils/format'
defineProps({
  overviewData: {
    type: Object,
    default: () => ({}),
  },
})
const days = computed(() => getday())
</script>
