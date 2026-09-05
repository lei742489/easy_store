<template>
  <view class="page">
    <view id="profit-statistics-top" class="top-area">
      <view class="filter-panel">
        <view class="filter-row">
          <customer-autocomplete
            v-model="customerName"
            placeholder="全部客户"
            @select="handleCustomerChange"
            @select-item="handleCustomerSelected"
          />
        </view>
        <view class="date-row">
          <text class="date-label">日期</text>
          <uni-datetime-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clear-icon="true"
            @change="handleDateChange"
          />
        </view>
        <view class="action-row">
          <button class="search-button" :disabled="loading" @click="queryStatistics(true)">
            <uni-icons type="search" color="#fff" :size="18" />
            <text>统计</text>
          </button>
          <button class="reset-button" :disabled="loading" @click="handleReset">
            <uni-icons type="refresh" color="#666" :size="18" />
            <text>重置</text>
          </button>
        </view>
      </view>

      <view class="summary-card">
        <view class="summary-item">
          <text class="summary-label">折后金额</text>
          <text class="summary-value">￥{{ formatAmount(result.discountedTotal) }}</text>
        </view>
        <view v-if="isRoot" class="summary-item">
          <text class="summary-label">成本金额</text>
          <text class="summary-value">￥{{ formatAmount(result.costTotal) }}</text>
        </view>
        <view v-if="isRoot" class="summary-item">
          <text class="summary-label">利润金额</text>
          <text class="summary-value profit" :class="{ negative: Number(result.profitTotal || 0) < 0 }">
            ￥{{ formatAmount(result.profitTotal) }}
          </text>
        </view>
        <view v-if="isRoot" class="summary-item">
          <text class="summary-label">利润率</text>
          <text class="summary-value">{{ formatRate(result.profitRateTotal) }}</text>
        </view>
      </view>
    </view>

    <scroll-view
      class="statistics-scroll"
      scroll-y
      :show-scrollbar="false"
      :style="{ height: `${scrollHeight}px` }"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      :lower-threshold="120"
      @refresherrefresh="handleRefresh"
      @scrolltolower="loadMore"
    >
      <view v-if="records.length" class="record-list">
        <view
          v-for="record in records"
          :key="String(record.customerId || record.rowNo)"
          class="record-card"
          @click="openDetail(record)"
        >
          <view class="record-header">
            <view class="record-main">
              <text class="record-name">{{ record.customerName || '未设置往来单位' }}</text>
              <text class="record-index">#{{ record.rowNo || '-' }}</text>
            </view>
            <uni-icons type="right" color="#aaa5b5" :size="18" />
          </view>
          <view class="amount-grid">
            <view class="amount-item">
              <text class="amount-label">折后金额</text>
              <text class="amount-value">￥{{ formatAmount(record.discountedAmount) }}</text>
            </view>
            <view v-if="isRoot" class="amount-item">
              <text class="amount-label">成本金额</text>
              <text class="amount-value">￥{{ formatAmount(record.costAmount) }}</text>
            </view>
            <view v-if="isRoot" class="amount-item">
              <text class="amount-label">利润金额</text>
              <text class="amount-value profit" :class="{ negative: Number(record.profitAmount || 0) < 0 }">
                ￥{{ formatAmount(record.profitAmount) }}
              </text>
            </view>
            <view v-if="isRoot" class="amount-item">
              <text class="amount-label">利润率</text>
              <text class="amount-value">{{ formatRate(record.profitRate) }}</text>
            </view>
          </view>
          <view class="record-footer">
            <text>点击查看利润明细</text>
          </view>
        </view>
      </view>
      <view v-else-if="!loading" class="empty-state">
        <uni-icons type="info" color="#b6b0c2" :size="44" />
        <text>暂无利润统计数据</text>
      </view>
      <uni-load-more v-if="records.length || loading" :status="loadStatus" />
      <view v-if="loading && !records.length" class="loading-state">
        <uni-icons type="spinner-cycle" color="#722ed1" :size="28" />
        <text>正在加载...</text>
      </view>
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import { getUserInfo, listProfitStatistics } from '../../common/api'
import CustomerAutocomplete from '../../components/customer-autocomplete.vue'

