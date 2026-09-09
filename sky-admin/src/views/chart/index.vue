<template>
  <div class="dashboard-container">
    <HeadLable :but-list="true">
      <div class="headBut">
        <span :class="{ act: act === 'day' }" @click="dateAct('day')"
          >日报</span
        >
        <span :class="{ act: act === 'week' }" @click="dateAct('week')"
          >周报</span
        >
        <span :class="{ act: act === 'mouth' }" @click="dateAct('mouth')"
          >月报</span
        >
      </div>
    </HeadLable>
    <div class="topLable">
      <div class="tit">
        <span v-if="act === 'day'">统计时间：00：00 - 24：00</span>
        <span v-if="act === 'week'"
          >统计时间：{{ stateTime }} -{{ endTime }}</span
        >
        <span v-if="act === 'mouth'"
          >统计时间：{{ stateTime }} -{{ endTime }}</span
        >
      </div>
      <div v-if="act === 'day'" class="dataSelect">
        <div>
          <span @click="checkaffterDate('day', 'before')">前一天</span>
          <el-date-picker
            v-model="dataTime"
            size="small"
            type="date"
            placeholder="选择日期"
            value-format="yyyy-MM-dd"
            :clearable="false"
            @change="changeDate('day')"
          />
          <span @click="checkaffterDate('day', 'after')">后一天</span>
        </div>
        <div><span class="but" @click="init()">查看今日数据</span></div>
      </div>
      <div v-if="act === 'week'" class="dataSelect">
        <div>
          <span @click="checkaffterDate('week', 'before')">前一周</span>
          <el-date-picker
            v-model="dataTime"
            size="small"
            type="week"
            format="yyyy 第 WW 周"
            value-format="yyyy-MM-dd"
            placeholder="选择周"
            :clearable="false"
            @change="changeDate('week')"
          />
          <span @click="checkaffterDate('week', 'after')">后一周</span>
        </div>
        <div>
          <span class="but" @click="dateAct('week')">查看本周数据</span>
        </div>
      </div>
      <div v-if="act === 'mouth'" class="dataSelect">
        <div>
          <span @click="checkaffterDate('mouth', 'before')">前一月</span>
          <el-date-picker
            v-model="dataTime"
            size="small"
            type="month"
            value-format="yyyy-MM-dd"
            placeholder="选择月"
            :clearable="false"
            @change="changeDate('mouth')"
          />
          <span @click="checkaffterDate('mouth', 'after')">后一月</span>
        </div>
        <div>
          <span class="but" @click="dateAct('mouth')">查看本月数据</span>
        </div>
      </div>
    </div>
    <div :key="restKey" class="container">
      <div v-if="act === 'day'" class="topDataBox">
        <div class="box nowData">
          <div class="icon">
            <img
              src="./../../assets/icons/xiangmujine@2x.png"
              width="45"
              height="43"
              alt=""
            />
          </div>
          <div class="item">
            <div>实收金额</div>
            <div>{{ topData.payTotal / 100 || 0 }}元</div>
            <div>较前一日 0%</div>
          </div>
        </div>
        <div class="box noData">
          <div class="icon">
            <img src="./../../assets/icons/jine_m-2@2x.png" width="50" alt="" />
          </div>
          <div class="item">
            <div>未收金额</div>
            <div>{{ topData.noPayTotal / 100 || 0 }}元</div>
            <div>较前一日 0%</div>
          </div>
        </div>
        <div class="box employee">
          <div class="icon">
            <img src="./../../assets/icons/renshu@2x.png" width="46" alt="" />
          </div>
          <div class="item">
            <div>就餐人数</div>
            <div>{{ topData.totalPerson || 0 }}人</div>
            <div>较前一日 0%</div>
          </div>
        </div>
      </div>
      <div class="employeeChart" style="position: relative">
        <div class="topButBox">
          <span
            :class="{ butAct: typeA == 1 }"
            @click="topActiveHandle('typeA')"
            >按金额</span
          >
          <span
            :class="{ butAct: typeA == 2 }"
            @click="topActiveHandle('typeA')"
            >按单数</span
          >
        </div>
        <Basic
          v-if="chartDataA"
          id="line"
          :chart-data="chartDataA"
          title="时段销售趋势"
        />
      </div>
    </div>
    <div class="container">
      <div class="chartBox">
        <div style="position: relative">
          <div class="topButBox">
            <span
              :class="{ butAct: typeB == 1 }"
              @click="topActiveHandle('typeB')"
              >按金额</span
            >
            <span
              :class="{ butAct: typeB == 2 }"
              @click="topActiveHandle('typeB')"
              >按销量</span
            >
          </div>
          <BarChart :chart-data="chartDataC" title="菜品分类占比" />
        </div>
        <div>
          <MixedChart :chart-data="chartDataB" title="菜单销售排行" />
        </div>
      </div>
    </div>
    <div class="container">
      <div class="chartBox">
        <div>
          <BarChart id="bar" :chart-data="chartDataD" title="店内收款构成" />
        </div>
        <div class="itemList">
          <div class="title">优惠指标</div>
          <div class="item topLab">
            <span>优惠合计</span><span>{{ discountTotal / 100 }}元</span
            ><span>{{ discountPercentTotal * 100 }}%</span>
          </div>
          <div v-for="(item, index) in discount" :key="index" class="item">
            <span>{{ item.name }}</span
            ><span>{{ item.value / 100 }}元</span
            ><span>{{ item.percent * 100 }}%</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import HeadLable from '@/components/HeadLable/index.vue'
