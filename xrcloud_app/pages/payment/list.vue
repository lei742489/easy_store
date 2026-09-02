<template>
  <voucher-list ref="voucherListRef" mode="payment" />
</template>

<script>
import VoucherList from '../../components/voucher-list.vue'

export default {
  components: { VoucherList },
  onShow() {
    this.$refs.voucherListRef && this.$refs.voucherListRef.handlePageShow()
  },
  onReachBottom() {
    this.$refs.voucherListRef && this.$refs.voucherListRef.loadMore()
  },
  onPageScroll(event) {
    this.$refs.voucherListRef && this.$refs.voucherListRef.handlePageScroll(event.scrollTop)
  },
  onPullDownRefresh() {
    const list = this.$refs.voucherListRef
    if (!list) {
      uni.stopPullDownRefresh()
      return
    }
    list.handlePullDownRefresh().finally(() => uni.stopPullDownRefresh())
  }
}
</script>
