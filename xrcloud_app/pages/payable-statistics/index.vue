<template>
  <partner-debt-statistics ref="statisticsRef" mode="payable" />
</template>

<script>
import PartnerDebtStatistics from '../../components/partner-debt-statistics.vue'

export default {
  components: {
    PartnerDebtStatistics
  },
  onPullDownRefresh() {
    const view = this.$refs.statisticsRef
    const request = view && view.refresh ? view.refresh() : Promise.resolve()
    request.finally(() => uni.stopPullDownRefresh())
  }
}
</script>
