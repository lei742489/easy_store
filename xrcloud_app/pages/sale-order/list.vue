<template>
  <view class="page">
    <view class="filter-panel">
      <view class="quick-search-row">
        <input
          v-model="filters.searchKey"
          class="quick-search-input"
          placeholder="单号 / 备注 / 货品名称"
          confirm-type="search"
          @confirm="handleSearch"
        />
        <button class="quick-search-button" @click="handleSearch">
          <uni-icons type="search" color="#fff" :size="20" />
          <text>查询</text>
        </button>
      </view>

      <view class="filter-actions">
        <button class="more-filter-button" @click="openMoreFilters">
          <text>展开更多</text>
          <uni-icons type="down" color="#722ed1" :size="16" />
          <text v-if="activeFilterCount" class="filter-count">{{ activeFilterCount }}</text>
        </button>
        <button class="reset-button" @click="handleReset">
          <uni-icons type="refresh" color="#666" :size="18" />
          <text>重置</text>
        </button>
      </view>
    </view>

    <uni-popup ref="filterPopup" type="bottom" :safe-area="true" :is-mask-click="true">
      <view class="filter-popup">
        <view class="popup-header">
          <text class="popup-title">更多筛选</text>
          <uni-icons type="closeempty" color="#999" :size="22" @click="closeMoreFilters" />
        </view>

        <view class="popup-form-item">
          <text class="popup-label">客户</text>
          <customer-autocomplete
            v-model="filters.customerId"
            placeholder="名称 / 联系人 / 手机 / 拼音"
          />
        </view>

        <view class="popup-form-item">
          <text class="popup-label">状态</text>
          <picker :range="statusOptions" range-key="label" :value="statusIndex" @change="onStatusChange">
            <view class="popup-picker">
              <text :class="{ placeholder: statusIndex === 0 }">{{ statusOptions[statusIndex].label }}</text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </view>

        <view v-if="isRoot" class="popup-form-item">
          <text class="popup-label">营业员</text>
          <picker :range="cashierOptions" range-key="label" :value="cashierIndex" @change="onCashierChange">
            <view class="popup-picker">
              <text :class="{ placeholder: cashierIndex === 0 }">{{ cashierOptions[cashierIndex].label }}</text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </view>

        <view class="popup-form-item">
          <text class="popup-label">日期</text>
          <view class="popup-date-picker">
            <uni-datetime-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :clear-icon="true"
            />
          </view>
        </view>

        <view class="popup-actions">
          <button class="popup-cancel-button" @click="closeMoreFilters">取消</button>
          <button class="popup-confirm-button" @click="applyMoreFilters">应用筛选</button>
        </view>
      </view>
    </uni-popup>

    <view class="list-summary">
      <text>销售单</text>
      <text>共 {{ total }} 条</text>
    </view>

    <view v-if="records.length" class="order-list">
      <view v-for="order in records" :key="order.id" class="order-card">
        <view class="card-header">
          <view class="order-main">
            <text class="order-no">{{ order.orderNo || '-' }}</text>
            <text class="order-type" :class="{ return: Number(order.orderType) === 2 }">
              {{ Number(order.orderType) === 2 ? '退货' : '出货' }}
            </text>
          </view>
          <text class="status-tag" :class="Number(order.status) === 0 ? 'pending' : 'normal'">
            {{ Number(order.status) === 0 ? '待审核' : '正常' }}
          </text>
        </view>

        <view class="customer-row">
          <view class="customer-name">
            <uni-icons type="person" color="#722ed1" :size="18" />
            <text>{{ order.customerId_dictText || order.customerName || order.customerId || '未设置客户' }}</text>
          </view>
          <text class="order-date">{{ formatDate(order.createTime) }}</text>
        </view>

        <view class="amount-grid">
          <view class="amount-item">
            <text class="amount-label">总金额</text>
            <text class="amount-value">{{ formatAmount(order.totalAmount) }}</text>
          </view>
          <view class="amount-item">
            <text class="amount-label">应收</text>
            <text class="amount-value">{{ formatAmount(order.payableAmount) }}</text>
          </view>
          <view class="amount-item">
            <text class="amount-label">实收</text>
            <text class="amount-value" :class="{ mismatch: isPaidMismatch(order) }">
              {{ formatAmount(order.paidAmount) }}
            </text>
          </view>
          <view class="amount-item">
            <text class="amount-label">未收</text>
            <text class="amount-value debt">{{ formatAmount(order.unpaidAmount) }}</text>
          </view>
          <view v-if="isRoot" class="amount-item profit-item" style="flex-direction: row;align-items: center;padding-left: 25rpx;">
            <text class="amount-label">销售毛利：</text>
            <text class="amount-value" :class="{ loss: Number(order.grossProfit || 0) < 0 }">
              {{ formatAmount(order.grossProfit) }}
            </text>
          </view>
        </view>

        <view v-if="order.items && order.items.length" class="goods-summary">
          <view v-for="(item, index) in order.items.slice(0, 3)" :key="item.id || index" class="goods-row">
            <text class="goods-name">{{ getGoodsName(item) }}</text>
            <text class="goods-quantity">{{ formatQuantity(item.quantity) }} {{ item.unit || '' }}</text>
          </view>
          <text v-if="order.items.length > 3" class="more-goods">另有 {{ order.items.length - 3 }} 项货品</text>
        </view>

        <view v-if="order.note" class="note-row">
          <text class="note-label">备注</text>
          <text class="note-text">{{ order.note }}</text>
        </view>

        <view class="card-footer">
          <text>营业员：{{ order.cashierName || order.cashierId_dictText || '-' }}</text>
          <text v-if="order.updateBy">更新人：{{ order.updateBy }}</text>
        </view>
      </view>
    </view>

    <view v-else-if="!loading" class="empty-state">
      <uni-icons type="info" color="#aaa5b5" :size="52" />
      <text>没有查询到销售单</text>
    </view>

    <uni-load-more v-if="records.length || loading" :status="loadStatus" />
    <view class="bottom-space" />

    <image
      v-if="showGoTop"
      class="go-top-button"
      src="/static/ico/go-top.png"
      mode="aspectFit"
      @click="scrollToTop"
    />
  </view>
