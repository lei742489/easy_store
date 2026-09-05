<template>
  <view class="page">
    <view id="cashier-statistics-top" class="top-area">
      <view class="filter-panel">
        <view class="date-row">
          <text class="date-label">日期范围</text>
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
          <button class="search-button" :disabled="loading" @click="handleSearch">
            <uni-icons type="search" color="#fff" :size="18" />
            <text>查询</text>
          </button>
          <button class="reset-button" :disabled="loading" @click="handleReset">
            <uni-icons type="refresh" color="#666" :size="18" />
            <text>重置</text>
          </button>
        </view>
      </view>

      <view class="summary-card">
        <view class="summary-item">
          <text class="summary-label">销售数量</text>
          <text class="summary-value">{{ formatQuantity(summary.quantityTotal) }}</text>
        </view>
        <view v-if="isRoot" class="summary-item">
          <text class="summary-label">销售金额</text>
          <text class="summary-value">￥{{ formatAmount(summary.salesTotal) }}</text>
        </view>
        <view v-if="isRoot" class="summary-item">
          <text class="summary-label">利润金额</text>
          <text class="summary-value" :class="{ negative: Number(summary.profitTotal || 0) < 0 }">
            ￥{{ formatAmount(summary.profitTotal) }}
          </text>
        </view>
        <view class="summary-item">
          <text class="summary-label">提成金额</text>
          <text class="summary-value">￥{{ formatAmount(summary.commissionTotal) }}</text>
        </view>
        <view v-if="isRoot" class="summary-item">
          <text class="summary-label">利润率</text>
          <text class="summary-value">{{ formatRate(summary.profitRateTotal) }}</text>
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
          :key="String(record.cashierId || record.rowNo)"
          class="record-card"
        >
          <view class="record-header">
            <view class="record-main">
              <text class="record-name">{{ record.cashierName || '-' }}</text>
              <text v-if="isRoot" class="record-rate">{{ formatRate(record.profitRate) }}</text>
            </view>
            <uni-icons type="right" color="#aaa5b5" :size="18" />
          </view>
          <view class="amount-grid">
            <view class="amount-item">
              <text class="amount-label">销售数量</text>
              <text class="amount-value">{{ formatQuantity(record.quantity) }}</text>
            </view>
            <view v-if="isRoot" class="amount-item">
              <text class="amount-label">销售金额</text>
              <text class="amount-value">￥{{ formatAmount(record.salesAmount) }}</text>
            </view>
            <view v-if="isRoot" class="amount-item">
              <text class="amount-label">利润金额</text>
              <text class="amount-value" :class="{ negative: Number(record.profitAmount || 0) < 0 }">
                ￥{{ formatAmount(record.profitAmount) }}
              </text>
            </view>
            <view class="amount-item">
              <text class="amount-label">提成金额</text>
              <text class="amount-value">￥{{ formatAmount(record.commissionAmount) }}</text>
            </view>
          </view>
          <view class="record-footer">
            <button class="footer-button ghost" @click.stop="openPeriod(record)">
              <uni-icons type="calendar" color="#722ed1" :size="16" />
              <text>统计</text>
            </button>
            <button class="footer-button primary" @click.stop="openDetail(record)">
              <uni-icons type="list" color="#fff" :size="16" />
              <text>明细</text>
            </button>
          </view>
        </view>
      </view>
      <view v-else-if="!loading" class="empty-state">
        <uni-icons type="info" color="#b6b0c2" :size="44" />
        <text>暂无营业员统计数据</text>
      </view>
      <view v-if="loading && !records.length" class="loading-state">
        <uni-icons type="spinner-cycle" color="#722ed1" :size="28" />
        <text>加载中...</text>
      </view>
      <uni-load-more v-if="records.length || loading" :status="loadStatus" />
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import { getUserInfo, listCashierStatistics } from '../../common/api'

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
  data() {
    const range = monthRange()
    return {
      user: getUser() || {},
      startDate: range[0],
      endDate: range[1],
      dateRange: range,
      summary: {},
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
  onLoad() {
    this.initialize()
  },
  onReady() {
    this.scheduleMeasure()
  },
  onShow() {
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    this.scheduleMeasure()
    if (this.initialized) this.reloadFirstPage()
  },
  onHide() {
    this.clearMeasureTimer()
  },
  onUnload() {
    this.clearMeasureTimer()
  },
  onPullDownRefresh() {
    this.reloadFirstPage().finally(() => uni.stopPullDownRefresh())
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
          .select('#cashier-statistics-top')
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
        await this.loadStatistics(true)
      } catch (error) {
        this.handleError(error, '营业员统计初始化失败')
      } finally {
        this.initializing = false
        this.scheduleMeasure()
      }
    },
    buildQuery(page) {
      return {
        userId: this.user.id || '',
        startDate: this.startDate || undefined,
        endDate: this.endDate || undefined,
        current: page,
        pageSize: this.pageSize
      }
    },
    async loadStatistics(reset = false) {
      if (this.loading || this.redirecting) return false
      this.loading = true
      const page = reset ? 1 : this.current
      try {
        const data = await listCashierStatistics(this.buildQuery(page))
        const source = data || {}
        const rows = Array.isArray(source.records) ? source.records : []
        this.records = reset ? rows : this.records.concat(rows)
        this.current = Number(source.current || page)
        this.pageSize = Number(source.pageSize || this.pageSize)
        this.total = Number(source.total || this.records.length)
        this.summary = source
        return true
      } catch (error) {
        this.handleError(error, '营业员统计加载失败')
        return false
      } finally {
        this.loading = false
        this.scheduleMeasure()
      }
    },
    reloadFirstPage() {
      if (this.loading || this.redirecting) return Promise.resolve(false)
      this.current = 1
      this.records = []
      return this.loadStatistics(true)
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      const previousCurrent = this.current
      this.current += 1
      this.loadStatistics(false).then((loaded) => {
        if (loaded === false) this.current = previousCurrent
      })
    },
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
      this.startDate = range[0] || ''
      this.endDate = range[1] || ''
    },
    handleSearch() {
      this.reloadFirstPage()
    },
    handleReset() {
      const range = monthRange()
      this.startDate = range[0]
      this.endDate = range[1]
      this.dateRange = range
      this.reloadFirstPage()
    },
    handleRefresh() {
      this.refreshing = true
      this.reloadFirstPage().finally(() => {
        this.refreshing = false
      })
    },
    openDetail(record) {
      if (!record || record.cashierId === undefined || record.cashierId === null) return
      const query = [
        'mode=detail',
        `cashierId=${encodeURIComponent(record.cashierId)}`,
        `cashierName=${encodeURIComponent(record.cashierName || '')}`,
        `startDate=${encodeURIComponent(this.startDate || '')}`,
        `endDate=${encodeURIComponent(this.endDate || '')}`
      ].join('&')
      uni.navigateTo({ url: `/pages/cashier-statistics/detail?${query}` })
    },
    openPeriod(record) {
      if (!record || record.cashierId === undefined || record.cashierId === null) return
      const query = [
        'mode=period',
        `cashierId=${encodeURIComponent(record.cashierId)}`,
        `cashierName=${encodeURIComponent(record.cashierName || '')}`,
        `startDate=${encodeURIComponent(this.startDate || '')}`,
        `endDate=${encodeURIComponent(this.endDate || '')}`
      ].join('&')
      uni.navigateTo({ url: `/pages/cashier-statistics/detail?${query}` })
    },
    formatQuantity(value) {
      const number = Number(value || 0)
      if (!Number.isFinite(number)) return '0'
      return Number.isInteger(number) ? String(number) : number.toFixed(2)
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
.page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 18rpx 14rpx 0;
  box-sizing: border-box;
  color: #34313d;
  background: #f5f4fb;
  overflow: hidden;
}

