<template>
  <view class="page">
    <view class="filter-panel">
      <view class="quick-search-row">
        <input
          v-model="keyword"
          class="quick-search-input"
          placeholder="单号 / 备注"
          confirm-type="search"
          @confirm="handleSearch"
        />
        <button class="quick-search-button" :disabled="loading" @click="handleSearch">
          <uni-icons type="search" color="#fff" :size="20" />
          <text>查询</text>
        </button>
      </view>
      <view class="date-filter-row">
        <text class="date-filter-label">日期</text>
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
      <view class="filter-actions">
        <button class="add-button" @click="openCreateForm">
          <uni-icons type="plusempty" color="#fff" :size="18" />
          <text>新增</text>
        </button>
        <button class="reset-button" @click="handleReset">
          <uni-icons type="refresh" color="#666" :size="18" />
          <text>重置</text>
        </button>
      </view>
    </view>

    <view class="list-summary">
      <text>盘点单列表</text>
      <text>共 {{ total }} 条</text>
    </view>

    <view v-if="records.length" class="record-list">
      <view
        v-for="record in records"
        :key="record.id"
        class="record-card"
        @click="openEditForm(record)"
      >
        <view class="card-header">
          <view class="record-main">
            <view class="record-icon">
              <uni-icons type="bars" color="#722ed1" :size="24" />
            </view>
            <view class="record-title-wrap">
              <text class="order-no">{{ record.orderNo || '-' }}</text>
              <text class="record-date">{{ formatDate(record.createTime) }}</text>
            </view>
          </view>
          <uni-icons type="right" color="#aaa5b5" :size="18" />
        </view>

        <view class="summary-grid">
          <view class="summary-item">
            <text class="summary-label">盘点商品</text>
            <text class="summary-value">{{ itemCount(record) }} 种</text>
          </view>
          <view class="summary-item">
            <text class="summary-label">盈亏数量</text>
            <text class="summary-value" :class="valueClass(record.profitLossQuantity)">
              {{ formatQuantity(record.profitLossQuantity) }}
            </text>
          </view>
          <view class="summary-item">
            <text class="summary-label">盈亏金额</text>
            <text class="summary-value" :class="valueClass(record.profitLossAmount)">
              ¥ {{ formatAmount(record.profitLossAmount) }}
            </text>
          </view>
        </view>

        <view v-if="record.note" class="note-row">
          <text class="note-label">备注</text>
          <text class="note-text">{{ record.note }}</text>
        </view>

        <view class="card-footer">
          <text>营业员：{{ record.cashierName || record.cashierId_dictText || '-' }}</text>
          <text>点击编辑</text>
        </view>
      </view>
    </view>

    <view v-else-if="!loading" class="empty-state">
      <uni-icons type="info" color="#aaa5b5" :size="52" />
      <text>暂无盘点单</text>
    </view>

    <uni-load-more v-if="records.length || loading" :status="loadStatus" />
    <view class="bottom-space" />
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import { getUserInfo, listStockChecks } from '../../common/api'

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
      keyword: '',
      dateRange: range,
      records: [],
      current: 1,
      pageSize: 15,
      total: 0,
      loading: false,
      initialized: false,
      initializing: false,
      redirecting: false
    }
  },
  computed: {
    hasMore() {
      return this.records.length < this.total
    },
    loadStatus() {
      if (this.loading) return 'loading'
      return this.hasMore ? 'more' : 'noMore'
    }
  },
  onShow() {
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    if (!this.initialized) this.initialize()
    else this.queryRecords(true)
  },
  onReachBottom() {
    this.loadMore()
  },
  onPullDownRefresh() {
    this.queryRecords(true).finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    async initialize() {
      if (this.initializing || this.redirecting) return
      this.initializing = true
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...currentUser }
        updateUser(this.user)
        this.initialized = true
        await this.queryRecords(true)
      } catch (error) {
        this.handleError(error, '库存盘点加载失败')
      } finally {
        this.initializing = false
      }
    },
    buildQuery(current) {
      const params = {
        current,
        pageSize: this.pageSize,
        column: 'createTime',
        order: 'desc',
        userId: this.user.id,
        createTime_begin: this.dateRange[0],
        createTime_end: this.dateRange[1]
      }
      const value = String(this.keyword || '').trim()
      if (value) params.searchKey = value
      return params
    },
    async queryRecords(reset = false, pageNumber) {
      if (this.loading || this.redirecting) return
      const targetPage = reset ? 1 : (pageNumber || this.current)
      this.loading = true
      try {
        const page = await listStockChecks(this.buildQuery(targetPage))
        const list = page && Array.isArray(page.records) ? page.records : []
        this.records = reset ? list : this.records.concat(list)
        this.current = Number(page && page.current ? page.current : targetPage)
        this.total = Number(page && page.total ? page.total : this.records.length)
      } catch (error) {
        this.handleError(error, '库存盘点加载失败')
      } finally {
        this.loading = false
      }
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.queryRecords(false, this.current + 1)
    },
    handleSearch() {
      this.queryRecords(true)
    },
    handleReset() {
      this.keyword = ''
      this.dateRange = monthRange()
      this.queryRecords(true)
    },
    handleDateChange(value) {
      this.dateRange = Array.isArray(value) ? value : []
      this.queryRecords(true)
    },
    openCreateForm() {
      this.navigateToForm()
    },
    openEditForm(record) {
      if (!record || record.id === undefined || record.id === null) return
      this.navigateToForm(String(record.id), record)
    },
    navigateToForm(id, record) {
      const storageKey = id ? `easy-store-stock-check-edit-${Date.now()}` : ''
      if (storageKey) uni.setStorageSync(storageKey, record)
      const query = id
        ? `?id=${encodeURIComponent(id)}&storageKey=${encodeURIComponent(storageKey)}`
        : ''
      uni.navigateTo({
        url: `/pages/stock-check/form${query}`,
        success: (res) => {
          const channel = res && res.eventChannel
          if (channel) channel.on('saved', () => this.queryRecords(true))
        }
      })
    },
    itemCount(record) {
      return Array.isArray(record && record.items) ? record.items.length : 0
    },
    formatDate(value) {
      return value ? String(value).split(' ')[0].split('T')[0] : '-'
    },
    formatQuantity(value) {
      const number = Number(value || 0)
      return Number.isFinite(number) ? number.toFixed(2) : '0.00'
    },
    formatAmount(value) {
      const number = Number(value || 0)
      return Number.isFinite(number) ? number.toFixed(2) : '0.00'
    },
    valueClass(value) {
      return Number(value || 0) < 0 ? 'negative' : 'positive'
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
.page { min-height: 100vh; padding: 22rpx; box-sizing: border-box; color: #33303f; background: #f5f4fb; }
.filter-panel, .record-card { background: #fff; border: 1rpx solid #ece9f2; border-radius: 16rpx; box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06); }
.filter-panel { padding: 20rpx 22rpx 16rpx; }
.quick-search-row, .filter-actions, .card-header, .record-main, .card-footer { display: flex; align-items: center; }
.quick-search-row { gap: 14rpx; }
.quick-search-input { flex: 1; min-width: 0; height: 58rpx; padding: 0 20rpx; box-sizing: border-box; color: #393044; font-size: 25rpx; background: #fff; border: 1rpx solid #ded9e8; border-radius: 10rpx; }
.quick-search-button, .add-button, .reset-button { display: flex; align-items: center; justify-content: center; gap: 8rpx; flex: 0 0 124rpx; height: 58rpx; margin: 0; padding: 0; font-size: 24rpx; line-height: 58rpx; border-radius: 10rpx; }
.quick-search-button, .add-button { color: #fff; background: #722ed1; }
.reset-button { color: #666; background: #f2f3f5; }
.quick-search-button::after, .add-button::after, .reset-button::after { border: 0; }
.date-filter-row { display: flex; align-items: center; gap: 12rpx; margin-top: 16rpx; }
.date-filter-label { flex: 0 0 auto; color: #6f6979; font-size: 22rpx; }
.date-filter-row :deep(.uni-date) { flex: 1; min-width: 0; }
.date-filter-row :deep(.uni-date-editor--x) { height: 58rpx; }
.date-filter-row :deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }
.filter-actions { justify-content: flex-end; gap: 14rpx; margin-top: 16rpx; }
.list-summary { display: flex; align-items: center; justify-content: space-between; padding: 26rpx 6rpx 16rpx; color: #777482; font-size: 23rpx; }
.list-summary text:first-child { color: #454252; font-size: 29rpx; font-weight: 600; }
.record-card { margin-bottom: 18rpx; padding: 22rpx; }
.card-header { justify-content: space-between; gap: 14rpx; }
.record-main { flex: 1; min-width: 0; gap: 14rpx; }
.record-icon { display: flex; align-items: center; justify-content: center; flex: 0 0 62rpx; width: 62rpx; height: 62rpx; background: #f4efff; border-radius: 14rpx; }
.record-title-wrap { display: flex; flex-direction: column; min-width: 0; }
.order-no { overflow: hidden; color: #30283d; font-size: 29rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.record-date { margin-top: 6rpx; color: #9a95a4; font-size: 21rpx; }
.summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8rpx; margin-top: 20rpx; padding: 18rpx 8rpx; background: #faf9fc; border-radius: 12rpx; }
.summary-item { min-width: 0; text-align: center; }
.summary-label { display: block; color: #918b99; font-size: 20rpx; }
.summary-value { display: block; margin-top: 7rpx; color: #4d4858; font-size: 23rpx; font-weight: 600; }
.summary-value.positive { color: #00a870; }
.summary-value.negative { color: #e5484d; }
.note-row { display: flex; gap: 12rpx; margin-top: 16rpx; color: #686171; font-size: 22rpx; }
.note-label { flex: 0 0 62rpx; color: #918b99; }
.note-text { flex: 1; word-break: break-all; }
.card-footer { justify-content: space-between; margin-top: 18rpx; padding-top: 15rpx; color: #9a95a4; font-size: 20rpx; border-top: 1rpx solid #eeeaf4; }
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 500rpx; gap: 18rpx; color: #aaa5b5; font-size: 24rpx; }
.bottom-space { height: 30rpx; }
</style>