</template>

<script>
import CustomerAutocomplete from '../../components/customer-autocomplete.vue'
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import { getUserInfo, listSaleOrders, listAppUsers } from '../../common/api'

const createFilters = () => ({
  customerId: '',
  cashierId: '',
  searchKey: '',
  status: undefined
})

export default {
  components: { CustomerAutocomplete },
  data() {
    return {
      user: getUser() || {},
      loading: false,
      initializing: false,
      initialized: false,
      redirecting: false,
      showGoTop: false,
      current: 1,
      pageSize: 15,
      total: 0,
      records: [],
      filters: createFilters(),
      dateRange: [],
      statusIndex: 0,
      cashierIndex: 0,
      statusOptions: [
        { label: '全部状态', value: undefined },
        { label: '待审核', value: 0 },
        { label: '正常', value: 1 }
      ],
      cashierOptions: [{ label: '全部营业员', value: '' }]
    }
  },
  computed: {
    isRoot() {
      return Number(this.user.isRoot) === 1
    },
    hasMore() {
      return this.records.length < this.total
    },
    loadStatus() {
      if (this.loading) return 'loading'
      return this.hasMore ? 'more' : 'noMore'
    },
    activeFilterCount() {
      let count = 0
      if (this.filters.customerId) count += 1
      if (this.filters.status !== undefined) count += 1
      if (this.isRoot && this.filters.cashierId) count += 1
      if (this.dateRange && this.dateRange.length === 2) count += 1
      return count
    }
  },
  onShow() {
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    if (!this.initialized) this.initialize()
  },
  onReachBottom() {
    this.loadMore()
  },
  onPageScroll(event) {
    const visible = Number(event.scrollTop || 0) >= 500
    if (visible !== this.showGoTop) this.showGoTop = visible
  },
  onPullDownRefresh() {
    this.queryOrders(true).finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    },
    async initialize() {
      if (this.loading || this.initializing) return
      this.initializing = true
      try {
        const user = await getUserInfo()
        this.user = { ...this.user, ...user }
        updateUser(this.user)
        if (this.isRoot) await this.loadCashiers()
        this.initialized = true
        await this.queryOrders(true)
      } catch (error) {
        this.redirectToLogin()
      } finally {
        this.initializing = false
      }
    },
    async loadCashiers() {
      const users = await listAppUsers()
      const options = (Array.isArray(users) ? users : [])
        .filter((item) => Number(item.status) === 1)
        .map((item) => ({
          label: item.realName || item.userName || `员工${item.id}`,
          value: String(item.id)
        }))
      this.cashierOptions = [{ label: '全部营业员', value: '' }, ...options]
    },
    buildQuery(current) {
      const params = {
        current,
        pageSize: this.pageSize,
        column: 'createTime',
        order: 'desc'
      }
      const customerId = String(this.filters.customerId || '').trim()
      const searchKey = String(this.filters.searchKey || '').trim()
      if (customerId) params.customerId = customerId
      if (searchKey) params.searchKey = searchKey
      if (this.filters.status !== undefined) params.status = this.filters.status
      if (this.isRoot && this.filters.cashierId) params.cashierId = this.filters.cashierId
      if (this.dateRange && this.dateRange.length === 2) {
        params.createTime_begin = this.dateRange[0]
        params.createTime_end = this.dateRange[1]
      }
      return params
    },
    async queryOrders(reset = false, pageNumber, showPageLoading = false) {
      if (this.loading || this.redirecting) return
      const targetPage = reset ? 1 : (pageNumber || this.current)
      this.loading = true
      if (showPageLoading) uni.showLoading({ title: '查询中...', mask: true })
      try {
        const page = await listSaleOrders(this.buildQuery(targetPage))
        const list = page && Array.isArray(page.records) ? page.records : []
        this.records = reset ? list : this.records.concat(list)
        this.current = Number(page && page.current ? page.current : targetPage)
        this.total = Number(page && page.total ? page.total : 0)
      } catch (error) {
        const message = error && error.message ? error.message : '销售单加载失败'
        if (/登录|token|过期/.test(message)) {
          this.redirectToLogin()
        } else {
          uni.showToast({ title: message, icon: 'none' })
        }
      } finally {
        this.loading = false
        if (showPageLoading) uni.hideLoading()
      }
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.queryOrders(false, this.current + 1)
    },
    handleSearch() {
      this.closeMoreFilters()
      this.queryOrders(true, undefined, true)
    },
    handleReset() {
      this.filters = createFilters()
      this.dateRange = []
      this.statusIndex = 0
      this.cashierIndex = 0
      this.closeMoreFilters()
      this.queryOrders(true, undefined, true)
    },
    openMoreFilters() {
      this.$refs.filterPopup && this.$refs.filterPopup.open()
    },
    closeMoreFilters() {
      this.$refs.filterPopup && this.$refs.filterPopup.close()
    },
    applyMoreFilters() {
      this.closeMoreFilters()
      this.queryOrders(true, undefined, true)
    },
    scrollToTop() {
      uni.pageScrollTo({
        scrollTop: 0,
        duration: 300
      })
    },
    onStatusChange(event) {
      this.statusIndex = Number(event.detail.value || 0)
      this.filters.status = this.statusOptions[this.statusIndex].value
    },
    onCashierChange(event) {
      this.cashierIndex = Number(event.detail.value || 0)
      this.filters.cashierId = this.cashierOptions[this.cashierIndex].value
    },
    formatDate(value) {
      return value ? String(value).split(' ')[0] : '-'
    },
    formatAmount(value) {
      const amount = Number(value || 0)
      return `￥${Number.isFinite(amount) ? amount.toFixed(2) : '0.00'}`
    },
    formatQuantity(value) {
      const quantity = Number(value || 0)
      return Number.isInteger(quantity) ? quantity : quantity.toFixed(2)
    },
    isPaidMismatch(order) {
      return Math.abs(Number(order.payableAmount || 0) - Number(order.paidAmount || 0)) >= 0.005
    },
    getGoodsName(item) {
      return item.goodsId_dictText || item.goodsName || item.goodsCode || item.goodsId || '未设置货品'
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 22rpx;
  box-sizing: border-box;
  color: #33303f;
  background: #f5f4fb;
}

.filter-panel,
.order-card {
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
}

.filter-panel { padding: 20rpx 22rpx 16rpx; }
.quick-search-row { display: flex; align-items: center; gap: 14rpx; }
.quick-search-input,
.popup-input,
.popup-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 72rpx;
  padding: 0 20rpx;
  box-sizing: border-box;
  color: #393044;
  font-size: 25rpx;
  background: #fff;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}
.quick-search-input { flex: 1; min-width: 0; height: 58rpx; }
.quick-search-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  flex: 0 0 124rpx;
  height: 58rpx;
  margin: 0;
  padding: 0;
  color: #fff;
  font-size: 25rpx;
  line-height: 58rpx;
  background: #722ed1;
  border-radius: 10rpx;
}
.quick-search-button::after,
.reset-button::after,
.more-filter-button::after,
.popup-actions button::after { border: 0; }
.placeholder { color: #aaa5b5; }
.filter-actions { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; margin-top: 16rpx; }
.reset-button,
.more-filter-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 58rpx;
  margin: 0;
  padding: 0 18rpx;
  font-size: 23rpx;
  line-height: 58rpx;
  border-radius: 8rpx;
}
.reset-button { color: #666; background: #f2f3f5; }
.more-filter-button { position: relative; color: #722ed1; background: #f4efff; }
.filter-count { display: inline-flex; align-items: center; justify-content: center; min-width: 28rpx; height: 28rpx; padding: 0 5rpx; color: #fff; font-size: 17rpx; line-height: 28rpx; background: #722ed1; border-radius: 18rpx; }
.filter-popup { padding: 28rpx 28rpx calc(22rpx + env(safe-area-inset-bottom)); background: #fff; border-radius: 28rpx 28rpx 0 0; }
.popup-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 26rpx; }
.popup-title { color: #30283d; font-size: 32rpx; font-weight: 600; }
.popup-form-item { margin-bottom: 22rpx; }
.popup-label { display: block; margin-bottom: 10rpx; color: #6b6676; font-size: 22rpx; }
.popup-input,
.popup-picker { width: 100%; }
.popup-date-picker { width: 100%; }
.popup-actions { display: flex; gap: 18rpx; margin-top: 30rpx; }
.popup-actions button { flex: 1; height: 78rpx; margin: 0; font-size: 26rpx; line-height: 78rpx; border-radius: 10rpx; }
.popup-cancel-button { color: #666; background: #f2f3f5; }
.popup-confirm-button { color: #fff; background: #722ed1; }
.list-summary { display: flex; align-items: center; justify-content: space-between; padding: 26rpx 6rpx 16rpx; color: #777482; font-size: 23rpx; }
.list-summary text:first-child { color: #454252; font-size: 29rpx; font-weight: 600; }
.order-card { margin-bottom: 18rpx; padding: 22rpx; }
.card-header,
.customer-row,
.card-footer,
.goods-row { display: flex; align-items: center; justify-content: space-between; }
.order-main,
.customer-name { display: flex; align-items: center; min-width: 0; }
.order-no { max-width: 420rpx; overflow: hidden; color: #30283d; font-size: 27rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.order-type { margin-left: 12rpx; padding: 3rpx 10rpx; color: #1677ff; font-size: 19rpx; background: #e8f3ff; border-radius: 8rpx; }
.order-type.return { color: #e5484d; background: #fff0f0; }
.status-tag { padding: 6rpx 14rpx; font-size: 20rpx; border-radius: 20rpx; }
.status-tag.pending { color: #e5484d; background: #fff0f0; }
.status-tag.normal { color: #00a870; background: #e8f8f2; }
.customer-row { margin-top: 18rpx; color: #6f6979; font-size: 23rpx; }
.customer-name { flex: 1; gap: 8rpx; overflow: hidden; }
.customer-name text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.order-date { flex: 0 0 auto; margin-left: 18rpx; color: #9a95a4; }
.amount-grid { display: flex; flex-wrap: wrap; margin-top: 20rpx; padding: 18rpx 0 4rpx; background: #faf9fc; border-radius: 12rpx; }
.amount-item { display: flex; flex: 0 0 25%; flex-direction: column; align-items: center; margin-bottom: 14rpx; }
.profit-item { flex-basis: 100%; align-items: flex-end; padding: 12rpx 22rpx 0 0; box-sizing: border-box; border-top: 1rpx solid #eeeaf4; }
.amount-label { color: #918b99; font-size: 20rpx; }
.amount-value { margin-top: 7rpx; color: #34303d; font-size: 23rpx; font-weight: 600; }
.amount-value.mismatch,
.amount-value.debt,
.amount-value.loss { color: #e5484d; }
.goods-summary { margin-top: 18rpx; padding: 14rpx 16rpx; background: #f7f5fb; border-left: 5rpx solid #722ed1; border-radius: 8rpx; }
.goods-row { min-height: 42rpx; font-size: 22rpx; }
.goods-name { flex: 1; min-width: 0; overflow: hidden; color: #5e5868; text-overflow: ellipsis; white-space: nowrap; }
.goods-quantity { flex: 0 0 auto; margin-left: 18rpx; color: #8c8595; }
.more-goods { display: block; margin-top: 6rpx; color: #722ed1; font-size: 20rpx; }
.note-row { display: flex; margin-top: 16rpx; font-size: 22rpx; }
.note-label { flex: 0 0 62rpx; color: #918b99; }
.note-text { flex: 1; color: #686171; word-break: break-all; }
.card-footer { margin-top: 18rpx; padding-top: 15rpx; color: #9a95a4; font-size: 20rpx; border-top: 1rpx solid #eeeaf4; }
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 500rpx; gap: 18rpx; color: #aaa5b5; font-size: 24rpx; }
.bottom-space { height: 30rpx; }
.go-top-button {
  position: fixed;
  right: 28rpx;
  bottom: calc(42rpx + env(safe-area-inset-bottom));
  z-index: 20;
  width: 88rpx;
  height: 88rpx;
  padding: 10rpx;
  box-sizing: border-box;
  background: rgba(255, 255, 255, .94);
  border: 1rpx solid #e5e0ec;
  border-radius: 50%;
  opacity: 0.9;
  box-shadow: 0 8rpx 22rpx rgba(67, 47, 119, .2);
}

:deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }
:deep(.uni-date-editor--x) { height: 72rpx; }
</style>
