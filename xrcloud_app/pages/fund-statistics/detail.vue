<template>
  <view class="page">
    <view id="fund-detail-top" class="summary-card">
      <view class="title-row">
        <view class="title-main">
          <text class="title">{{ itemName || '资金统计明细' }}</text>
          <text class="subtitle">资金统计明细</text>
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
      <view class="summary-row">
        <text>明细合计</text>
        <text :class="{ negative: totalAmount < 0 }">￥{{ formatAmount(totalAmount) }}</text>
      </view>
    </view>

    <scroll-view
      class="detail-scroll"
      scroll-y
      :show-scrollbar="false"
      :style="{ height: `${scrollHeight}px` }"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      :lower-threshold="120"
      @refresherrefresh="handleRefresh"
      @scrolltolower="loadMore"
    >
      <view v-if="records.length" class="detail-list">
        <view v-for="(record, index) in records" :key="String(record.rowNo || index)" class="detail-card">
          <view class="detail-header">
            <view class="detail-main">
              <text class="detail-summary">{{ record.summary || '-' }}</text>
              <text class="detail-order">{{ record.orderNo || '非单据业务' }}</text>
            </view>
            <text class="detail-date">{{ formatDate(record.businessDate, record.businessTime) }}</text>
          </view>
          <text class="counterparty">{{ record.counterparty || '未设置往来单位' }}</text>
          <view class="detail-footer">
            <text>金额</text>
            <text class="amount" :class="{ negative: amount(record) < 0 }">
              {{ amount(record) < 0 ? '-' : '+' }}￥{{ formatAmount(Math.abs(amount(record))) }}
            </text>
          </view>
        </view>
      </view>
      <view v-else-if="!loading" class="empty-state">
        <uni-icons type="info" color="#b6b0c2" :size="44" />
        <text>暂无资金明细</text>
      </view>
      <view v-if="loading" class="loading-state">
        <uni-icons type="spinner-cycle" color="#722ed1" :size="28" />
        <text>正在加载...</text>
      </view>
      <uni-load-more v-if="records.length || loading" :status="loadStatus" />
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import { getUserInfo, listFundStatisticsDetail } from '../../common/api'

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
      itemKey: '',
      itemName: '',
      result: {},
      startDate: range[0],
      endDate: range[1],
      dateRange: range,
      records: [],
      current: 1,
      pageSize: 50,
      total: 0,
      loading: false,
      refreshing: false,
      initialized: false,
      redirecting: false,
      scrollHeight: 1,
      measureTimer: null
    }
  },
  computed: {
    totalAmount() {
      return this.result.netTotal !== undefined
        ? Number(this.result.netTotal || 0)
        : this.records.reduce((total, item) => total + this.amount(item), 0)
    },
    hasMore() {
      return this.records.length < this.total
    },
    loadStatus() {
      if (this.loading) return 'loading'
      return this.hasMore ? 'more' : 'noMore'
    }
  },
  onLoad(options) {
    this.itemKey = String(options && options.itemKey || '')
    this.itemName = String(options && options.itemName || '')
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
          .select('#fund-detail-top')
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
        this.handleError(error, '资金明细加载失败')
      }
    },
    async loadDetail() {
      if (!this.itemKey || this.loading || this.redirecting) return
      this.loading = true
      try {
        const data = await listFundStatisticsDetail({
          userId: this.user.id || '',
          itemKey: this.itemKey,
          startDate: this.startDate || undefined,
          endDate: this.endDate || undefined,
          current: this.current,
          pageSize: this.pageSize
        })
        const source = data || {}
        const rows = Array.isArray(source.records) ? source.records : []
        this.records = this.current === 1 ? rows : this.records.concat(rows)
        this.current = Number(source.current || this.current)
        this.pageSize = Number(source.pageSize || this.pageSize)
        this.total = Number(source.total || this.records.length)
        this.result = source
        return true
      } catch (error) {
        this.handleError(error, '资金明细加载失败')
        return false
      } finally {
        this.loading = false
        this.scheduleMeasure()
      }
    },
    reloadFirstPage() {
      if (this.loading || this.redirecting) return Promise.resolve()
      this.current = 1
      this.records = []
      return this.loadDetail()
    },
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
      this.startDate = range[0] || ''
      this.endDate = range[1] || ''
      this.current = 1
      this.records = []
      this.loadDetail()
    },
    async loadMore() {
      if (this.loading || !this.hasMore) return
      const previousCurrent = this.current
      this.current += 1
      const loaded = await this.loadDetail()
      if (loaded === false) {
        this.current = previousCurrent
      }
    },
    amount(record) {
      return Number(record && record.income || 0) - Number(record && record.expense || 0)
    },
    formatDate(value, timestamp) {
      if (value) return String(value).slice(0, 10)
      const number = Number(timestamp || 0)
      if (!number) return '-'
      const date = new Date(number)
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    },
    formatAmount(value) {
      const number = Number(value || 0)
      return Number.isFinite(number) ? number.toFixed(2) : '0.00'
    },
    handleRefresh() {
      this.refreshing = true
      this.reloadFirstPage().finally(() => {
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
.title-row, .date-row, .detail-header, .summary-row, .detail-footer { display: flex; align-items: center; }
.title-row, .summary-row, .detail-header, .detail-footer { justify-content: space-between; gap: 12rpx; }
.title-main, .detail-main { display: flex; flex-direction: column; min-width: 0; }
.title { overflow: hidden; color: #30283d; font-size: 29rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.subtitle { margin-top: 6rpx; color: #8b8594; font-size: 21rpx; }
.refresh-button { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 58rpx; margin: 0; padding: 0; background: #f4efff; border: 0; border-radius: 10rpx; }
.refresh-button::after { border: 0; }
.date-row { gap: 12rpx; margin-top: 18rpx; }
.date-label { flex: 0 0 auto; color: #6f6979; font-size: 22rpx; }
.date-row :deep(.uni-date) { flex: 1; min-width: 0; }
.date-row :deep(.uni-date-editor--x) { height: 62rpx; }
.date-row :deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }
.summary-row { margin-top: 18rpx; padding-top: 14rpx; color: #77717e; font-size: 22rpx; border-top: 1rpx solid #f0edf5; }
.summary-row text:last-child { color: #722ed1; font-weight: 600; }
.negative { color: #e5484d !important; }
.detail-scroll { flex: 0 0 auto; min-height: 0; margin-top: 16rpx; }
.detail-list { display: flex; flex-direction: column; gap: 14rpx; }
.detail-card { padding: 18rpx; }
.detail-header { align-items: flex-start; }
.detail-summary { overflow: hidden; color: #30283d; font-size: 25rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.detail-order { margin-top: 6rpx; color: #aaa5b5; font-size: 20rpx; }
.detail-date { flex: 0 0 auto; color: #8b8594; font-size: 21rpx; }
.counterparty { display: block; margin-top: 12rpx; overflow: hidden; color: #625d69; font-size: 22rpx; text-overflow: ellipsis; white-space: nowrap; }
.detail-footer { margin-top: 16rpx; padding-top: 14rpx; color: #918b99; font-size: 21rpx; border-top: 1rpx solid #f0edf5; }
.amount { color: #00a870; font-size: 25rpx; font-weight: 600; }
.empty-state, .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 360rpx; gap: 14rpx; color: #aaa5b5; font-size: 23rpx; }
.bottom-space { height: 24rpx; }
</style>