import Basic from '@/components/Charts/Basic.vue'
import BarChart from '@/components/Charts/BarChart.vue'
import MixedChart from '@/components/Charts/MixedChart.vue'
import moment from 'moment'
import {
  getDayPayType,
  getprivilege,
  getDayDataes,
  getSalesRanking,
  getDayRanking,
  getTimeQuantumDataes,
  getTimeQuantumReceivables,
  getTimeQuantumType,
  getTimeQuantumDishes,
  getTimeQuantumDiscount,
  getChartsDataes,
} from '@/api/charts'
import type {
  DiscountItem,
  LineChartData,
  PieChartData,
  SalesSummary,
} from '@/api/charts'

const dataTime = ref('')
const restKey = ref(0)
const Today = moment().format('YYYY-MM-DD')
const week = [
  moment().startOf('week').add(1, 'day').format('YYYY-MM-DD'),
  moment().endOf('week').add(1, 'day').format('YYYY-MM-DD'),
]
const month = [
  moment().startOf('month').format('YYYY-MM-DD'),
  moment().endOf('month').format('YYYY-MM-DD'),
]
const topData = ref<SalesSummary>({ payTotal: 0, noPayTotal: 0, totalPerson: 0 })
const stateTime = ref(moment().format('YYYY-MM-DD'))
const endTime = ref(moment().format('YYYY-MM-DD'))
const act = ref('day')
const dataType = ref(1) //类型(1:金额;2:数量)
const typeA = ref(1)
const typeB = ref(1)
const chartDataA = ref<LineChartData>() // 销售趋势
const chartDataB = ref<LineChartData>() // 销售排行
const chartDataC = ref<PieChartData>() //分类占比
const chartDataD = ref<PieChartData>() // 收款构成
const discount = ref<DiscountItem[]>([]) // 优惠指标
const discountTotal = ref(0)
const discountPercentTotal = ref(0)

const init = async () => {
  dataTime.value = Today
  stateTime.value = Today
  endTime.value = Today
  // await this.getData()
  await getTimeWuantumData()
}

const checkaffterDate = (val: string, st: string) => {
  const date = moment(dataTime.value).valueOf()
  if (st == 'before') {
    // 前一天、周、月
    if (val === 'day') {
      dataTime.value = moment(date - 86400000).format('YYYY-MM-DD')
    } else if (val === 'week') {
      dataTime.value = moment(date - 86400000 * 7).format('YYYY-MM-DD')
    } else if (val === 'mouth') {
      const mouthDate = moment(dataTime.value)
        .startOf('month')
        .format('YYYY-MM-DD')
      dataTime.value = moment(moment(mouthDate).valueOf() - 86400000)
        .startOf('month')
        .format('YYYY-MM-DD')
    }
    changeDate(val)
  } else if (st == 'after') {
    // 后一天、周、月
    if (moment(date + 86400000).isBefore(moment())) {
      if (val === 'day') {
        dataTime.value = moment(date + 86400000).format('YYYY-MM-DD')
      } else if (val === 'week') {
        dataTime.value = moment(date + 86400000 * 7).format('YYYY-MM-DD')
      } else if (val === 'mouth') {
        const mouthDate = moment(dataTime.value)
          .endOf('month')
          .format('YYYY-MM-DD')
        dataTime.value = moment(moment(mouthDate).valueOf() + 86400000)
          .startOf('month')
          .format('YYYY-MM-DD')
      }
      changeDate(val)
    } else {
      let err = ''
      switch (val) {
        case 'day':
          err = '已经是最后一天了'
          break
        case 'week':
          err = '已经是最后一周了'
          break
        default:
          err = '已经是最后一个月了'
      }
      ElMessage.error(err)
    }
  }
}

