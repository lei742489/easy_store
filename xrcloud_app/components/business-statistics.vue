<template>
  <view class="page">
    <view :id="`${mode}-statistics-top`" class="top-area">
      <view class="filter-panel">
        <view class="search-row">
          <input
            v-model="filters.goodsKey"
            class="search-input"
            type="text"
            confirm-type="search"
            :placeholder="`${subject}名称 / 代码 / 拼音`"
            @confirm="handleSearch"
          />
          <button class="search-button" :disabled="loading" @click="handleSearch">
            <uni-icons type="search" color="#fff" :size="18" />
            <text>统计</text>
          </button>
        </view>

        <view class="partner-row">
          <customer-autocomplete
            v-if="!isPurchase"
            v-model="partnerName"
            placeholder="全部客户"
            @select="handlePartnerChange"
            @select-item="handlePartnerSelected"
          />
          <supplier-autocomplete
            v-else
            v-model="partnerName"
            placeholder="全部供应商"
            @select="handlePartnerChange"
            @select-item="handlePartnerSelected"
          />
          <button class="more-button" @click="openFilters">
            <uni-icons type="settings" color="#722ed1" :size="18" />
            <text>筛选</text>
            <text v-if="activeFilterCount" class="filter-count">{{ activeFilterCount }}</text>
          </button>
        </view>
      </view>

      <view v-if="hasSummary" class="summary-card">
        <view class="summary-item">
          <text class="summary-label">货品种类</text>
          <text class="summary-value">{{ total }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">数量合计</text>
          <text class="summary-value">{{ formatQuantity(summary.quantityTotal) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">金额合计</text>
          <text class="summary-value emphasis">{{ formatAmount(summary.amountTotal) }}</text>
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
          :key="String(record.goodsId || record.rowNo)"
          class="record-card"
          @click="openDetail(record)"
        >
          <view class="record-header">
            <view class="record-name-wrap">
              <text class="record-name">{{ record.goodsName || '-' }}</text>
              <text class="record-unit">{{ record.unit || '-' }}</text>
            </view>
            <uni-icons type="right" color="#aaa5b5" :size="18" />
          </view>
          <view class="record-metrics">
            <view class="metric">
              <text class="metric-label">数量</text>
              <text class="metric-value">{{ formatQuantity(record.quantity) }}</text>
            </view>
            <view class="metric metric-amount">
              <text class="metric-label">金额</text>
              <text class="metric-value emphasis">{{ formatAmount(record.amount) }}</text>
            </view>
          </view>
        </view>
      </view>
      <view v-else-if="!loading" class="empty-state">
        <uni-icons type="info" color="#b6b0c2" :size="44" />
        <text>暂无{{ subject }}统计数据</text>
      </view>
      <uni-load-more v-if="records.length || loading" :status="loadStatus" />
      <view class="bottom-space" />
    </scroll-view>

    <uni-popup ref="filterPopup" type="bottom" :safe-area="true">
      <view class="filter-popup">
        <view class="popup-header">
          <text class="popup-title">更多筛选</text>
          <uni-icons type="closeempty" color="#999" :size="22" @click="closeFilters" />
        </view>

        <view v-if="isRoot" class="popup-item">
          <text class="popup-label">营业员</text>
          <picker
            :range="cashierOptions"
            range-key="label"
            :value="cashierIndex"
            @change="handleCashierChange"
          >
            <view class="popup-picker">
              <text :class="{ placeholder: !filters.cashierId }">
                {{ cashierOptions[cashierIndex].label }}
              </text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </view>

        <view class="popup-item">
          <text class="popup-label">货品分类</text>
          <picker
            :range="categories"
            range-key="title"
            :value="categoryIndex"
            @change="handleCategoryChange"
          >
            <view class="popup-picker">
              <text :class="{ placeholder: !filters.categoryId }">
                {{ categories[categoryIndex].title }}
              </text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </view>

        <view class="popup-item">
          <text class="popup-label">日期</text>
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

        <view class="popup-actions">
          <button class="reset-button" @click="handleReset">重置</button>
          <button class="apply-button" @click="applyFilters">应用筛选</button>
        </view>
      </view>
    </uni-popup>

  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../common/auth'
import {
  getUserInfo,
  listAppUsers,
  listGoodsCategories,
  listPurchaseStatistics,
  listSaleStatistics
} from '../common/api'
import CustomerAutocomplete from './customer-autocomplete.vue'
import SupplierAutocomplete from './supplier-autocomplete.vue'

const pad = (value) => String(value).padStart(2, '0')

const monthRange = () => {
  const date = new Date()
  const start = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-01`
  const endDate = new Date(date.getFullYear(), date.getMonth() + 1, 0)
  const end = `${endDate.getFullYear()}-${pad(endDate.getMonth() + 1)}-${pad(endDate.getDate())}`
  return [start, end]
}

const normalizeCategories = (source) => {
  const list = Array.isArray(source) ? source : []
  const root = list.find((item) => Number(item && item.id) === 0)
  const children = root && Array.isArray(root.children)
    ? root.children
    : list.filter((item) => item && Number(item.parentId || 0) <= 0 && Number(item.id) !== 0)
  const result = [{ id: '', title: '全部分类' }]
  const seen = new Set()
  children.forEach((item) => {
    if (!item || item.id === undefined || item.id === null) return
    const id = String(item.id)
    if (seen.has(id)) return
    seen.add(id)
    result.push({ id, title: item.title || '未命名分类' })
  })
  return result
}

export default {
  components: {
    CustomerAutocomplete,
    SupplierAutocomplete
  },
  props: {
    mode: {
      type: String,
      default: 'sale'
    }
  },
  data() {
    const range = monthRange()
    return {
      user: getUser() || {},
      initialized: false,
      initializing: false,
      loading: false,
      refreshing: false,
      redirecting: false,
      records: [],
      partnerId: '',
      partnerName: '',
      summary: {},
      total: 0,
      current: 1,
      pageSize: 20,
      categories: [{ id: '', title: '全部分类' }],
      categoryIndex: 0,
      cashierOptions: [{ label: '全部营业员', value: '' }],
      cashierIndex: 0,
      filters: {
        goodsKey: '',
        categoryId: '',
        cashierId: '',
        startDate: range[0],
        endDate: range[1]
      },
      dateRange: range,
      scrollHeight: 1,
      measureTimer: null
    }
  },
  computed: {
    isPurchase() {
      return this.mode === 'purchase'
    },
    subject() {
      return this.isPurchase ? '进货' : '销售'
    },
    hasMore() {
      return this.records.length < this.total
    },
    loadStatus() {
      if (this.loading) return 'loading'
      return this.hasMore ? 'more' : 'noMore'
    },
    hasSummary() {
      return this.records.length > 0 || this.loading
    },
    isRoot() {
      return Number(this.user.isRoot) === 1
    },
    activeFilterCount() {
      let count = 0
      if (this.filters.categoryId) count += 1
      if (this.isRoot && this.filters.cashierId) count += 1
      if (this.dateRange && this.dateRange.length === 2) count += 1
      return count
    }
  },
  mounted() {
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
    refresh() {
      return this.queryStatistics(true)
    },
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
      const selector = `#${this.mode}-statistics-top`
      try {
        uni.createSelectorQuery()
          .select(selector)
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
        const requests = [getUserInfo(), listGoodsCategories()]
        const result = await Promise.all(requests)
        this.user = { ...this.user, ...(result[0] || {}) }
        updateUser(this.user)
        this.categories = normalizeCategories(result[1])
        if (this.isRoot) await this.loadCashiers()
        this.initialized = true
        await this.queryStatistics(true)
      } catch (error) {
        this.handleError(error, `${this.subject}统计加载失败`)
      } finally {
        this.initializing = false
        this.scheduleMeasure()
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
    buildQuery(page) {
      const query = {
        userId: this.user.id,
        current: page,
        pageSize: this.pageSize,
        goodsKey: String(this.filters.goodsKey || '').trim() || undefined,
        categoryId: this.filters.categoryId || undefined,
        startDate: this.filters.startDate || undefined,
        endDate: this.filters.endDate || undefined
      }
      if (this.partnerId) {
        query[this.isPurchase ? 'supplierId' : 'customerId'] = this.partnerId
      }
      if (this.isRoot && this.filters.cashierId) {
        query.cashierId = this.filters.cashierId
      }
      return query
    },
    async queryStatistics(reset = false) {
      if (this.loading || this.redirecting) return Promise.resolve()
      this.loading = true
      try {
        const api = this.isPurchase ? listPurchaseStatistics : listSaleStatistics
        const source = await api(this.buildQuery(reset ? 1 : this.current)) || {}
        const rows = Array.isArray(source.records) ? source.records : []
        this.records = reset ? rows : this.records.concat(rows)
        this.current = Number(source.current || (reset ? 1 : this.current))
        this.total = Number(source.total || this.records.length)
        this.summary = source
      } catch (error) {
        this.handleError(error, `${this.subject}统计加载失败`)
      } finally {
        this.loading = false
        this.scheduleMeasure()
      }
    },
    handleSearch() {
      this.closeFilters()
      this.current = 1
      this.queryStatistics(true)
    },
    handleReset() {
      const range = monthRange()
      this.filters = {
        goodsKey: '',
        categoryId: '',
        cashierId: '',
        startDate: range[0],
        endDate: range[1]
      }
      this.partnerId = ''
      this.partnerName = ''
      this.dateRange = range
      this.categoryIndex = 0
      this.cashierIndex = 0
      this.closeFilters()
      this.queryStatistics(true)
    },
    openFilters() {
      this.$refs.filterPopup && this.$refs.filterPopup.open()
    },
    closeFilters() {
      this.$refs.filterPopup && this.$refs.filterPopup.close()
    },
    applyFilters() {
      this.closeFilters()
      this.queryStatistics(true)
    },
    handlePartnerChange(value) {
      if (!value) {
        this.partnerId = ''
        this.partnerName = ''
      }
    },
    handlePartnerSelected(payload) {
      if (!payload) {
        this.partnerId = ''
        this.partnerName = ''
        return
      }
      this.partnerId = payload.id === undefined || payload.id === null
        ? ''
        : String(payload.id)
      this.partnerName = String(payload.name || '').trim()
    },
    handleCashierChange(event) {
      this.cashierIndex = Number(event.detail.value || 0)
      this.filters.cashierId = this.cashierOptions[this.cashierIndex].value
    },
    handleCategoryChange(event) {
      this.categoryIndex = Number(event.detail.value || 0)
      this.filters.categoryId = this.categories[this.categoryIndex].id
    },
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
      this.filters.startDate = range[0] || ''
      this.filters.endDate = range[1] || ''
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.current += 1
      this.queryStatistics(false)
    },
    handleRefresh() {
      this.refreshing = true
      this.queryStatistics(true).finally(() => {
        this.refreshing = false
      })
    },
    openDetail(record) {
      if (!record || record.goodsId === undefined || record.goodsId === null) return
      const query = {
        mode: this.mode,
        goodsId: record.goodsId,
        goodsName: record.goodsName || '',
        unit: record.unit || '',
        userId: this.user.id || '',
        goodsKey: this.filters.goodsKey || '',
        categoryId: this.filters.categoryId || '',
        cashierId: this.filters.cashierId || '',
        customerId: this.isPurchase ? '' : this.partnerId,
        supplierId: this.isPurchase ? this.partnerId : '',
        startDate: this.filters.startDate || '',
        endDate: this.filters.endDate || ''
      }
      const queryString = Object.keys(query)
        .map((key) => `${key}=${encodeURIComponent(query[key])}`)
        .join('&')
      uni.navigateTo({
        url: `/pages/business-statistics-detail/index?${queryString}`
      })
    },
    formatQuantity(value) {
      const number = Number(value || 0)
      if (!Number.isFinite(number)) return '0'
      return Number.isInteger(number) ? String(number) : number.toFixed(2)
    },
    formatAmount(value) {
      const number = Number(value || 0)
      return Number.isFinite(number) ? `￥${number.toFixed(2)}` : '￥0.00'
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
  },
  beforeUnmount() {
    this.clearMeasureTimer()
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

.search-row,
.partner-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.partner-row { margin-top: 14rpx; }

.search-input {
  flex: 1;
  min-width: 0;
  height: 62rpx;
  padding: 0 18rpx;
  box-sizing: border-box;
  color: #393044;
  font-size: 24rpx;
  background: #fff;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}

.search-button,
.more-button,
.reset-button,
.apply-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  height: 62rpx;
  margin: 0;
  padding: 0 18rpx;
  box-sizing: border-box;
  border: 0;
  border-radius: 10rpx;
  font-size: 23rpx;
  line-height: 62rpx;
  white-space: nowrap;
}

.search-button {
  flex: 0 0 160rpx;
  width: 160rpx;
  color: #fff;
  background: #722ed1;
}

.more-button {
  position: relative;
  flex: 0 0 160rpx;
  width: 160rpx;
  color: #722ed1;
  background: #f4efff;
}

.search-button::after,
.more-button::after,
.reset-button::after,
.apply-button::after { border: 0; }

.filter-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 26rpx;
  height: 26rpx;
  padding: 0 5rpx;
  color: #fff;
  font-size: 17rpx;
  line-height: 26rpx;
  background: #722ed1;
  border-radius: 16rpx;
}

.partner-row :deep(.customer-select),
.partner-row :deep(.supplier-select) {
  flex: 1;
  min-width: 0;
}

.partner-row :deep(.input-wrap) { height: 62rpx; }

.summary-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4rpx;
  margin-top: 16rpx;
  padding: 20rpx 6rpx;
}

.summary-item,
.metric { min-width: 0; text-align: center; }

.summary-label,
.metric-label {
  display: block;
  color: #817a89;
  font-size: 20rpx;
}

.summary-value {
  display: block;
  margin-top: 8rpx;
  color: #7350d1;
  font-size: 25rpx;
  font-weight: 600;
}

.emphasis { color: #722ed1; }

.statistics-scroll {
  min-height: 240px;
  margin-top: 16rpx;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.record-card { padding: 20rpx; }

.record-header {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.record-name-wrap {
  display: flex;
  align-items: baseline;
  flex: 1;
  min-width: 0;
  gap: 12rpx;
}

.record-name {
  overflow: hidden;
  color: #30283d;
  font-size: 27rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-unit {
  flex: 0 0 auto;
  color: #8b8594;
  font-size: 21rpx;
}

.record-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8rpx;
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f0edf5;
}

.metric-value {
  display: block;
  margin-top: 7rpx;
  color: #3d3944;
  font-size: 25rpx;
  font-weight: 600;
}

.metric-amount { border-left: 1rpx solid #f0edf5; }

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 360rpx;
  gap: 14rpx;
  color: #aaa5b5;
  font-size: 23rpx;
}

.bottom-space { height: 24rpx; }

.filter-popup {
  max-height: 82vh;
  padding: 28rpx 28rpx calc(22rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 26rpx;
}

.popup-title {
  color: #30283d;
  font-size: 32rpx;
  font-weight: 600;
}

.popup-item { margin-bottom: 22rpx; }

.popup-label {
  display: block;
  margin-bottom: 10rpx;
  color: #6b6676;
  font-size: 22rpx;
}

.popup-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 68rpx;
  padding: 0 18rpx;
  box-sizing: border-box;
  color: #393044;
  font-size: 24rpx;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}

.placeholder { color: #aaa5b5; }

.popup-item :deep(.uni-date),
.popup-item :deep(.uni-date-editor--x) {
  width: 100%;
}

.popup-item :deep(.uni-date-editor--x) { height: 68rpx; }

.popup-actions {
  display: flex;
  gap: 18rpx;
  margin-top: 30rpx;
}

.popup-actions button { flex: 1; }

.reset-button {
  color: #666;
  background: #f2f3f5;
}

.apply-button {
  color: #fff;
  background: #722ed1;
}

</style>
