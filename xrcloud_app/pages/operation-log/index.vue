<template>
  <root-management ref="view" mode="log" />
</template>

<script>
import RootManagement from '../../components/root-management.vue'

export default {
  components: { RootManagement },
  onPullDownRefresh() {
    const view = this.$refs.view
    const request = view && view.refresh ? view.refresh() : Promise.resolve()
    request.finally(() => uni.stopPullDownRefresh())
  },
  onReachBottom() {
    const view = this.$refs.view
    if (view && view.loadMore) view.loadMore()
  }
}
</script>