const pad = (value) => String(value).padStart(2, '0')
const monthRange = () => {
  const date = new Date()
  const end = new Date(date.getFullYear(), date.getMonth() + 1, 0)
  return [
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-01`,
    `${end.getFullYear()}-${pad(end.getMonth() + 1)}-${pad(end.getDate())}`
  ]
}

export default {
  components: { CustomerAutocomplete },
  data() {
    const range = monthRange()
    return {
      user: getUser() || {},
      customerId: '',
      customerName: '',
      startDate: range[0],
      endDate: range[1],
      dateRange: range,
      result: {},
      records: [],
      current: 1,
      pageSize: 20,
      total: 0,
      loading: false,
      refreshing: false,
      initializing: false,
      initialized: false,
      redirecting: false,
      scrollHeight: 1,
      measureTimer: null
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
    }
  },
  onReady() {
    this.scheduleMeasure()
  },
  onLoad() {
    this.initialize()
  },
  onShow() {
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    this.scheduleMeasure()
    if (this.initialized) this.queryStatistics(true)
  },
  onHide() {
    this.clearMeasureTimer()
  },
  onUnload() {
    this.clearMeasureTimer()
  },
  onPullDownRefresh() {
    this.queryStatistics(true).finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    clearMeasureTimer() {
      if (this.measureTimer) {
        clearTimeout(this.measureTimer)
        this.measureTimer = null
      }
    },
    scheduleMeasure() {
      this.clearMeasureTimer()
      this.measureTimer = setTimeout(() => {
        this.measureTimer = null
        this.measureScroll()
      }, 0)
    },
    measureScroll() {
      if (this.redirecting) return
      const systemInfo = uni.getSystemInfoSync()
      try {
        uni.createSelectorQuery()
          .select('#profit-statistics-top')
          .boundingClientRect()
          .exec((rects) => {
            const rect = rects && rects[0]
            if (!rect || !rect.height) return
            const bottom = Number(rect.bottom || rect.top + rect.height)
            const margin = uni.upx2px ? uni.upx2px(16) : 8
            this.scrollHeight = Math.max(
              240,
              Math.floor(Number(systemInfo.windowHeight || 0) - bottom - margin)
            )
          })
      } catch (error) {
        this.scrollHeight = Math.max(
          240,
          Math.floor(Number(systemInfo.windowHeight || 0) * 0.65)
        )
      }
    },
    async initialize() {
      if (this.initializing || this.redirecting) return
      this.initializing = true
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...(currentUser || {}) }
        updateUser(this.user)
        this.initialized = true
        await this.queryStatistics(true)
      } catch (error) {
        this.handleError(error, '利润统计加载失败')
      } finally {
        this.initializing = false
        this.scheduleMeasure()
      }
    },
    buildQuery(page) {
      return {
        userId: this.user.id || '',
        customerId: this.customerId || undefined,
        startDate: this.startDate || undefined,
        endDate: this.endDate || undefined,
        current: page,
        pageSize: this.pageSize
      }
    },
    async queryStatistics(reset = false) {
      if (this.loading || this.redirecting) return
      this.loading = true
      const page = reset ? 1 : this.current
      try {
        const data = await listProfitStatistics(this.buildQuery(page))
        const source = data || {}
        const rows = Array.isArray(source.records) ? source.records : []
        this.records = reset ? rows : this.records.concat(rows)
        this.current = Number(source.current || page)
        this.total = Number(source.total || this.records.length)
        this.result = source
      } catch (error) {
        this.handleError(error, '利润统计加载失败')
      } finally {
        this.loading = false
        this.scheduleMeasure()
      }
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.current += 1
      this.queryStatistics(false)
    },
    handleCustomerChange(value) {
      if (!value) {
        this.customerId = ''
        this.customerName = ''
      }
    },
    handleCustomerSelected(payload) {
      if (!payload) {
        this.customerId = ''
        this.customerName = ''
        return
      }
      this.customerId = payload.id === undefined || payload.id === null
        ? ''
        : String(payload.id)
      this.customerName = String(payload.name || '').trim()
    },
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
      this.startDate = range[0] || ''
      this.endDate = range[1] || ''
    },
    handleReset() {
      const range = monthRange()
      this.customerId = ''
      this.customerName = ''
      this.startDate = range[0]
      this.endDate = range[1]
      this.dateRange = range
      this.queryStatistics(true)
    },
    handleRefresh() {
      this.refreshing = true
      this.queryStatistics(true).finally(() => {
        this.refreshing = false
      })
    },
    openDetail(record) {
      if (!record || record.customerId === undefined || record.customerId === null) return
      const query = [
        `customerId=${encodeURIComponent(record.customerId)}`,
        `customerName=${encodeURIComponent(record.customerName || '')}`,
        `userId=${encodeURIComponent(this.user.id || '')}`,
        `startDate=${encodeURIComponent(this.startDate || '')}`,
        `endDate=${encodeURIComponent(this.endDate || '')}`
      ].join('&')
      uni.navigateTo({ url: `/pages/profit-statistics/detail?${query}` })
    },
    formatAmount(value) {
      const number = Number(value || 0)
      return Number.isFinite(number) ? number.toFixed(2) : '0.00'
    },
    formatRate(value) {
      const number = Number(value || 0)
      return Number.isFinite(number) ? `${number.toFixed(2)}%` : '0.00%'
    },
    handleError(error, fallback) {
      const message = error && error.message ? error.message : fallback
      if (/登录|token|过期/i.test(message)) {
        this.redirectToLogin()
        return
      }
      uni.showToast({ title: message, icon: 'none' })
    },
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      this.clearMeasureTimer()
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
.page { display: flex; flex-direction: column; height: 100%; min-height: 0; padding: 18rpx 14rpx 0; box-sizing: border-box; color: #34313d; background: #f5f4fb; overflow: hidden; }
.top-area { flex: 0 0 auto; }
.filter-panel, .summary-card, .record-card { background: #fff; border: 1rpx solid #ece9f2; border-radius: 16rpx; box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06); }
.filter-panel { padding: 18rpx; }
.filter-row :deep(.customer-select), .filter-row :deep(.input-wrap) { width: 100%; min-width: 0; }
.filter-row :deep(.input-wrap) { height: 62rpx; }
.date-row, .action-row, .record-header, .record-main, .record-footer { display: flex; align-items: center; }
.date-row { gap: 12rpx; margin-top: 14rpx; }
.date-label { flex: 0 0 auto; color: #6f6979; font-size: 22rpx; }
.date-row :deep(.uni-date) { flex: 1; min-width: 0; }
.date-row :deep(.uni-date-editor--x) { height: 62rpx; }
.date-row :deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }
.action-row { justify-content: flex-end; gap: 12rpx; margin-top: 14rpx; }
.search-button, .reset-button { display: flex; align-items: center; justify-content: center; gap: 6rpx; flex: 0 0 124rpx; height: 58rpx; margin: 0; padding: 0; font-size: 23rpx; line-height: 58rpx; border: 0; border-radius: 10rpx; }
.search-button { color: #fff; background: #722ed1; }
.reset-button { color: #666; background: #f2f3f5; }
.search-button::after, .reset-button::after { border: 0; }
.summary-card { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 4rpx; margin-top: 16rpx; padding: 20rpx 4rpx; }
.summary-item, .amount-item { min-width: 0; text-align: center; }
.summary-label, .amount-label { display: block; color: #817a89; font-size: 19rpx; }
.summary-value { display: block; margin-top: 8rpx; color: #722ed1; font-size: 22rpx; font-weight: 600; }
.profit { color: #00a870 !important; }
.negative { color: #e5484d !important; }
.statistics-scroll { flex: 0 0 auto; min-height: 240px; margin-top: 16rpx; }
.record-list { display: flex; flex-direction: column; gap: 14rpx; }
.record-card { padding: 20rpx; }
.record-header { justify-content: space-between; gap: 12rpx; }
.record-main { flex: 1; min-width: 0; gap: 12rpx; }
.record-name { overflow: hidden; color: #30283d; font-size: 28rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.record-index { flex: 0 0 auto; color: #aaa5b5; font-size: 20rpx; }
.amount-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14rpx 8rpx; margin-top: 18rpx; padding: 16rpx 4rpx; background: #faf9fc; border-radius: 10rpx; }
.amount-value { display: block; margin-top: 7rpx; color: #4d4858; font-size: 23rpx; font-weight: 600; }
.record-footer { justify-content: flex-end; margin-top: 14rpx; padding-top: 12rpx; color: #9a95a4; font-size: 20rpx; border-top: 1rpx solid #eeeaf4; }
.empty-state, .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 360rpx; gap: 14rpx; color: #aaa5b5; font-size: 23rpx; }
.bottom-space { height: 24rpx; }
</style>
