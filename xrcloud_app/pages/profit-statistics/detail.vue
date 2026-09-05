<template>
  <view class="page">
    <view id="profit-detail-top" class="summary-card">
      <view class="title-row">
        <view class="title-main">
          <text class="title">{{ customerName || '利润明细' }}</text>
          <text class="subtitle">利润统计明细</text>
        </view>
        <button class="refresh-button" :disabled="loading" @click="loadDetail">
          <uni-icons type="refresh" color="#722ed1" :size="18" />
        </button>
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
      <view class="summary-grid">
        <view class="summary-item">
          <text class="summary-label">销售数量</text>
          <text class="summary-value">{{ formatQuantity(totalQuantity) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">折后金额</text>
          <text class="summary-value">￥{{ formatAmount(totalDiscountedAmount) }}</text>
        </view>
        <view v-if="isRoot" class="summary-item">
          <text class="summary-label">成本金额</text>
          <text class="summary-value">￥{{ formatAmount(totalCostAmount) }}</text>
        </view>
        <view v-if="isRoot" class="summary-item">
          <text class="summary-label">利润金额</text>
          <text class="summary-value profit" :class="{ negative: totalProfitAmount < 0 }">
            ￥{{ formatAmount(totalProfitAmount) }}
          </text>
        </view>
        <view v-if="isRoot" class="summary-item">
          <text class="summary-label">利润率</text>
          <text class="summary-value">{{ formatRate(totalProfitRate) }}</text>
        </view>
      </view>
    </view>

    <scroll-view
      class="detail-scroll"
      scroll-y
      :show-scrollbar="false"
      :style="{ height: `${scrollHeight}px` }"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="handleRefresh"
    >
      <view v-if="records.length" class="detail-list">
        <view
          v-for="(record, index) in records"
          :key="String(record.rowNo || index)"
          class="detail-card"
        >
          <view class="detail-header">
            <view class="detail-main">
              <text class="goods-name">{{ record.goodsName || '-' }}</text>
              <text class="goods-unit">单位：{{ record.unit || '-' }}</text>
            </view>
            <text class="detail-date">{{ record.businessDate || '-' }}</text>
          </view>
          <view class="metric-grid">
            <view class="metric-item">
              <text class="metric-label">数量</text>
              <text class="metric-value">{{ formatQuantity(record.quantity) }}</text>
            </view>
            <view class="metric-item">
              <text class="metric-label">折后单价</text>
              <text class="metric-value">￥{{ formatAmount(record.discountedUnitPrice) }}</text>
            </view>
            <view class="metric-item">
              <text class="metric-label">折后金额</text>
              <text class="metric-value">￥{{ formatAmount(record.discountedAmount) }}</text>
            </view>
            <view v-if="isRoot" class="metric-item">
              <text class="metric-label">成本金额</text>
              <text class="metric-value">￥{{ formatAmount(record.costAmount) }}</text>
            </view>
            <view v-if="isRoot" class="metric-item">
              <text class="metric-label">利润金额</text>
              <text class="metric-value profit" :class="{ negative: Number(record.profitAmount || 0) < 0 }">
                ￥{{ formatAmount(record.profitAmount) }}
              </text>
            </view>
            <view v-if="isRoot" class="metric-item">
              <text class="metric-label">利润率</text>
              <text class="metric-value">{{ formatRate(record.profitRate) }}</text>
            </view>
          </view>
        </view>
      </view>
      <view v-else-if="!loading" class="empty-state">
        <uni-icons type="info" color="#b6b0c2" :size="44" />
        <text>暂无利润明细</text>
      </view>
      <view v-if="loading" class="loading-state">
        <uni-icons type="spinner-cycle" color="#722ed1" :size="28" />
        <text>正在加载...</text>
      </view>
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import { getUserInfo, listProfitStatisticsDetail } from '../../common/api'

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
      customerId: '',
      customerName: '',
      startDate: range[0],
      endDate: range[1],
      dateRange: range,
      records: [],
      loading: false,
      refreshing: false,
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
    totalQuantity() {
      return this.records.reduce((total, item) => total + Number(item.quantity || 0), 0)
    },
    totalDiscountedAmount() {
      return this.records.reduce((total, item) => total + Number(item.discountedAmount || 0), 0)
    },
    totalCostAmount() {
      return this.records.reduce((total, item) => total + Number(item.costAmount || 0), 0)
    },
    totalProfitAmount() {
      return this.records.reduce((total, item) => total + Number(item.profitAmount || 0), 0)
    },
    totalProfitRate() {
      return Math.abs(this.totalDiscountedAmount) < 0.000001
        ? 0
        : this.totalProfitAmount * 100 / this.totalDiscountedAmount
    }
  },
  onLoad(options) {
    this.customerId = String(options && options.customerId || '')
    this.customerName = String(options && options.customerName || '')
    if (options && options.startDate) this.startDate = String(options.startDate)
    if (options && options.endDate) this.endDate = String(options.endDate)
    this.dateRange = [this.startDate, this.endDate]
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
    if (this.initialized) this.loadDetail()
  },
  onHide() {
    this.clearMeasureTimer()
  },
  onUnload() {
    this.clearMeasureTimer()
  },
  onPullDownRefresh() {
    this.loadDetail().finally(() => uni.stopPullDownRefresh())
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
          .select('#profit-detail-top')
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
      if (this.initialized || this.redirecting) return
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...(currentUser || {}) }
        updateUser(this.user)
        this.initialized = true
        await this.loadDetail()
      } catch (error) {
        this.handleError(error, '利润明细加载失败')
      }
    },
    async loadDetail() {
      if (!this.customerId || this.loading || this.redirecting) return
      this.loading = true
      try {
        const data = await listProfitStatisticsDetail({
          userId: this.user.id || '',
          customerId: this.customerId,
          startDate: this.startDate || undefined,
          endDate: this.endDate || undefined
        })
        this.records = Array.isArray(data) ? data : []
      } catch (error) {
        this.handleError(error, '利润明细加载失败')
      } finally {
        this.loading = false
        this.scheduleMeasure()
      }
    },
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
      this.startDate = range[0] || ''
      this.endDate = range[1] || ''
      this.loadDetail()
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
    handleRefresh() {
      this.refreshing = true
      this.loadDetail().finally(() => {
        this.refreshing = false
      })
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
.summary-card, .detail-card { background: #fff; border: 1rpx solid #ece9f2; border-radius: 16rpx; box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06); }
.summary-card { flex: 0 0 auto; padding: 20rpx 18rpx; }
.title-row, .date-row, .detail-header { display: flex; align-items: center; }
.title-row, .detail-header { justify-content: space-between; gap: 12rpx; }
.title-main, .detail-main { display: flex; flex-direction: column; min-width: 0; }
.title { overflow: hidden; color: #30283d; font-size: 29rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.subtitle, .goods-unit { margin-top: 6rpx; color: #8b8594; font-size: 21rpx; }
.refresh-button { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 58rpx; margin: 0; padding: 0; background: #f4efff; border: 0; border-radius: 10rpx; }
.refresh-button::after { border: 0; }
.date-row { gap: 12rpx; margin-top: 18rpx; }
.date-label { flex: 0 0 auto; color: #6f6979; font-size: 22rpx; }
.date-row :deep(.uni-date) { flex: 1; min-width: 0; }
.date-row :deep(.uni-date-editor--x) { height: 62rpx; }
.date-row :deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }
.summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14rpx 8rpx; margin-top: 20rpx; padding-top: 18rpx; border-top: 1rpx solid #f0edf5; }
.summary-item, .metric-item { min-width: 0; text-align: center; }
.summary-label, .metric-label { display: block; color: #817a89; font-size: 20rpx; }
.summary-value { display: block; margin-top: 7rpx; color: #722ed1; font-size: 23rpx; font-weight: 600; }
.profit { color: #00a870 !important; }
.negative { color: #e5484d !important; }
.detail-scroll { flex: 0 0 auto; min-height: 0; margin-top: 16rpx; }
.detail-list { display: flex; flex-direction: column; gap: 14rpx; }
.detail-card { padding: 18rpx; }
.detail-header { align-items: flex-start; }
.goods-name { overflow: hidden; color: #30283d; font-size: 27rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.detail-date { flex: 0 0 auto; color: #8b8594; font-size: 21rpx; }
.metric-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14rpx 4rpx; margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid #f0edf5; }
.metric-value { display: block; margin-top: 6rpx; color: #4d4858; font-size: 22rpx; font-weight: 600; }
.empty-state, .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 360rpx; gap: 14rpx; color: #aaa5b5; font-size: 23rpx; }
.bottom-space { height: 24rpx; }
</style>
