<template>
  <business-statistics ref="statisticsRef" mode="sale" />
</template>

<script>
import BusinessStatistics from '../../components/business-statistics.vue'

export default {
  components: {
    BusinessStatistics
  },
  onPullDownRefresh() {
    const view = this.$refs.statisticsRef
    const request = view && view.refresh ? view.refresh() : Promise.resolve()
    request.finally(() => uni.stopPullDownRefresh())
  }
}
</script>
