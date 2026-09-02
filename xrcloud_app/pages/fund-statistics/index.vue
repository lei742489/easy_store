<template>
  <view class="page">
    <view id="fund-statistics-top" class="top-area">
      <view class="filter-panel">
        <view class="filter-row">
          <picker
            class="type-picker-wrap"
            :range="itemTypeOptions"
            range-key="label"
            :value="itemTypeIndex"
            @change="handleItemTypeChange"
          >
            <view class="picker-input">
              <text>{{ itemTypeOptions[itemTypeIndex].label }}</text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
          <picker
            class="item-picker-wrap"
            :range="itemOptions"
            range-key="label"
            :value="itemIndex"
            @change="handleItemChange"
          >
            <view class="picker-input">
              <text :class="{ placeholder: itemIndex === 0 }">
                {{ itemOptions[itemIndex].label }}
              </text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
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
          <button class="search-button" :disabled="loading" @click="queryStatistics">
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
          <text class="summary-label">收入合计</text>
          <text class="summary-value income">￥{{ formatAmount(result.incomeTotal) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">支出合计</text>
          <text class="summary-value expense">￥{{ formatAmount(result.expenseTotal) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">收支净额</text>
          <text class="summary-value" :class="{ negative: Number(result.netTotal || 0) < 0 }">
            ￥{{ formatAmount(result.netTotal) }}
          </text>
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
      @refresherrefresh="handleRefresh"
    >
      <view v-if="records.length" class="record-list">
        <view
          v-for="record in records"
          :key="String(record.itemKey || record.rowNo)"
          class="record-card"
          @click="openDetail(record)"
        >
          <view class="record-header">
            <view class="record-main">
              <text class="record-name">{{ record.itemName || '-' }}</text>
              <text class="record-type">{{ itemTypeText(record.itemType) }}</text>
            </view>
            <uni-icons type="right" color="#aaa5b5" :size="18" />
          </view>
          <view class="amount-grid">
            <view class="amount-item">
              <text class="amount-label">收入</text>
              <text class="amount-value income">￥{{ formatAmount(record.income) }}</text>
            </view>
            <view class="amount-item">
              <text class="amount-label">支出</text>
              <text class="amount-value expense">￥{{ formatAmount(record.expense) }}</text>
            </view>
            <view class="amount-item">
              <text class="amount-label">净额</text>
              <text class="amount-value" :class="{ negative: netAmount(record) < 0 }">
                ￥{{ formatAmount(netAmount(record)) }}
              </text>
            </view>
          </view>
          <view class="record-footer">
            <text>点击查看明细</text>
          </view>
        </view>
      </view>
      <view v-else-if="!loading" class="empty-state">
        <uni-icons type="info" color="#b6b0c2" :size="44" />
        <text>暂无资金统计数据</text>
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
import {
  getUserInfo,
  listFundStatisticsItems,
  listFundStatistics
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
      loading: false,
      refreshing: false,
      initializing: false,
      initialized: false,
      redirecting: false,
      result: {},
      records: [],
      itemTypeOptions: [
        { label: '全部项目', value: '' },
        { label: '收入项目', value: 'income' },
        { label: '支出项目', value: 'expense' }
      ],
      itemTypeIndex: 0,
      itemOptions: [{ label: '全部收支项目', value: '' }],
      itemIndex: 0,
      startDate: range[0],
      endDate: range[1],
      dateRange: range,
      scrollHeight: 1,
      measureTimer: null
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
    if (this.initialized) this.queryStatistics()
  },
  onHide() {
    this.clearMeasureTimer()
  },
  onUnload() {
    this.clearMeasureTimer()
  },
  onPullDownRefresh() {
    this.queryStatistics().finally(() => uni.stopPullDownRefresh())
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
          .select('#fund-statistics-top')
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
        await this.loadItems()
        this.initialized = true
        await this.queryStatistics()
      } catch (error) {
        this.handleError(error, '资金统计加载失败')
      } finally {
        this.initializing = false
        this.scheduleMeasure()
      }
    },
    async loadItems() {
      const list = await listFundStatisticsItems({
        userId: this.user.id || '',
        itemType: this.itemTypeOptions[this.itemTypeIndex].value || undefined
      })
      const options = (Array.isArray(list) ? list : [])
        .filter((item) => item && item.itemKey && item.name)
        .map((item) => ({
          label: String(item.name),
          value: String(item.itemKey)
        }))
      this.itemOptions = [{ label: '全部收支项目', value: '' }, ...options]
      this.itemIndex = 0
    },
    async queryStatistics() {
      if (this.loading || this.redirecting) return
      this.loading = true
      try {
        const data = await listFundStatistics({
          userId: this.user.id || '',
          itemType: this.itemTypeOptions[this.itemTypeIndex].value || undefined,
          itemKey: this.itemOptions[this.itemIndex].value || undefined,
          startDate: this.startDate || undefined,
          endDate: this.endDate || undefined
        })
        this.result = data || {}
        this.records = Array.isArray(this.result.records) ? this.result.records : []
      } catch (error) {
        this.handleError(error, '资金统计加载失败')
      } finally {
        this.loading = false
        this.scheduleMeasure()
      }
    },
    handleItemTypeChange(event) {
      this.itemTypeIndex = Number(event.detail.value || 0)
      this.loadItems().catch((error) => this.handleError(error, '收支项目加载失败'))
    },
    handleItemChange(event) {
      this.itemIndex = Number(event.detail.value || 0)
    },
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
      this.startDate = range[0] || ''
      this.endDate = range[1] || ''
    },
    handleReset() {
      const range = monthRange()
      this.itemTypeIndex = 0
      this.startDate = range[0]
      this.endDate = range[1]
      this.dateRange = range
      this.loadItems()
        .then(() => this.queryStatistics())
        .catch((error) => this.handleError(error, '资金统计重置失败'))
    },
    handleRefresh() {
      this.refreshing = true
      this.queryStatistics().finally(() => {
        this.refreshing = false
      })
    },
    openDetail(record) {
      if (!record || !record.itemKey) return
      const query = [
        `itemKey=${encodeURIComponent(record.itemKey)}`,
        `itemName=${encodeURIComponent(record.itemName || '')}`,
        `userId=${encodeURIComponent(this.user.id || '')}`,
        `startDate=${encodeURIComponent(this.startDate || '')}`,
        `endDate=${encodeURIComponent(this.endDate || '')}`
      ].join('&')
      uni.navigateTo({ url: `/pages/fund-statistics/detail?${query}` })
    },
    itemTypeText(value) {
      if (value === 'income') return '收入'
      if (value === 'expense') return '支出'
      return '收支'
    },
    netAmount(record) {
      return Number(record.income || 0) - Number(record.expense || 0)
    },
    formatAmount(value) {
      const number = Number(value || 0)
      return Number.isFinite(number) ? number.toFixed(2) : '0.00'
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
.filter-row, .date-row, .action-row, .record-header, .record-main, .record-footer { display: flex; align-items: center; }
.filter-row, .date-row { gap: 12rpx; }
.type-picker-wrap { flex: 0 0 210rpx; }
.item-picker-wrap { flex: 1; min-width: 0; }
.picker-input { display: flex; align-items: center; justify-content: space-between; width: 100%; height: 62rpx; padding: 0 16rpx; box-sizing: border-box; overflow: hidden; color: #393044; font-size: 23rpx; background: #fff; border: 1rpx solid #ded9e8; border-radius: 10rpx; }
.picker-input text:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.placeholder { color: #aaa5b5; }
.date-row { margin-top: 14rpx; }
.date-label { flex: 0 0 auto; color: #6f6979; font-size: 22rpx; }
.date-row :deep(.uni-date) { flex: 1; min-width: 0; }
.date-row :deep(.uni-date-editor--x) { height: 62rpx; }
.date-row :deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }
.action-row { justify-content: flex-end; gap: 12rpx; margin-top: 14rpx; }
.search-button, .reset-button { display: flex; align-items: center; justify-content: center; gap: 6rpx; flex: 0 0 124rpx; height: 58rpx; margin: 0; padding: 0; font-size: 23rpx; line-height: 58rpx; border: 0; border-radius: 10rpx; }
.search-button { color: #fff; background: #722ed1; }
.reset-button { color: #666; background: #f2f3f5; }
.search-button::after, .reset-button::after { border: 0; }
.summary-card { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6rpx; margin-top: 16rpx; padding: 20rpx 6rpx; }
.summary-item, .amount-item { min-width: 0; text-align: center; }
.summary-label, .amount-label { display: block; color: #817a89; font-size: 20rpx; }
.summary-value { display: block; margin-top: 8rpx; color: #722ed1; font-size: 24rpx; font-weight: 600; }
.income { color: #00a870 !important; }
.expense { color: #e5484d !important; }
.negative { color: #e5484d !important; }
.statistics-scroll { flex: 0 0 auto; min-height: 240px; margin-top: 16rpx; }
.record-list { display: flex; flex-direction: column; gap: 14rpx; }
.record-card { padding: 20rpx; }
.record-header { justify-content: space-between; gap: 12rpx; }
.record-main { flex: 1; min-width: 0; gap: 12rpx; }
.record-name { overflow: hidden; color: #30283d; font-size: 28rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.record-type { flex: 0 0 auto; padding: 4rpx 10rpx; color: #722ed1; font-size: 19rpx; background: #f4efff; border-radius: 8rpx; }
.amount-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6rpx; margin-top: 18rpx; padding: 16rpx 4rpx; background: #faf9fc; border-radius: 10rpx; }
.amount-value { display: block; margin-top: 7rpx; color: #4d4858; font-size: 23rpx; font-weight: 600; }
.record-footer { justify-content: flex-end; margin-top: 14rpx; padding-top: 12rpx; color: #9a95a4; font-size: 20rpx; border-top: 1rpx solid #eeeaf4; }
.empty-state, .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 360rpx; gap: 14rpx; color: #aaa5b5; font-size: 23rpx; }
.bottom-space { height: 24rpx; }
</style>
