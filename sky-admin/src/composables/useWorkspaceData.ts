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

export function useWorkspaceData() {
  const overviewData = ref<BusinessDataVO>()
  const orderviewData = ref<OrderOverViewVO>()
  const dishesData = ref<DishOverViewVO>()
  const setMealData = ref<SetmealOverViewVO>()
  const loading = ref(false)
  const error = ref('')

  async function refresh() {
    loading.value = true
    error.value = ''
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
      loading.value = false
    }
  }

  onMounted(refresh)

  return { overviewData, orderviewData, dishesData, setMealData, loading, error, refresh }
}
