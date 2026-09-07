<template>
  <div class="dashboard-container">
    <div class="informBox">
      <ul class="conTab">
        <li
          v-for="(item, index) in tabList"
          :key="index"
          :class="activeIndex === index ? 'active' : ''"
          @click="handleClass(index)"
        >
          <el-badge
            class="item"
            :class="ountUnread >= 10 ? 'badgeW' : ''"
            :value="
              ountUnread === 0 ? null : ountUnread > 99 ? '99+' : ountUnread
            "
            :hidden="!([1].includes(item.value) && ountUnread)"
            >{{ item.label }}</el-badge
          >
        </li>
      </ul>

      <el-button
        class="right-el-button"
        v-if="status === 1 && baseData.length > 0"
        @click="handleBatch"
        ><i class="iconfont icon-clear"></i>全部已读</el-button
      >
      <el-button
        class="right-el-button onbutton"
        disabled
        v-else
        ><i class="iconfont icon-clear"></i>全部已读</el-button
      >
    </div>
    <div class="container newBox" :class="{ hContainer: baseData.length }">
      <div class="informList" v-if="baseData.length > 0">
        <div v-for="(item, index) in baseData" :key="index">
          <!-- 待接单 -->
          <div class="item" v-if="item.type === 1">
            <div class="tit">
              <span>【待接单】</span>{{ item.arrNew[0]
              }}<span class="fontOrderTip" @click="handleSetStatus(item.id)">
                <router-link :to="'/order?status=' + 2">{{
                  item.arrNew[1]
                }}</router-link></span
              >{{ item.arrNew[2]
              }}<span class="time">{{ item.createTime }}</span>
            </div>
          </div>
          <div class="item" v-if="item.type === 2">
            <div class="tit">
              <i>急</i><span>【待接单】</span>{{ item.arrNew[0]
              }}<span class="fontOrderTip" @click="handleSetStatus(item.id)"
                ><router-link :to="'/order?status=' + 2">{{
                  item.arrNew[1]
                }}</router-link></span
              >{{ item.arrNew[2]
              }}<span class="time">{{ item.createTime }}</span>
            </div>
          </div>
          <!-- end -->
          <!-- 待派送 -->
          <div class="item" v-if="item.type === 3">
            <div class="tit">
              <span>【待派送】</span>{{ item.arrNew[0]
              }}<span class="fontOrderTip" @click="handleSetStatus(item.id)"
                ><router-link :to="'/order?status=' + 2">{{
                  item.arrNew[1]
                }}</router-link></span
              >{{ item.arrNew[2]
              }}<span class="time">{{ item.createTime }}</span>
            </div>
          </div>
          <!-- end -->
          <!-- 催单 -->
          <div
            class="item"
            v-if="item.type === 4"
            @mouseenter="toggleShow(item.id, index)"
            @mouseleave="mouseLeaves(index)"
          >
            <div :class="isActive ? 'titAlready' : ''">
              <div class="tit">
                <span>【催单】</span>{{ item.arrNew[0] }}
                <!-- <span
                  class="fontOrderTip"
                  >去处理</span
                > -->
                <span class="time">{{ item.createTime }}</span>
              </div>
              <div v-if="shopShow && showIndex === index" class="orderInfo">
                <p>
                  <span
                    ><label>下单时间：</label>{{ item.details.orderTime }}</span
                  ><span
                    ><label>预计送达时间：</label
                    >{{ item.details.estimatedDeliveryTime }}</span
                  >
                </p>
                <p>
                  {{ item.details.consignee }}，{{ item.details.phone }}，{{
                    item.details.address
                  }}
                </p>
                <p>
                  <span
                    ><label>菜品：</label>{{ item.details.orderDishes }}</span
                  >
                </p>
              </div>
            </div>
          </div>
          <!-- end -->
          <!-- <div class="item" v-if="item.type === 4 && isActive && status === 1">
            <div class="titAlready">
              <div class="tit">
                <span>【催单】</span>{{ item.arrNew[0] }}
                <span class="time">{{ item.createTime }}</span>
              </div>
            </div>
          </div> -->
          <!-- 闭店 -->
          <!-- <div class="item" v-if="item.type === 5 && isActive && status === 1">
            <div class="titAlready">
              <div class="tit">
                <span>【今日数据】</span>认真工作的同时也要好好生活。<span
                  class="time"
                  >{{ item.createTime }}</span
                >
              </div>
            </div>
          </div> -->
          <div
            class="item"
            v-if="item.type === 5"
            @mouseenter="toggleShow(item.id, index)"
            @mouseleave="mouseLeaves(index)"
          >
            <div :class="isActive ? 'titAlready' : ''">
              <div class="tit">
                <span>【今日数据】</span>认真工作的同时也要好好生活。<span
                  class="time"
                  >{{ item.createTime }}</span
                >
              </div>
              <div v-if="shopShow && showIndex === index" class="orderInfo">
                <p>
                  <span
                    ><label>营业额：</label>{{ item.details.turnover }}</span
                  >
                  <span
                    ><label>有效订单：</label
                    >{{ item.details.validOrderCount }}笔</span
                  >
                  <span
                    ><label>订单完成率：</label
                    >{{ item.details.orderCompletionRate }}</span
                  >
                </p>
                <p>
                  <span
                    ><label>今日新增用户：</label
                    >{{ item.details.newUsers }}</span
                  >
                  <span
                    ><label>今日取消：</label
                    >{{ item.details.cancelledOrders }}笔</span
                  >
                  <span
                    ><label>今日取消金额：</label>￥{{
                      item.details.cancelledAmount
                    }}</span
                  >
                </p>
              </div>
            </div>
          </div>
        </div>
        <!-- end -->
      </div>
      <Empty v-else :is-search="isSearch" />
      <el-pagination
        v-if="counts > 10"
        class="pageList"
        :page-sizes="[10, 20, 30, 40]"
        :page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="counts"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import Empty from '@/components/Empty/index.vue'
