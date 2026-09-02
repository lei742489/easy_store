<template>
  <view class="page">
    <view id="stock-top" class="top-area">
      <view class="filter-panel">
        <view class="search-row">
          <input
            v-model="filters.key"
            class="search-input"
            placeholder="货品名称 / 代码"
            confirm-type="search"
            @confirm="handleSearch"
          />
          <button class="search-button" :disabled="loading" @click="handleSearch">
            <uni-icons type="search" color="#fff" :size="18" />
            <text>查询</text>
          </button>
        </view>
        <view class="date-row">
          <text class="filter-label">日期</text>
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
          <picker
            class="category-picker"
            :range="categories"
            range-key="title"
            :value="categoryIndex"
            @change="handleCategoryChange"
          >
            <view class="picker-value">
              <text :class="{ placeholder: !filters.categoryId }">
                {{ categories[categoryIndex].title }}
              </text>
              <uni-icons type="down" color="#999" :size="15" />
            </view>
          </picker>
          <button class="reset-button" @click="handleReset">
            <uni-icons type="refresh" color="#666" :size="17" />
            <text>重置</text>
          </button>
        </view>
      </view>

      <view v-if="hasSummary" class="summary-card">
        <view class="summary-item">
          <text class="summary-label">期初数量</text>
          <text class="summary-value">{{ formatQuantity(summary.openingQtyTotal) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">库存新增</text>
          <text class="summary-value">{{ formatQuantity(summary.inQtyTotal) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">库存减少</text>
          <text class="summary-value">{{ formatQuantity(summary.outQtyTotal) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">期末数量</text>
          <text class="summary-value emphasis">{{ formatQuantity(summary.endingQtyTotal) }}</text>
        </view>
      </view>
    </view>

    <scroll-view
      class="stock-scroll"
      scroll-y
      :show-scrollbar="false"
      :style="{ height: `${stockScrollHeight}px` }"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      :lower-threshold="120"
      @refresherrefresh="handleRefresh"
      @scrolltolower="loadMore"
    >
      <view v-if="records.length" class="record-list">
        <view
          v-for="item in records"
          :key="String(item.goodsId || item.rowNo)"
          class="record-card"
          @click="openDetail(item)"
        >
          <view class="record-title-row">
            <text class="record-title">{{ item.goodsName || '-' }}</text>
            <text class="record-unit">{{ item.unit || '-' }}</text>
            <uni-icons type="right" color="#aaa5b5" :size="18" />
          </view>
          <view class="metric-grid">
            <view class="metric">
              <text class="metric-label">期初</text>
              <text class="metric-value">{{ formatQuantity(item.openingQty) }}</text>
              <text class="metric-amount">￥{{ formatAmount(item.openingAmount) }}</text>
            </view>
            <view class="metric">
              <text class="metric-label">新增</text>
              <text class="metric-value positive">{{ formatQuantity(item.inQty) }}</text>
              <text class="metric-amount">￥{{ formatAmount(item.inAmount) }}</text>
            </view>
            <view class="metric">
              <text class="metric-label">减少</text>
              <text class="metric-value negative">{{ formatQuantity(item.outQty) }}</text>
              <text class="metric-amount">￥{{ formatAmount(item.outAmount) }}</text>
            </view>
            <view class="metric">
              <text class="metric-label">期末</text>
              <text class="metric-value emphasis">{{ formatQuantity(item.endingQty) }}</text>
              <text class="metric-amount">￥{{ formatAmount(item.endingAmount) }}</text>
            </view>
          </view>
        </view>
      </view>
      <view v-else-if="!loading" class="empty-state">
        <uni-icons type="info" color="#b6b0c2" :size="44" />
        <text>暂无库存统计数据</text>
      </view>
      <uni-load-more v-if="records.length || loading" :status="loadStatus" />
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import {
  getUserInfo,
  listGoodsCategories,
  listStockStatistics
} from '../../common/api'

const pad = (value) => String(value).padStart(2, '0')
const monthRange = () => {
  const date = new Date()
  const start = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-01`
  const endDate = new Date(date.getFullYear(), date.getMonth() + 1, 0)
  return [start, `${endDate.getFullYear()}-${pad(endDate.getMonth() + 1)}-${pad(endDate.getDate())}`]
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
      stockScrollHeight: 1,
      measureTimer: null,
      current: 1,
      pageSize: 20,
      total: 0,
      records: [],
      categories: [{ id: '', title: '全部分类' }],
      categoryIndex: 0,
      filters: { categoryId: '', key: '', startDate: range[0], endDate: range[1] },
      dateRange: range,
      summary: {}
    }
  },
  computed: {
    hasMore() { return this.records.length < this.total },
    loadStatus() {
      if (this.loading) return 'loading'
      return this.hasMore ? 'more' : 'noMore'
    },
    hasSummary() { return this.records.length > 0 }
  },
  onReady() {
    this.scheduleMeasure()
  },
  onLoad() { this.initialize() },
  onShow() {
    if (!isLoggedIn()) this.redirectToLogin()
    else {
      this.scheduleMeasure()
      if (this.initialized) this.queryStatistics(true)
    }
  },
  onHide() {
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
        this.measureStockScroll()
      }, 0)
    },
    measureStockScroll() {
      if (this.redirecting) return
      const systemInfo = uni.getSystemInfoSync()
      try {
        uni.createSelectorQuery()
          .select('#stock-top')
          .boundingClientRect()
          .exec((rects) => {
            const topRect = rects && rects[0]
            if (!topRect || !topRect.height) return
            const topBottom = Number(topRect.bottom || topRect.top + topRect.height)
            const viewportBottom = Number(systemInfo.windowHeight || 0)
            const marginTop = uni.upx2px ? uni.upx2px(16) : 8
            this.stockScrollHeight = Math.max(
              240,
              Math.floor(viewportBottom - topBottom - marginTop)
            )
          })
      } catch (error) {
        this.stockScrollHeight = Math.max(
          240,
          Math.floor(Number(systemInfo.windowHeight || 0) * 0.65)
        )
      }
    },
    async initialize() {
      if (this.initializing || this.redirecting) return
      this.initializing = true
      try {
        const [user, categoryData] = await Promise.all([getUserInfo(), listGoodsCategories()])
        this.user = { ...this.user, ...user }
        updateUser(this.user)
        const source = Array.isArray(categoryData) ? categoryData : []
        const children = source.find((item) => Number(item.id) === 0)?.children || source
        this.categories = [{ id: '', title: '全部分类' }].concat(
          children.filter((item) => item && item.id !== undefined).map((item) => ({
            id: String(item.id),
            title: item.title || '未命名分类'
          }))
        )
        this.initialized = true
        await this.queryStatistics(true)
        this.scheduleMeasure()
      } catch (error) {
        this.handleError(error, '库存统计加载失败')
      } finally {
        this.initializing = false
      }
    },
    buildQuery(page) {
      return {
        categoryId: this.filters.categoryId || undefined,
        key: String(this.filters.key || '').trim() || undefined,
        startDate: this.filters.startDate,
        endDate: this.filters.endDate,
        current: page,
        pageSize: this.pageSize
      }
    },
    async queryStatistics(reset = false) {
      if (this.loading || this.redirecting) return
      this.loading = true
      try {
        const page = await listStockStatistics(this.buildQuery(reset ? 1 : this.current))
        const source = page || {}
        const rows = Array.isArray(source.records) ? source.records : []
        this.records = reset ? rows : this.records.concat(rows)
        this.current = Number(source.current || (reset ? 1 : this.current))
        this.total = Number(source.total || this.records.length)
        this.summary = source
      } catch (error) {
        this.handleError(error, '库存统计加载失败')
      } finally {
        this.loading = false
        this.scheduleMeasure()
      }
    },
    handleSearch() {
      this.current = 1
      this.queryStatistics(true)
    },
    handleReset() {
      const range = monthRange()
      this.filters = { categoryId: '', key: '', startDate: range[0], endDate: range[1] }
      this.dateRange = range
      this.categoryIndex = 0
      this.handleSearch()
    },
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
      this.filters.startDate = range[0] || undefined
      this.filters.endDate = range[1] || undefined
    },
    handleCategoryChange(event) {
      this.categoryIndex = Number(event.detail.value || 0)
      this.filters.categoryId = this.categories[this.categoryIndex]?.id || ''
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.current += 1
      this.queryStatistics(false)
    },
    handleRefresh() {
      this.refreshing = true
      this.queryStatistics(true).finally(() => { this.refreshing = false })
    },
    openDetail(item) {
      if (!item || item.goodsId === undefined || item.goodsId === null) return
      const params = [
        `goodsId=${encodeURIComponent(item.goodsId)}`,
        `goodsName=${encodeURIComponent(item.goodsName || '')}`,
        `unit=${encodeURIComponent(item.unit || '')}`,
        `startDate=${encodeURIComponent(this.filters.startDate || '')}`,
        `endDate=${encodeURIComponent(this.filters.endDate || '')}`
      ]
      uni.navigateTo({ url: `/pages/stock-detail/index?${params.join('&')}` })
    },
    formatQuantity(value) {
      const number = Number(value || 0)
      return Number.isFinite(number) ? (Number.isInteger(number) ? String(number) : number.toFixed(2)) : '0'
    },
    formatAmount(value) {
      const number = Number(value || 0)
      return Number.isFinite(number) ? number.toFixed(2) : '0.00'
    },
    handleError(error, fallback) {
      const message = error && error.message ? error.message : fallback
      if (/登录|token|过期/i.test(message)) this.redirectToLogin()
      else uni.showToast({ title: message, icon: 'none' })
    },
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
.page { display: flex; flex-direction: column; height: 100%; min-height: 0; padding: 18rpx 14rpx 0; box-sizing: border-box; color: #34313d; background: #f5f4fb; overflow: hidden; }
.top-area { flex: 0 0 auto; }
.filter-panel, .summary-card, .record-card { background: #fff; border: 1rpx solid #ece9f2; border-radius: 16rpx; box-shadow: 0 6rpx 20rpx rgba(67,47,119,.06); }
.filter-panel { padding: 18rpx; }
.search-row, .date-row, .action-row { display: flex; align-items: center; gap: 12rpx; }
.search-input, .picker-value { flex: 1; min-width: 0; height: 62rpx; padding: 0 18rpx; box-sizing: border-box; color: #393044; font-size: 24rpx; line-height: 62rpx; background: #fff; border: 1rpx solid #ded9e8; border-radius: 10rpx; }
.search-button, .reset-button { display: flex; align-items: center; justify-content: center; gap: 6rpx; height: 62rpx; margin: 0; padding: 0 18rpx; font-size: 23rpx; border: 0; border-radius: 10rpx; }
.search-button { flex: 0 0 124rpx; color: #fff; background: #722ed1; }
.reset-button { flex: 0 0 124rpx; color: #666; background: #f2f3f5; }
.search-button::after, .reset-button::after { border: 0; }
.date-row { margin-top: 14rpx; }
.filter-label { flex: 0 0 auto; color: #6f6979; font-size: 22rpx; }
.date-row :deep(.uni-date) { flex: 1; min-width: 0; }
.date-row :deep(.uni-date-editor--x) { height: 62rpx; }
.date-row :deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }
.action-row { margin-top: 14rpx; }
.category-picker { flex: 1; min-width: 0; }
.picker-value { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.placeholder { color: #aaa5b5; }
.summary-card { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 4rpx; margin-top: 16rpx; padding: 18rpx 6rpx; }
.summary-item, .metric { min-width: 0; text-align: center; }
.summary-label, .metric-label { display: block; color: #817a89; font-size: 20rpx; }
.summary-value { display: block; margin-top: 8rpx; color: #7350d1; font-size: 24rpx; font-weight: 600; }
.emphasis { color: #722ed1; font-weight: 600; }
.stock-scroll { min-height: 100; margin-top: 16rpx; }
.record-list { display: flex; flex-direction: column; gap: 14rpx; }
.record-card { padding: 18rpx; }
.record-title-row { display: flex; align-items: center; gap: 8rpx; }
.record-title { flex: 1; min-width: 0; overflow: hidden; color: #30283d; font-size: 27rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.record-unit { flex: 0 0 auto; color: #8b8594; font-size: 21rpx; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 4rpx; margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid #f0edf5; }
.metric-value { display: block; margin-top: 6rpx; color: #3d3944; font-size: 23rpx; font-weight: 600; }
.metric-amount { display: block; margin-top: 5rpx; color: #8b8594; font-size: 18rpx; }
.positive { color: #3c8d69; }
.negative { color: #e5484d; }
.empty-state, .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 360rpx; gap: 14rpx; color: #aaa5b5; font-size: 23rpx; }
.bottom-space { height: 24rpx; }
</style>
