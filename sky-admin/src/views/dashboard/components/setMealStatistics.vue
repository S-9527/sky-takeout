<template>
  <div class="container">
    <h2 class="homeTitle">
      套餐总览<span><router-link to="setmeal">套餐管理</router-link></span>
    </h2>
    <div class="orderviewBox">
      <ul>
        <li>
          <span class="status"><i class="iconfont icon-open"></i>已启售</span>
          <span class="num">{{ setMealData.sold }}</span>
        </li>
        <li>
          <span class="status"><i class="iconfont icon-stop"></i>已停售</span>
          <span class="num">{{ setMealData.discontinued }}</span>
        </li>
        <li class="add">
          <router-link to="setmeal/add">
            <i></i>
            <p>新增套餐</p>
          </router-link>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps({
  setMealData: {
    type: Object,
    default: () => ({}),
  },
})
</script>