// 日期选择
const changeDate = (val: string) => {
  if (stateTime.value == '' || endTime.value == '' || dataTime.value == null) {
    ElMessage.error('检索日期不能为空！')
    dataTime.value = moment().format('YYYY-MM-DD')
    stateTime.value = moment().format('YYYY-MM-DD')
    endTime.value = moment().format('YYYY-MM-DD')
    return
  }
  if (val === 'day') {
    stateTime.value = moment(dataTime.value).format('YYYY-MM-DD')
    endTime.value = moment(dataTime.value).format('YYYY-MM-DD')
    if (
      moment(dataTime.value).format('YYYY-MM-DD') ===
      moment().format('YYYY-MM-DD')
    ) {
      getData()
    } else {
      getDaySalesVolumeData()
      // this.getData()
      getTimeWuantumData()
    }
  } else {
    if (val === 'week') {
      stateTime.value = moment(dataTime.value)
        .startOf('week')
        .add(1, 'day')
        .format('YYYY-MM-DD')
      endTime.value = moment(dataTime.value)
        .endOf('week')
        .add(1, 'day')
        .format('YYYY-MM-DD')
    } else {
      stateTime.value = moment(dataTime.value)
        .startOf('month')
        .format('YYYY-MM-DD')
      endTime.value = moment(dataTime.value).endOf('month').format('YYYY-MM-DD')
    }
    getTimeWuantumData()
  }
}

// 日 周 月 切换
const dateAct = (val: string) => {
  restKey.value++
  act.value = val
  if (val === 'day') {
    init()
  } else {
    if (val === 'week') {
      dataTime.value = Today
      stateTime.value = week[0]
      endTime.value = week[1]
    } else {
      dataTime.value = Today
      stateTime.value = month[0]
      endTime.value = month[1]
    }
    getTimeWuantumData()
  }
}

const topActiveHandle = (activeKey: string) => {
  if (activeKey === 'typeA') {
    typeA.value = typeA.value === 1 ? 2 : 1
    getTimeQuantumData()
  } else {
    typeB.value = typeB.value === 1 ? 2 : 1
    getTimeQuantumTypeData()
  }
}

// 获取当天数据
const getData = () => {
  getDayData()
  getSalesRankData()
  getDayPayTypeData()
  getDayRankingData()
  getDaySalesVolumeData()
  getprivilegeData()
}

// 获取当日销售趋势信息 - 销售趋势图
const getDayData = () => {
  getDayDataes({ type: typeA.value, date: dataTime.value })
    .then((res) => {
      const data = res
        let yData: number[] = []
        if (typeA.value === 1) {
          data.series.length > 0 &&
            data.series.map((n) => {
              yData.push(n / 100)
            })
        } else {
          yData = data.series
        }
        const charts: LineChartData = { xData: data.xaxis, yData: yData }
        chartDataA.value = charts
      })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}
// 获取菜品分类销售排行 - 菜品分类占比 -当日
const getSalesRankData = () => {
  getSalesRanking({ type: typeB.value, date: dataTime.value })
    .then((res) => {
      const data = res
        const chartData: SalesRankItemForChart[] = []
        if (typeB.value === 1) {
          data.length > 0 &&
            data.forEach((n) => {
              chartData.push({ ...n, value: n.value / 100 })
            })
        } else {
          chartData.push(...data)
        }
        const charts: PieChartData = {
          legendData: [],
          seriesData: chartData,
          selected: {},
        }
        data.forEach((item) => {
          charts.legendData.push(item.name)
          charts.selected[item.name] = true
        })
        chartDataC.value = charts
      })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}
