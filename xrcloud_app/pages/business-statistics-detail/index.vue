<template>
  <view class="page">
    <view id="statistics-detail-top" class="summary-card">
      <view class="title-row">
        <view class="title-main">
          <text class="goods-name">{{ goodsName || '-' }}</text>
          <text class="goods-unit">单位：{{ goodsUnit || '-' }}</text>
        </view>
        <button class="refresh-button" :disabled="loading" @click="loadDetail">
          <uni-icons type="refresh" color="#722ed1" :size="18" />
        </button>
      </view>
      
      <view class="summary-row">
        <text>{{ subject }}明细</text>
        <text>{{ records.length }} 条</text>
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
          v-for="(item, index) in records"
          :key="String(item.rowNo || index)"
          class="detail-card"
        >
          <view class="detail-header">
            <view class="detail-main">
              <text class="detail-order">{{ item.orderNo || '-' }}</text>
              <text class="detail-partner">
                {{ isPurchase ? (item.supplierName || '-') : (item.customerName || '-') }}
              </text>
              <text v-if="!isPurchase && item.cashierName" class="detail-cashier">
                营业员：{{ item.cashierName }}
              </text>
            </view>
            <text class="detail-date">{{ item.businessDate || '-' }}</text>
          </view>
          <view class="detail-values">
            <view class="value-item">
              <text class="value-label">数量</text>
              <text class="value-text">{{ formatQuantity(item.quantity) }}</text>
            </view>
            <view class="value-item">
              <text class="value-label">单价</text>
              <text class="value-text">{{ formatAmount(item.unitPrice) }}</text>
            </view>
            <view class="value-item amount-item">
              <text class="value-label">金额</text>
              <text class="value-text amount">{{ formatAmount(finalAmount(item)) }}</text>
            </view>
          </view>
          <text v-if="item.note" class="detail-note">备注：{{ item.note }}</text>
        </view>
      </view>
      <view v-else-if="loading" class="state">
        <uni-icons type="spinner-cycle" color="#722ed1" :size="28" />
        <text>正在加载明细...</text>
      </view>
      <view v-else class="state">
        <uni-icons type="info" color="#b6b0c2" :size="42" />
        <text>暂无明细数据</text>
      </view>
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import {
  getUserInfo,
  listPurchaseStatisticsDetail,
  listSaleStatisticsDetail
} from '../../common/api'

const pad = (value) => String(value).padStart(2, '0')