import { useAppStore } from '@/store/modules/app'
import {
  getInformData,
  batchMsg,
  setStatus,
  getCountUnread as getCountUnreadApi,
} from '@/api/inform'

const appStore = useAppStore()

const activeIndex = ref(0)
const shopShow = ref(false)
const counts = ref<number>(0)
const page = ref<number>(1)
const pageSize = ref<number>(10)
const status = ref(1)
const baseData = ref<any[]>([])
const showIndex = ref(0)
const isSearch = ref<boolean>(false)
const isActive = ref(false)

const tabList = computed(() => {
  return [
    {
      label: '未读',
      value: 1,
    },
    {
      label: '已读',
      value: 2,
    },
  ]
})
const ountUnread = computed(() => appStore.statusNumber)

// 获取列表数据
const getData = async () => {
  const parent = {
    pageNum: page.value,
    pageSize: pageSize.value,
    status: status.value,
  }
  const { data } = await getInformData(parent)
  if (data.code === 1) {
    baseData.value = data.data.records
    counts.value = data.data.total
    let objNew = {} as any
    let arrDetails: any[] = []
    baseData.value.forEach((val: any) => {
      const arrContent = val.content.split(' ')
      val.arrNew = arrContent
      objNew = { ...val }
      objNew.details = eval('(' + objNew.details + ')')
      arrDetails.push(objNew)
    })

    baseData.value = arrDetails
  } else {
    ElMessage.error(data.msg)
  }
}

// 全部已读
const handleBatch = async () => {
  const ids: any[] = []
  baseData.value.forEach((val: any) => {
    ids.push(val.id)
  })
  const { data } = await batchMsg(ids)
  if (data.code === 1) {
    getCountUnread()
    getData()
  } else {
    ElMessage.error(data.msg)
  }
}

// 设置单个订单已读
const handleSetStatus = async (id: any) => {
  const { data } = await setStatus(id)
  if (data.code === 1) {
    if (!isActive.value) {
      getCountUnread()
      getData()
    }
  } else {
    ElMessage.error(data.msg)
  }
}

// 获取未读消息
const getCountUnread = async () => {
  const { data } = await getCountUnreadApi()
  if (data.code === 1) {
    appStore.StatusNumber(data.data)
  } else {
    ElMessage.error(data.msg)
  }
}

// 触发已读未读按钮
const handleClass = (index: any) => {
  activeIndex.value = index
  if (index === 0) {
    status.value = 1
  } else {
    status.value = 2
  }
  getData()
}

// 下拉菜单显示
const toggleShow = (id: any, index: any) => {
  shopShow.value = true
  showIndex.value = index
  let t = 3
  let timer = setInterval(() => {
    t--
    if (t === 0) {
      if (status.value === 1) {
        isActive.value = true
        handleSetStatus(id)
      }

      clearInterval(timer)
    }
  }, 1000)
}

// 下拉菜单隐藏
const mouseLeaves = (index: any) => {
  shopShow.value = false
  showIndex.value = index
}

const handleSizeChange = (val: any) => {
  pageSize.value = val
  getData()
}

const handleCurrentChange = (val: any) => {
  page.value = val
  getData()
}

getData()
</script>

<style lang="scss" scoped>
.dashboard {
  &-container {
    margin: 30px;
    .container {
      background: #fff;
      position: relative;
      z-index: 1;
      padding: 0 30px;
      border-radius: 4px;
      // min-height: 650px;
      height: calc(100% - 55px);
      overflow: hidden;
      &.newBox {
        // padding:0;
        .pageList {
          border-top: 1px solid #f3f4f7;
          padding: 40px;
          margin-top: 0;
        }
      }
    }
    .hContainer {
      height: auto !important;
    }
  }
}
</style>