<template>
  <view class="page">
    <view id="cashier-statistics-detail-top" class="summary-card">
      <view class="title-row">
        <view class="title-main">
          <text class="title">{{ cashierName || '营业员' }}</text>
          <text class="subtitle">{{ isPeriod ? '营业员销售统计' : '营业员销售明细' }}</text>
        </view>
        <button class="refresh-button" :disabled="loading" @click="loadDetail">
          <uni-icons type="refresh" color="#722ed1" :size="18" />
        </button>
      </view>

      <view class="mode-row">
        <button
          class="mode-button"
          :class="{ active: !isPeriod }"
          :disabled="loading"
          @click="switchMode('detail')"
        >
          明细
        </button>
        <button
          class="mode-button"
          :class="{ active: isPeriod }"
          :disabled="loading"
          @click="switchMode('period')"
        >
          统计
        </button>
      </view>

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

      <view v-if="!isPeriod" class="filter-row">
        <button class="small-button" :class="{ active: groupBy === 'customer' }" @click="changeGroupBy('customer')">
          按客户
        </button>
        <button class="small-button" :class="{ active: groupBy === 'goods' }" @click="changeGroupBy('goods')">
          按货品
        </button>
      </view>

      <view v-else class="filter-row">
        <picker
          class="period-picker"
          :range="statisticsTypeOptions"
          range-key="label"
          :value="statisticsTypeIndex"
          @change="handleStatisticsTypeChange"
        >
          <view class="picker-input">
            <text>{{ statisticsTypeOptions[statisticsTypeIndex].label }}</text>
            <uni-icons type="down" color="#999" :size="16" />
          </view>
        </picker>
      </view>

      <view v-if="isRoot" class="summary-grid">
        <view class="summary-item">
          <text class="summary-label">销售数量</text>
          <text class="summary-value">{{ formatQuantity(totalQuantity) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">销售金额</text>
          <text class="summary-value">￥{{ formatAmount(totalSalesAmount) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">利润金额</text>
          <text class="summary-value" :class="{ negative: totalProfitAmount < 0 }">
            ￥{{ formatAmount(totalProfitAmount) }}
          </text>
        </view>
        <view class="summary-item">
          <text class="summary-label">提成金额</text>
          <text class="summary-value">￥{{ formatAmount(totalCommissionAmount) }}</text>
        </view>
        <view class="summary-item">
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
              <text class="detail-primary">{{ primaryText(record) }}</text>
              <text class="detail-secondary">{{ secondaryText(record) }}</text>
            </view>
            <text class="detail-date">{{ isPeriod ? (record.date || '-') : '' }}</text>
          </view>

          <view v-if="isRoot" class="detail-values" :class="{ period: isPeriod }">
            <view class="value-item">
              <text class="value-label">销售数量</text>
              <text class="value-text">{{ formatQuantity(record.quantity) }}</text>
            </view>
            <view class="value-item">
              <text class="value-label">销售金额</text>
              <text class="value-text">￥{{ formatAmount(record.salesAmount) }}</text>
            </view>
            <view class="value-item">
              <text class="value-label">利润金额</text>
              <text class="value-text" :class="{ negative: Number(record.profitAmount || 0) < 0 }">
                ￥{{ formatAmount(record.profitAmount) }}
              </text>
            </view>
            <view class="value-item">
              <text class="value-label">提成金额</text>
              <text class="value-text">￥{{ formatAmount(record.commissionAmount) }}</text>
            </view>
            <view class="value-item">
              <text class="value-label">利润率</text>
              <text class="value-text">{{ formatRate(record.profitRate) }}</text>
            </view>
          </view>
        </view>
      </view>
      <view v-else-if="loading" class="state">
        <uni-icons type="spinner-cycle" color="#722ed1" :size="28" />
        <text>加载中...</text>
      </view>
      <view v-else class="state">
        <uni-icons type="info" color="#b6b0c2" :size="42" />
        <text>暂无营业员统计数据</text>
      </view>
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import {
  getUserInfo,
  listCashierStatisticsDetail,
  listCashierStatisticsPeriod
} from '../../common/api'

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
      mode: 'detail',
      cashierId: '',
      cashierName: '',
      startDate: range[0],
      endDate: range[1],
      dateRange: range,
      groupBy: 'customer',
      statisticsTypeOptions: [
        { label: '按日统计', value: 'day' },
        { label: '按月统计', value: 'month' },
        { label: '汇总统计', value: 'range' }
      ],
      statisticsTypeIndex: 0,
      records: [],
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
    isPeriod() {
      return this.mode === 'period'
    },
    totalQuantity() {
      return this.records.reduce((total, item) => total + Number(item.quantity || 0), 0)
    },
    totalSalesAmount() {
      return this.records.reduce((total, item) => total + Number(item.salesAmount || 0), 0)
    },
    totalProfitAmount() {
      return this.records.reduce((total, item) => total + Number(item.profitAmount || 0), 0)
    },
    totalCommissionAmount() {
      return this.records.reduce((total, item) => total + Number(item.commissionAmount || 0), 0)
    },
    totalProfitRate() {
      return Math.abs(this.totalSalesAmount) < 0.000001
        ? 0
        : (this.totalProfitAmount * 100) / this.totalSalesAmount
    }
  },
  onLoad(options) {
    const query = options || {}
    this.mode = query.mode === 'period' ? 'period' : 'detail'
    this.cashierId = String(query.cashierId || '')
    this.cashierName = String(query.cashierName || '')
    if (query.startDate) this.startDate = String(query.startDate)
    if (query.endDate) this.endDate = String(query.endDate)
    this.dateRange = [this.startDate, this.endDate]
    const statisticsType = String(query.statisticsType || 'day')
    const index = this.statisticsTypeOptions.findIndex((item) => item.value === statisticsType)
    this.statisticsTypeIndex = index >= 0 ? index : 0
    this.applyNavigationTitle()
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
          .select('#cashier-statistics-detail-top')
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
        if (!this.cashierId && this.user.id !== undefined && this.user.id !== null) {
          this.cashierId = String(this.user.id)
        }
        this.initialized = true
        await this.loadDetail()
      } catch (error) {
        this.handleError(error, '营业员统计加载失败')
      } finally {
        this.initializing = false
        this.scheduleMeasure()
      }
    },
    applyNavigationTitle() {
      uni.setNavigationBarTitle({
        title: this.isPeriod ? '营业员销售统计' : '营业员销售明细'
      })
    },
    buildQuery() {
      const query = {
        userId: this.user.id || '',
        cashierId: this.cashierId || '',
        startDate: this.startDate || undefined,
        endDate: this.endDate || undefined
      }
      if (this.isPeriod) {
        query.statisticsType = this.statisticsTypeOptions[this.statisticsTypeIndex].value
      } else {
        query.groupBy = this.groupBy
      }
      const result = {}
      Object.keys(query).forEach((key) => {
        const value = query[key]
        if (value !== undefined && value !== '') result[key] = value
      })
      return result
    },
    async loadDetail() {
      if (!this.cashierId || this.loading || this.redirecting) return
      this.loading = true
      try {
        const api = this.isPeriod
          ? listCashierStatisticsPeriod
          : listCashierStatisticsDetail
        const data = await api(this.buildQuery())
        this.records = Array.isArray(data) ? data : []
      } catch (error) {
        this.handleError(error, '营业员统计加载失败')
      } finally {
        this.loading = false
        this.scheduleMeasure()
      }
    },
    reloadAndMeasure() {
      return this.loadDetail()
    },
    switchMode(mode) {
      if (mode !== 'detail' && mode !== 'period') return
      if (this.mode === mode) return
      this.mode = mode
      this.records = []
      this.applyNavigationTitle()
      this.loadDetail()
    },
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
      this.startDate = range[0] || ''
      this.endDate = range[1] || ''
      this.loadDetail()
    },
    changeGroupBy(groupBy) {
      if (this.groupBy === groupBy) return
      this.groupBy = groupBy
      if (!this.isPeriod) this.loadDetail()
    },
    handleStatisticsTypeChange(event) {
      this.statisticsTypeIndex = Number(event.detail.value || 0)
      if (this.isPeriod) this.loadDetail()
    },
    primaryText(record) {
      if (this.isPeriod) return record.date || '-'
      return record.groupName || '-'
    },
    secondaryText(record) {
      if (this.isPeriod) return '按当前时间范围统计'
      return record.unit ? `单位：${record.unit}` : '销售汇总'
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

.summary-card,
.detail-card { background: #fff; border: 1rpx solid #ece9f2; border-radius: 16rpx; box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06); }
.summary-card { flex: 0 0 auto; padding: 20rpx 18rpx; }
.title-row,
.date-row,
.filter-row,
.detail-header,
.detail-main,
.mode-row { display: flex; align-items: center; }
.title-row,
.detail-header { justify-content: space-between; gap: 12rpx; }
.title-main,
.detail-main { display: flex; flex-direction: column; min-width: 0; }
.title { overflow: hidden; color: #30283d; font-size: 29rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.subtitle,
.detail-secondary { margin-top: 6rpx; color: #8b8594; font-size: 21rpx; }
.refresh-button { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 58rpx; margin: 0; padding: 0; background: #f4efff; border: 0; border-radius: 10rpx; }
.refresh-button::after,
.mode-button::after,
.small-button::after { border: 0; }
.mode-row,
.filter-row { gap: 12rpx; margin-top: 14rpx; }
.mode-button,
.small-button { height: 56rpx; margin: 0; padding: 0 18rpx; color: #666; font-size: 22rpx; line-height: 56rpx; background: #f2f3f5; border: 0; border-radius: 10rpx; }
.mode-button.active,
.small-button.active { color: #fff; background: #722ed1; }
.date-row { gap: 12rpx; margin-top: 14rpx; }
.date-label { flex: 0 0 auto; color: #6f6979; font-size: 22rpx; }
.date-row :deep(.uni-date),
.period-picker { flex: 1; min-width: 0; }
.date-row :deep(.uni-date-editor--x) { height: 62rpx; }
.date-row :deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }
.picker-input { display: flex; align-items: center; justify-content: space-between; width: 100%; height: 62rpx; padding: 0 16rpx; box-sizing: border-box; overflow: hidden; color: #393044; font-size: 23rpx; background: #fff; border: 1rpx solid #ded9e8; border-radius: 10rpx; }
.summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6rpx; margin-top: 16rpx; padding: 20rpx 6rpx 0; border-top: 1rpx solid #f0edf5; }
.summary-item,
.value-item { min-width: 0; text-align: center; }
.summary-label,
.value-label { display: block; color: #817a89; font-size: 20rpx; }
.summary-value { display: block; margin-top: 8rpx; color: #722ed1; font-size: 24rpx; font-weight: 600; }
.negative { color: #e5484d !important; }
.detail-scroll { flex: 0 0 auto; min-height: 0; margin-top: 16rpx; }
.detail-list { display: flex; flex-direction: column; gap: 14rpx; }
.detail-card { padding: 18rpx; }
.detail-date { flex: 0 0 auto; color: #8b8594; font-size: 21rpx; }
.detail-primary { overflow: hidden; color: #30283d; font-size: 27rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.detail-values { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14rpx 4rpx; margin-top: 16rpx; padding-top: 16rpx; border-top: 1rpx solid #f0edf5; }
.detail-values.period { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.value-text { display: block; margin-top: 6rpx; color: #4d4858; font-size: 22rpx; font-weight: 600; }
.state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 360rpx; gap: 14rpx; color: #aaa5b5; font-size: 23rpx; }
.bottom-space { height: 24rpx; }
</style>