const monthRange = () => {
  const date = new Date()
  const endDate = new Date(date.getFullYear(), date.getMonth() + 1, 0)
  return [
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-01`,
    `${endDate.getFullYear()}-${pad(endDate.getMonth() + 1)}-${pad(endDate.getDate())}`
  ]
}

export default {
  data() {
    const range = monthRange()
    return {
      user: getUser() || {},
      mode: 'sale',
      goodsId: '',
      goodsName: '',
      goodsUnit: '',
      goodsKey: '',
      categoryId: '',
      cashierId: '',
      customerId: '',
      supplierId: '',
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
    isPurchase() {
      return this.mode === 'purchase'
    },
    subject() {
      return this.isPurchase ? '进货' : '销售'
    }
  },
  onLoad(options) {
    const query = options || {}
    this.mode = query.mode === 'purchase' ? 'purchase' : 'sale'
    this.goodsId = String(query.goodsId || '')
    this.goodsName = String(query.goodsName || '')
    this.goodsUnit = String(query.unit || '')
    this.goodsKey = String(query.goodsKey || '')
    this.categoryId = String(query.categoryId || '')
    this.cashierId = String(query.cashierId || '')
    this.customerId = String(query.customerId || '')
    this.supplierId = String(query.supplierId || '')
    if (query.startDate) this.startDate = String(query.startDate)
    if (query.endDate) this.endDate = String(query.endDate)
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
          .select('#statistics-detail-top')
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
      if (!this.goodsId || this.loading || this.redirecting) return
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...(currentUser || {}) }
        updateUser(this.user)
        this.initialized = true
        await this.loadDetail()
        this.scheduleMeasure()
      } catch (error) {
        this.handleError(error, `${this.subject}明细加载失败`)
      }
    },
    buildQuery() {
      const query = {
        userId: this.user.id || '',
        goodsId: this.goodsId,
        goodsKey: this.goodsKey || undefined,
        categoryId: this.categoryId || undefined,
        cashierId: this.cashierId || undefined,
        customerId: this.isPurchase ? undefined : (this.customerId || undefined),
        supplierId: this.isPurchase ? (this.supplierId || undefined) : undefined,
        startDate: this.startDate || undefined,
        endDate: this.endDate || undefined
      }
      const result = {}
      Object.keys(query).forEach((key) => {
        const value = query[key]
        if (value !== undefined && value !== '') result[key] = value
      })
      return result
    },
    async loadDetail() {
      if (!this.goodsId || this.loading || this.redirecting) return
      this.loading = true
      try {
        const api = this.isPurchase
          ? listPurchaseStatisticsDetail
          : listSaleStatisticsDetail
        const result = await api(this.buildQuery())
        this.records = Array.isArray(result) ? result : []
      } catch (error) {
        this.handleError(error, `${this.subject}明细加载失败`)
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
    handleRefresh() {
      this.refreshing = true
      this.loadDetail().finally(() => {
        this.refreshing = false
      })
    },
    finalAmount(item) {
      return this.isPurchase
        ? (item.payableAmount ?? item.amount)
        : (item.receivableAmount ?? item.amount)
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
.detail-card {
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
}

.summary-card {
  flex: 0 0 auto;
  padding: 20rpx 18rpx;
}

.title-row,
.date-row,
.detail-header,
.summary-row {
  display: flex;
  align-items: center;
}

.title-row,
.summary-row {
  justify-content: space-between;
  gap: 12rpx;
}

.title-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.goods-name {
  overflow: hidden;
  color: #30283d;
  font-size: 30rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-unit,
.detail-cashier,
.detail-date,
.value-label {
  color: #8b8594;
  font-size: 21rpx;
}

.goods-unit {
  margin-top: 7rpx;
}

.refresh-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 58rpx;
  margin: 0;
  padding: 0;
  background: #f4efff;
  border: 0;
  border-radius: 10rpx;
}

.refresh-button::after {
  border: 0;
}

.date-row {
  gap: 12rpx;
  margin-top: 18rpx;
}

.date-label {
  flex: 0 0 auto;
  color: #6f6979;
  font-size: 22rpx;
}

.date-row :deep(.uni-date) {
  flex: 1;
  min-width: 0;
}

.date-row :deep(.uni-date-editor--x) {
  height: 62rpx;
}

.date-row :deep(.uni-date-x--border) {
  border-color: #ded9e8;
  border-radius: 10rpx;
}

.summary-row {
  margin-top: 18rpx;
  padding-top: 16rpx;
  color: #77717e;
  font-size: 22rpx;
  border-top: 1rpx solid #f0edf5;
}

.summary-row text:last-child {
  color: #722ed1;
  font-weight: 600;
}

.detail-scroll {
  flex: 0 0 auto;
  min-height: 0;
  margin-top: 16rpx;
}

.detail-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.detail-card {
  padding: 18rpx;
}

.detail-header {
  justify-content: space-between;
  gap: 16rpx;
}

.detail-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 6rpx;
}

.detail-order {
  overflow: hidden;
  color: #30283d;
  font-size: 25rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-partner {
  overflow: hidden;
  color: #504b59;
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-date {
  flex: 0 0 auto;
}

.detail-values {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8rpx;
  margin-top: 16rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid #f0edf5;
}

.value-item {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  min-width: 0;
}

.value-text {
  color: #3d3944;
  font-size: 24rpx;
  font-weight: 600;
}

.amount-item {
  align-items: flex-end;
}

.amount {
  color: #722ed1;
}

.detail-note {
  display: block;
  margin-top: 14rpx;
  padding-top: 12rpx;
  overflow: hidden;
  color: #77717e;
  font-size: 21rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-top: 1rpx solid #f0edf5;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 360rpx;
  gap: 14rpx;
  color: #aaa5b5;
  font-size: 23rpx;
}

.bottom-space {
  height: 24rpx;
}
</style>
