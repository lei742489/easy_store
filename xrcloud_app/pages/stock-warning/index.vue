<template>
  <view class="page">
    <view id="warning-top" class="top-area">
      <view class="filter-panel">
        <view class="filter-row">
          <supplier-autocomplete
            v-model="supplierName"
            placeholder="全部供应商"
            @select="handleSupplierChange"
            @select-item="handleSupplierSelected"
          />
          <button class="search-button" :disabled="loading" @click="handleSearch">
            <uni-icons type="search" color="#fff" :size="18" />
            <text>查询</text>
          </button>
        </view>
        <view class="filter-actions">
          <text class="filter-hint">当前显示低于最低库存或高于最高库存的货品</text>
          <button class="reset-button" @click="handleReset">
            <uni-icons type="refresh" color="#666" :size="17" />
            <text>重置</text>
          </button>
        </view>
      </view>

      <view class="summary-card">
        <view class="summary-item">
          <text class="summary-label">预警货品</text>
          <text class="summary-value">{{ total }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">当前页</text>
          <text class="summary-value">{{ records.length }}</text>
        </view>
      </view>
    </view>

    <scroll-view
      class="warning-scroll"
      scroll-y
      :show-scrollbar="false"
      :style="{ height: `${warningScrollHeight}px` }"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      :lower-threshold="120"
      @refresherrefresh="handleRefresh"
      @scrolltolower="loadMore"
    >
      <view v-if="records.length" class="warning-list">
        <view
          v-for="item in records"
          :key="String(item.id)"
          class="warning-card"
          @click="openDetail(item)"
        >
          <view class="card-header">
            <view class="goods-title-wrap">
              <text class="goods-title">{{ item.title || '-' }}</text>
              <text v-if="item.goodsCode" class="goods-code">{{ item.goodsCode }}</text>
            </view>
            <uni-icons type="right" color="#aaa5b5" :size="18" />
          </view>
          <view class="goods-meta">
            <text>单位：{{ item.unit || '-' }}</text>
            <text class="supplier-name">{{ item.supplierName || item.supplierId_dictText || '未设置供应商' }}</text>
          </view>
          <view class="stock-panel">
            <view class="stock-item">
              <text class="stock-label">最高存量</text>
              <text class="stock-value">{{ formatQuantity(item.maxStock) }}</text>
            </view>
            <view class="stock-item">
              <text class="stock-label">最低存量</text>
              <text class="stock-value">{{ formatQuantity(item.minStock) }}</text>
            </view>
            <view class="stock-item">
              <text class="stock-label">当前存量</text>
              <text class="stock-value current" :class="{ danger: isShortage(item), excess: isExcess(item) }">
                {{ formatQuantity(item.stock) }}
              </text>
            </view>
            <view class="stock-item warning-item">
              <text class="stock-label">{{ isShortage(item) ? '短缺数量' : '超出数量' }}</text>
              <text class="stock-value warning-value">{{ formatQuantity(warningQuantity(item)) }}</text>
            </view>
          </view>
          <view class="warning-message" :class="{ excess: isExcess(item) }">
            <uni-icons
              :type="isShortage(item) ? 'info-filled' : 'notification-filled'"
              :color="isShortage(item) ? '#e5484d' : '#f08a24'"
              :size="17"
            />
            <text>{{ warningText(item) }}</text>
          </view>
        </view>
      </view>
      <view v-else-if="!loading" class="empty-state">
        <uni-icons type="info" color="#b6b0c2" :size="44" />
        <text>暂无库存预警</text>
      </view>
      <uni-load-more v-if="records.length || loading" :status="loadStatus" />
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import { getUserInfo, listStockWarnings } from '../../common/api'
import SupplierAutocomplete from '../../components/supplier-autocomplete.vue'

export default {
  components: { SupplierAutocomplete },
  data() {
    return {
      user: getUser() || {},
      supplierId: '',
      supplierName: '',
      loading: false,
      refreshing: false,
      initializing: false,
      initialized: false,
      redirecting: false,
      warningScrollHeight: 1,
      measureTimer: null,
      current: 1,
      pageSize: 20,
      total: 0,
      records: []
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
    if (this.initialized) this.queryWarnings(true)
  },
  onHide() {
    this.clearMeasureTimer()
  },
  onUnload() {
    this.clearMeasureTimer()
  },
  onPullDownRefresh() {
    this.queryWarnings(true).finally(() => uni.stopPullDownRefresh())
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
        this.measureWarningScroll()
      }, 0)
    },
    measureWarningScroll() {
      if (this.redirecting) return
      const systemInfo = uni.getSystemInfoSync()
      try {
        uni.createSelectorQuery()
          .select('#warning-top')
          .boundingClientRect()
          .exec((rects) => {
            const topRect = rects && rects[0]
            if (!topRect || !topRect.height) return
            const topBottom = Number(topRect.bottom || topRect.top + topRect.height)
            const viewportBottom = Number(systemInfo.windowHeight || 0)
            const marginTop = uni.upx2px ? uni.upx2px(16) : 8
            this.warningScrollHeight = Math.max(
              240,
              Math.floor(viewportBottom - topBottom - marginTop)
            )
          })
      } catch (error) {
        this.warningScrollHeight = Math.max(
          240,
          Math.floor(Number(systemInfo.windowHeight || 0) * 0.65)
        )
      }
    },
    async initialize() {
      if (this.initializing || this.redirecting) return
      this.initializing = true
      try {
        const user = await getUserInfo()
        this.user = { ...this.user, ...user }
        updateUser(this.user)
        this.initialized = true
        await this.queryWarnings(true)
        this.scheduleMeasure()
      } catch (error) {
        this.handleError(error, '库存预警加载失败')
      } finally {
        this.initializing = false
      }
    },
    async queryWarnings(reset = false) {
      if (this.loading || this.redirecting) return
      this.loading = true
      try {
        const page = await listStockWarnings({
          supplierId: this.supplierId || undefined,
          current: reset ? 1 : this.current,
          pageSize: this.pageSize
        })
        const source = page || {}
        const rows = Array.isArray(source.records) ? source.records : []
        this.records = reset ? rows : this.records.concat(rows)
        this.current = Number(source.current || (reset ? 1 : this.current))
        this.total = Number(source.total || this.records.length)
      } catch (error) {
        this.handleError(error, '库存预警加载失败')
      } finally {
        this.loading = false
        this.scheduleMeasure()
      }
    },
    handleSearch() {
      this.current = 1
      this.queryWarnings(true)
    },
    handleReset() {
      this.supplierId = ''
      this.supplierName = ''
      this.current = 1
      this.handleSearch()
    },
    handleSupplierChange(value) {
      if (!value) {
        this.supplierId = ''
        this.supplierName = ''
      }
    },
    handleSupplierSelected(payload) {
      if (!payload) {
        this.supplierId = ''
        this.supplierName = ''
        return
      }
      this.supplierId = payload.id === undefined || payload.id === null
        ? ''
        : String(payload.id)
      this.supplierName = String(payload.name || '').trim()
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.current += 1
      this.queryWarnings(false)
    },
    handleRefresh() {
      this.refreshing = true
      this.queryWarnings(true).finally(() => { this.refreshing = false })
    },
    warningQuantity(item) {
      const stock = Number(item && item.stock || 0)
      const minStock = Number(item && item.minStock || 0)
      const maxStock = Number(item && item.maxStock || 0)
      if (stock < minStock) return minStock - stock
      return maxStock > 0 && stock > maxStock ? stock - maxStock : 0
    },
    isShortage(item) {
      return Number(item && item.stock || 0) < Number(item && item.minStock || 0)
    },
    isExcess(item) {
      const maxStock = Number(item && item.maxStock || 0)
      return maxStock > 0 && Number(item && item.stock || 0) > maxStock
    },
    warningText(item) {
      return this.isShortage(item)
        ? `低于最低库存 ${this.formatQuantity(item.minStock)}`
        : `高于最高库存 ${this.formatQuantity(item.maxStock)}`
    },
    openDetail(item) {
      if (!item || item.id === undefined || item.id === null) return
      const params = [
        `goodsId=${encodeURIComponent(item.id)}`,
        `goodsName=${encodeURIComponent(item.title || '')}`,
        `unit=${encodeURIComponent(item.unit || '')}`
      ]
      uni.navigateTo({ url: `/pages/stock-detail/index?${params.join('&')}` })
    },
    formatQuantity(value) {
      const number = Number(value || 0)
      if (!Number.isFinite(number)) return '0'
      return Number.isInteger(number) ? String(number) : number.toFixed(2)
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
.filter-panel, .summary-card, .warning-card { background: #fff; border: 1rpx solid #ece9f2; border-radius: 16rpx; box-shadow: 0 6rpx 20rpx rgba(67,47,119,.06); }
.filter-panel { padding: 18rpx; }
.filter-row { display: flex; align-items: center; gap: 12rpx; }
.filter-row :deep(.supplier-select) { flex: 1; min-width: 0; }
.filter-row :deep(.input-wrap) { height: 62rpx; }
.search-button, .reset-button { display: flex; align-items: center; justify-content: center; gap: 6rpx; height: 62rpx; margin: 0; padding: 0 18rpx; font-size: 23rpx; border: 0; border-radius: 10rpx; }
.search-button { flex: 0 0 124rpx; color: #fff; background: #722ed1; }
.reset-button { flex: 0 0 124rpx; color: #666; background: #f2f3f5; }
.search-button::after, .reset-button::after { border: 0; }
.filter-actions { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin-top: 14rpx; }
.filter-hint { flex: 1; min-width: 0; overflow: hidden; color: #918b99; font-size: 20rpx; text-overflow: ellipsis; white-space: nowrap; }
.summary-card { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8rpx; margin-top: 16rpx; padding: 18rpx 8rpx; }
.summary-item { min-width: 0; text-align: center; }
.summary-label { display: block; color: #817a89; font-size: 21rpx; }
.summary-value { display: block; margin-top: 8rpx; color: #e5484d; font-size: 28rpx; font-weight: 600; }
.warning-scroll { flex: 0 0 auto; min-height: 240px; margin-top: 16rpx; }
.warning-list { display: flex; flex-direction: column; gap: 14rpx; }
.warning-card { padding: 18rpx; }
.card-header, .goods-meta, .warning-message { display: flex; align-items: center; }
.card-header { gap: 12rpx; }
.goods-title-wrap { flex: 1; min-width: 0; }
.goods-title { display: block; overflow: hidden; color: #30283d; font-size: 27rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.goods-code { display: block; margin-top: 5rpx; color: #aaa5b5; font-size: 19rpx; }
.goods-meta { gap: 18rpx; margin-top: 12rpx; color: #8b8594; font-size: 21rpx; }
.supplier-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.stock-panel { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 4rpx; margin-top: 18rpx; padding: 16rpx 4rpx; background: #faf9fc; border-radius: 10rpx; }
.stock-item { min-width: 0; text-align: center; }
.stock-label { display: block; color: #918b99; font-size: 19rpx; }
.stock-value { display: block; margin-top: 7rpx; color: #4c4754; font-size: 23rpx; font-weight: 600; }
.stock-value.current.danger { color: #e5484d; }
.stock-value.current.excess { color: #f08a24; }
.warning-item { border-left: 1rpx solid #eeeaf4; }
.warning-value { color: #e5484d; }
.warning-message { gap: 6rpx; margin-top: 14rpx; color: #e5484d; font-size: 21rpx; }
.warning-message.excess { color: #f08a24; }
.empty-state, .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 360rpx; gap: 14rpx; color: #aaa5b5; font-size: 23rpx; }
.bottom-space { height: 24rpx; }
</style>
