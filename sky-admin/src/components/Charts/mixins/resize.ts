import { onActivated, onDeactivated, onMounted, onBeforeUnmount } from 'vue';

export default function useChartResize(chartGetter: () => { resize: () => void } | null | undefined) {

  const getChart = () => {
    return chartGetter()
  }

  const chartResizeHandler = () => {
    const chart = getChart()
    if (chart) {
      chart.resize();
    }
  }

  const sidebarResizeHandler = (e: TransitionEvent) => {
    if (e.propertyName === 'width') {
      chartResizeHandler();
    }
  }

  let sidebarElm: Element | undefined

  const initResizeEvent = () => {
    window.addEventListener('resize', chartResizeHandler);
  }

  const destroyResizeEvent = () => {
    window.removeEventListener('resize', chartResizeHandler);
  }

  const initSidebarResizeEvent = () => {
    sidebarElm = document.getElementsByClassName('sidebar-container')[0];
    if (sidebarElm) {
      sidebarElm.addEventListener('transitionend', sidebarResizeHandler as EventListener);
    }
  }

  const destroySidebarResizeEvent = () => {
    if (sidebarElm) {
      sidebarElm.removeEventListener('transitionend', sidebarResizeHandler as EventListener);
    }
  }

  onMounted(() => {
    initResizeEvent();
    initSidebarResizeEvent();
  })

  onBeforeUnmount(() => {
    destroyResizeEvent();
    destroySidebarResizeEvent();
  })

  onActivated(() => {
    initResizeEvent();
    initSidebarResizeEvent();
  })

  onDeactivated(() => {
    destroyResizeEvent();
    destroySidebarResizeEvent();
  })
}