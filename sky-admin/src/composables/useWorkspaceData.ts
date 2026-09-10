import { onMounted, ref } from 'vue'
import {
  getWorkspaceBusinessData,
  getWorkspaceDishOverview,
  getWorkspaceOrderOverview,
  getWorkspaceSetmealOverview
} from '@/api/index'
import type {
  BusinessDataVO,
  DishOverViewVO,
  OrderOverViewVO,
  SetmealOverViewVO
} from '@/api/types/report'

// 遮罩最短显示延时:请求耗时短于该值时直接出结果,
// 避免工作台每次进入都重挂载、四接口上百毫秒内返回导致遮罩一闪而过
const LOADING_DELAY = 300

export function useWorkspaceData() {
  const overviewData = ref<BusinessDataVO>()
  const orderviewData = ref<OrderOverViewVO>()
  const dishesData = ref<DishOverViewVO>()
  const setMealData = ref<SetmealOverViewVO>()
  const loading = ref(false)
  const error = ref('')

  async function refresh() {
    error.value = ''
    const timer = setTimeout(() => {
      loading.value = true
    }, LOADING_DELAY)
    try {
      const [business, orders, dishes, setmeals] = await Promise.all([
        getWorkspaceBusinessData(),
        getWorkspaceOrderOverview(),
        getWorkspaceDishOverview(),
        getWorkspaceSetmealOverview()
      ])
      overviewData.value = business
      orderviewData.value = orders
      dishesData.value = dishes
      setMealData.value = setmeals
    } catch (err) {
      error.value = err instanceof Error ? err.message : '工作台数据加载失败'
    } finally {
      clearTimeout(timer)
      loading.value = false
    }
  }

  onMounted(refresh)

  return { overviewData, orderviewData, dishesData, setMealData, loading, error, refresh }
}