.top-area { flex: 0 0 auto; }

.filter-panel,
.summary-card,
.record-card {
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
}

.filter-panel { padding: 18rpx; }
.date-row,
.action-row,
.record-header,
.record-main,
.record-footer { display: flex; align-items: center; }
.date-row { gap: 12rpx; }
.date-label { flex: 0 0 auto; color: #6f6979; font-size: 22rpx; }
.date-row :deep(.uni-date) { flex: 1; min-width: 0; }
.date-row :deep(.uni-date-editor--x) { height: 62rpx; }
.date-row :deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }
.action-row { justify-content: flex-end; gap: 12rpx; margin-top: 14rpx; }
.search-button,
.reset-button,
.footer-button { display: flex; align-items: center; justify-content: center; gap: 6rpx; border: 0; }
.search-button,
.reset-button { flex: 0 0 124rpx; height: 58rpx; margin: 0; padding: 0; font-size: 23rpx; line-height: 58rpx; border-radius: 10rpx; }
.search-button { color: #fff; background: #722ed1; }
.reset-button { color: #666; background: #f2f3f5; }
.search-button::after,
.reset-button::after,
.footer-button::after { border: 0; }
.summary-card { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6rpx; margin-top: 16rpx; padding: 20rpx 6rpx; }
.summary-item,
.amount-item { min-width: 0; text-align: center; }
.summary-label,
.amount-label { display: block; color: #817a89; font-size: 20rpx; }
.summary-value { display: block; margin-top: 8rpx; color: #722ed1; font-size: 24rpx; font-weight: 600; }
.negative { color: #e5484d !important; }
.statistics-scroll { flex: 0 0 auto; min-height: 240px; margin-top: 16rpx; }
.record-list { display: flex; flex-direction: column; gap: 14rpx; }
.record-card { padding: 20rpx; }
.record-header { justify-content: space-between; gap: 12rpx; }
.record-main { flex: 1; min-width: 0; gap: 12rpx; }
.record-name { overflow: hidden; color: #30283d; font-size: 28rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.record-rate { flex: 0 0 auto; padding: 4rpx 10rpx; color: #722ed1; font-size: 19rpx; background: #f4efff; border-radius: 8rpx; }
.amount-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14rpx 8rpx; margin-top: 18rpx; padding: 16rpx 4rpx; background: #faf9fc; border-radius: 10rpx; }
.amount-value { display: block; margin-top: 7rpx; color: #4d4858; font-size: 23rpx; font-weight: 600; }
.record-footer { justify-content: flex-end; gap: 12rpx; margin-top: 14rpx; }
.footer-button { min-width: 132rpx; height: 56rpx; margin: 0; padding: 0 16rpx; font-size: 22rpx; line-height: 56rpx; border-radius: 10rpx; }
.footer-button.ghost { color: #722ed1; background: #f4efff; }
.footer-button.primary { color: #fff; background: #722ed1; }
.empty-state,
.loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 360rpx; gap: 14rpx; color: #aaa5b5; font-size: 23rpx; }
.bottom-space { height: 24rpx; }
</style>