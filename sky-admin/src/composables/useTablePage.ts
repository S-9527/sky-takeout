import { ref } from 'vue'

/**
 * 表格分页状态与翻页处理。
 * fetcher 是页面自己的列表查询函数,翻页/改页容量时由这里触发。
 */
export function useTablePage(fetcher: () => void | Promise<unknown>) {
  const page = ref(1)
  const pageSize = ref(10)

  function refresh() {
    void fetcher()
  }

  function reloadFirstPage() {
    page.value = 1
    refresh()
  }

  function handleSizeChange(val: number) {
    pageSize.value = val
    page.value = 1
    refresh()
  }

  function handleCurrentChange(val: number) {
    page.value = val
    refresh()
  }

  return { page, pageSize, refresh, reloadFirstPage, handleSizeChange, handleCurrentChange }
}