// 支付类型数据汇总 - 店内收款构成 - 当日
const getDayPayTypeData = () => {
  getDayPayType({ date: dataTime.value })
    .then((res) => {
      const data = res
        const chartData: SalesRankItemForChart[] = []
        if (typeB.value === 1) {
          data.length > 0 &&
            data.forEach((n) => {
              chartData.push({ ...n, value: n.value / 100 })
            })
        } else {
          chartData.push(...data)
        }
        const charts: PieChartData = {
          legendData: [],
          seriesData: chartData,
          selected: {},
        }
        data.forEach((item) => {
          charts.legendData.push(item.name)
          charts.selected[item.name] = true
        })
        chartDataD.value = charts
      })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}
// 获取当日菜品销售排行 - 销售排行图
const getDayRankingData = () => {
  getDayRanking({ type: dataType.value, date: dataTime.value })
    .then((res) => {
      const data = res
        const charts: LineChartData = { xData: data.xaxis, yData: data.series }
        chartDataB.value = charts
      })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}
// 获取一天的销售数量 - 顶部数据
const getDaySalesVolumeData = () => {
  // 获取当日销售数据
  getChartsDataes({ start: dataTime.value, end: dataTime.value })
    .then((res) => {
      topData.value = res
      })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}
// 获取当日各种优惠类型数据汇总
const getprivilegeData = () => {
  getprivilege({ date: dataTime.value })
    .then((res) => {
      discountTotal.value = 0
      discountPercentTotal.value = 0
      res.dataList.forEach((item) => {
        discountTotal.value += item.value
        discountPercentTotal.value += item.percent
      })
      discount.value = res.dataList
      })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}

// 查看时间段接口调用
const getTimeWuantumData = () => {
  getTimeQuantumData()
  getReceivables()
  getTimeQuantumTypeData()
  getTimeQuantumDishesDataes()
  getDaySalesVolumeData()
  getDiscount()
}

// 时间段数据获取
// 获取时间范围之内的优惠指标汇总数据 - 优惠指标
const getDiscount = () => {
  getTimeQuantumDiscount({ start: stateTime.value, end: endTime.value })
    .then((res) => {
      discountTotal.value = 0
      discountPercentTotal.value = 0
      res.dataList.forEach((item) => {
        discountTotal.value += item.value
        discountPercentTotal.value += item.percent
      })
      discount.value = res.dataList
      })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}
// 获取一定日期之内的销售趋势
const getTimeQuantumData = () => {
  getTimeQuantumDataes({
    type: typeA.value,
    start: stateTime.value,
    end: endTime.value,
  }).then((res) => {
    const data = res
      let yData: number[] = []
      if (typeA.value === 1) {
        data.series.length > 0 &&
          data.series.map((n) => {
            yData.push(n / 100)
          })
      } else {
        yData = data.series
      }
      const charts: LineChartData = { xData: data.xaxis, yData: yData }
      chartDataA.value = charts
    })
}
// 获取时间范围之内的各种支付类型数据汇总 - 店内收款构成 - 时间段
const getReceivables = () => {
  getTimeQuantumReceivables({ start: stateTime.value, end: endTime.value })
    .then((res) => {
      const data = res
        const chartData: SalesRankItemForChart[] = []
        if (typeB.value === 1) {
          data.length > 0 &&
            data.forEach((n) => {
              chartData.push({ ...n, value: n.value / 100 })
            })
        } else {
          chartData.push(...data)
        }
        const charts: PieChartData = {
          legendData: [],
          seriesData: chartData,
          selected: {},
        }
        data.forEach((item) => {
          charts.legendData.push(item.name)
          charts.selected[item.name] = true
        })
        chartDataD.value = charts
      })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}

// 获取时间范围之内的菜品类别销售汇总 -  菜品分类占比 - 时间段
const getTimeQuantumTypeData = () => {
  getTimeQuantumType({
    type: typeB.value,
    start: stateTime.value,
    end: endTime.value,
  })
    .then((res) => {
      const data = res
        const chartData: SalesRankItemForChart[] = []
        if (typeB.value === 1) {
          data.length > 0 &&
            data.forEach((n) => {
              chartData.push({ ...n, value: n.value / 100 })
            })
        } else {
          chartData.push(...data)
        }
        const charts: PieChartData = {
          legendData: [],
          seriesData: chartData,
          selected: {},
        }
        data.forEach((item) => {
          charts.legendData.push(item.name)
          charts.selected[item.name] = true
        })
        chartDataC.value = charts
      })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}
// 获取时间范围之内的菜品销售排行
const getTimeQuantumDishesDataes = () => {
  getTimeQuantumDishes({ start: stateTime.value, end: endTime.value })
    .then((res) => {
      const data = res
        const yData: number[] = []
        data.series.forEach((n) => {
          yData.push(n / 100)
        })
        const charts: LineChartData = { xData: data.xaxis, yData: yData }
        chartDataB.value = charts
      })
    .catch((err) => {
      ElMessage.error('请求出错了：' + err.message)
    })
}

interface SalesRankItemForChart {
  name: string
  percent: number
  value: number
}

init()
</script>
<style lang="scss" scoped>
.dashboard {
  &-container {
    margin: 30px;
    .headBut {
      span {
        cursor: pointer;
        margin: 0 10px;
        font-size: 18px;
        padding: 16px 0px;
      }
      .act {
        border-bottom: solid 4px $mine;
        font-weight: bold;
      }
    }
    .topLable {
      font-size: 16px;
      background: #fff;
      position: relative;
      z-index: 1;
      padding: 15px 28px;
      margin-bottom: 30px;
      border-radius: 4px;
      .tit {
        line-height: 40px;
        color: $mine;
      }
      .dataSelect {
        display: flex;
        justify-content: space-between;
        line-height: 40px;
        div:first-child {
          span:first-child {
            margin-right: 10px;
            cursor: pointer;
          }
          span:last-child {
            margin-left: 10px;
            cursor: pointer;
          }
        }
      }
      .but {
        background: $mine;
        cursor: pointer;
        display: inline-block;
        color: #fff;
        padding: 0px 20px;
        line-height: 30px;
        height: 30px;
        border-radius: 4px;
        font-size: 14px;
      }
    }
    .container {
      background: #fff;
      position: relative;
      z-index: 1;
      padding: 30px 28px;
      border-radius: 4px;
      margin-bottom: 30px;
      .topDataBox {
        display: flex;
        margin-bottom: 15px;
        .icon {
          width: 80px;
          height: 80px;
          /*padding-top: 15px;*/
          border-radius: 100%;
          background: #fff;
          margin-right: 10px;
          text-align: center;
          display: flex;
          justify-content: center;
          align-items: center;
        }
        .box {
          margin-right: 15px;
          display: flex;
          padding: 15px;
          flex: 1;
          border-radius: 5px;
          color: #fff;
          .item {
            div:nth-child(2) {
              font-size: 18px;
              line-height: 36px;
            }
          }
        }
        .nowData {
          background: linear-gradient(
            63deg,
            rgba(85, 169, 255, 1) 24%,
            rgba(55, 154, 255, 1) 100%
          );
        }
        .noData {
          background: linear-gradient(
            63deg,
            rgba(255, 168, 104, 1) 33%,
            rgba(255, 144, 61, 1) 100%
          );
        }
        .employee {
          margin-right: 0;
          background: linear-gradient(
            121deg,
            rgba(255, 136, 136, 1) 24%,
            rgba(245, 108, 108, 1) 100%
          );
        }
      }
      .chartBox {
        display: flex;
        div {
          flex: 1;
        }
        .itemList {
          .title {
            font-size: 18px;
            font-weight: bold;
            margin-bottom: 20px;
          }
          .item {
            display: flex;
            line-height: 45px;
            padding: 0 20px;
            border-bottom: solid 1px #f0f1f4;
            span {
              flex: 1;
              text-align: center;
            }
            span:first-child {
              text-align: left;
            }
          }
          .topLab {
            background: #f0f1f4;
            border-radius: 4px;
          }
        }
      }
    }
    .topButBox {
      position: absolute;
      right: 20px;
      top: 0;
      z-index: 9999;
      font-size: 12px;
      span {
        display: inline-block;
        padding: 4px 10px;
        border-radius: 4px;
        cursor: pointer;
      }
      .butAct {
        background: #3a9bff;
        color: #fff;
      }
    }
  }
}
</style>
