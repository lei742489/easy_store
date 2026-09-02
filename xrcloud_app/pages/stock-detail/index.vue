<template>
  <view class="page">
    <view id="detail-top" class="summary-card">
      <view class="title-row">
        <view class="title-main">
          <text class="goods-name">{{ result.goodsName || goodsName || '-' }}</text>
          <text class="goods-unit">单位：{{ result.unit || goodsUnit || '-' }}</text>
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
        <view class="summary-item"><text>期初</text><text>{{ formatQuantity(result.openingQty) }} / ￥{{ formatAmount(result.openingAmount) }}</text></view>
        <view class="summary-item"><text>新增</text><text>{{ formatQuantity(result.inQtyTotal) }} / ￥{{ formatAmount(result.inTotal) }}</text></view>
        <view class="summary-item"><text>减少</text><text>{{ formatQuantity(result.outQtyTotal) }} / ￥{{ formatAmount(result.outTotal) }}</text></view>
        <view class="summary-item"><text>期末</text><text>{{ formatQuantity(result.endingQty) }} / ￥{{ formatAmount(result.endingAmount) }}</text></view>
      </view>
    </view>

    <scroll-view
      class="detail-scroll"
      scroll-y
      :show-scrollbar="false"
      :style="{ height: `${detailScrollHeight}px` }"
    >
      <view v-if="records.length" class="detail-list">
        <view v-for="(item, index) in records" :key="String(item.rowNo || index)" class="detail-card">
          <view class="detail-header">
            <text class="business-type">{{ businessType(item.businessType) || '库存流水' }}</text>
            <text class="business-date">{{ item.businessDate || '-' }}</text>
          </view>
          <text class="counterparty">{{ item.counterpartyName || '库存调整' }}</text>
          <view class="detail-grid">
            <view class="detail-column inbound">
              <text class="column-title">库存新增</text>
              <text>数量：{{ displayQuantity(item.inQty) }}</text>
              <text>单价：￥{{ formatAmount(item.inUnitPrice) }}</text>
              <text>金额：￥{{ formatAmount(item.inAmount) }}</text>
            </view>
            <view class="detail-column outbound">
              <text class="column-title">库存减少</text>
              <text>数量：{{ displayQuantity(item.outQty) }}</text>
              <text>成本：￥{{ formatAmount(item.outCostPrice) }}</text>
              <text>金额：￥{{ formatAmount(item.outAmount) }}</text>
            </view>
          </view>
          <view class="ending-row">
            <text>结存：{{ formatQuantity(item.endingQty) }}</text>
            <text>成本：￥{{ formatAmount(item.endingCostPrice) }}</text>
            <text>金额：￥{{ formatAmount(item.endingAmount) }}</text>
          </view>
        </view>
      </view>
      <view v-else-if="!loading" class="empty-state">
        <uni-icons type="info" color="#b6b0c2" :size="44" />
        <text>暂无库存明细</text>
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
import { getUserInfo, getStockDetail } from '../../common/api'

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
      goodsId: '',
      goodsName: '',
      goodsUnit: '',
      startDate: range[0],
      endDate: range[1],
      dateRange: range,
      result: {},
      records: [],
      loading: false,
      initialized: false,
      redirecting: false,
      detailScrollHeight: 1,
      measureTimer: null
    }
  },
  onReady() {
    this.scheduleMeasure()
  },
  onLoad(options) {
    this.goodsId = options && options.goodsId ? String(options.goodsId) : ''
    this.goodsName = options && options.goodsName ? String(options.goodsName) : ''
    this.goodsUnit = options && options.unit ? String(options.unit) : ''
    if (options && options.startDate) this.startDate = String(options.startDate)
    if (options && options.endDate) this.endDate = String(options.endDate)
    this.dateRange = [this.startDate, this.endDate]
    this.initialize()
  },
  onShow() {
    if (!isLoggedIn()) this.redirectToLogin()
    else {
      this.scheduleMeasure()
      if (this.initialized) this.loadDetail()
    }
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
        this.measureDetailScroll()
      }, 0)
    },
    measureDetailScroll() {
      if (this.redirecting) return
      const systemInfo = uni.getSystemInfoSync()
      try {
        uni.createSelectorQuery()
          .select('#detail-top')
          .boundingClientRect()
          .exec((rects) => {
            const summaryRect = rects && rects[0]
            if (!summaryRect || !summaryRect.height) return
            const summaryBottom = Number(
              summaryRect.bottom || summaryRect.top + summaryRect.height
            )
            const viewportBottom = Number(systemInfo.windowHeight || 0)
            const marginTop = uni.upx2px ? uni.upx2px(16) : 8
            this.detailScrollHeight = Math.max(
              240,
              Math.floor(viewportBottom - summaryBottom - marginTop)
            )
          })
      } catch (error) {
        this.detailScrollHeight = Math.max(
          240,
          Math.floor(Number(systemInfo.windowHeight || 0) * 0.65)
        )
      }
    },
    async initialize() {
      if (!this.goodsId || this.loading || this.redirecting) return
      try {
        const user = await getUserInfo()
        this.user = { ...this.user, ...user }
        updateUser(this.user)
        this.initialized = true
        await this.loadDetail()
        this.scheduleMeasure()
      } catch (error) {
        this.handleError(error, '库存明细加载失败')
      }
    },
    async loadDetail() {
      if (!this.goodsId || this.loading || this.redirecting) return
      this.loading = true
      try {
        const data = await getStockDetail({
          goodsId: this.goodsId,
          startDate: this.startDate,
          endDate: this.endDate
        })
        this.result = data || {}
        this.records = Array.isArray(this.result.records) ? this.result.records : []
      } catch (error) {
        this.handleError(error, '库存明细加载失败')
      } finally {
        this.loading = false
      }
    },
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
      this.startDate = range[0] || undefined
      this.endDate = range[1] || undefined
      this.loadDetail()
    },
    businessType(value) {
      const map = {
        INIT: '期初',
        PURCHASE_IN: '进货入库',
        PURCHASE_RETURN: '采购退货',
        SALE_OUT: '销售出库',
        SALE_RETURN: '销售退货',
        STOCK_CHECK_IN: '库存盘盈',
        STOCK_CHECK_OUT: '库存盘亏'
      }
      return map[value] || value || ''
    },
    displayQuantity(value) {
      if (value === undefined || value === null || Number(value) === 0) return '-'
      return this.formatQuantity(value)
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
.summary-card, .detail-card { background: #fff; border: 1rpx solid #ece9f2; border-radius: 16rpx; box-shadow: 0 6rpx 20rpx rgba(67,47,119,.06); }
.summary-card { flex: 0 0 auto; padding: 20rpx 18rpx; }
.title-row, .date-row, .detail-header, .ending-row { display: flex; align-items: center; }
.title-row { justify-content: space-between; gap: 12rpx; }
.title-main { display: flex; flex-direction: column; min-width: 0; }
.goods-name { overflow: hidden; color: #30283d; font-size: 30rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.goods-unit { margin-top: 7rpx; color: #8b8594; font-size: 21rpx; }
.refresh-button { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 58rpx; margin: 0; padding: 0; background: #f4efff; border: 0; border-radius: 10rpx; }
.refresh-button::after { border: 0; }
.date-row { gap: 12rpx; margin-top: 18rpx; }
.date-label { color: #6f6979; font-size: 22rpx; }
.date-row :deep(.uni-date) { flex: 1; min-width: 0; }
.date-row :deep(.uni-date-editor--x) { height: 62rpx; }
.date-row :deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }
.summary-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14rpx 8rpx; margin-top: 20rpx; padding-top: 18rpx; border-top: 1rpx solid #f0edf5; }
.summary-item { display: flex; justify-content: space-between; gap: 8rpx; color: #817a89; font-size: 21rpx; }
.summary-item text:last-child { color: #7350d1; font-weight: 600; text-align: right; }
.detail-scroll { flex: 0 0 auto; min-height: 0; margin-top: 16rpx; }
.detail-list { display: flex; flex-direction: column; gap: 14rpx; }
.detail-card { padding: 18rpx; }
.detail-header { justify-content: space-between; }
.business-type { color: #722ed1; font-size: 25rpx; font-weight: 600; }
.business-date { color: #8b8594; font-size: 21rpx; }
.counterparty { display: block; margin-top: 8rpx; color: #504b59; font-size: 22rpx; }
.detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12rpx; margin-top: 16rpx; }
.detail-column { display: flex; flex-direction: column; gap: 7rpx; padding: 12rpx; color: #6e6876; font-size: 20rpx; border-radius: 10rpx; }
.detail-column.inbound { background: #f0faf5; }
.detail-column.outbound { background: #fff3f3; }
.column-title { color: #4a4552; font-size: 22rpx; font-weight: 600; }
.ending-row { flex-wrap: wrap; gap: 18rpx; margin-top: 14rpx; padding-top: 12rpx; color: #77717e; font-size: 20rpx; border-top: 1rpx solid #f0edf5; }
.empty-state, .loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 360rpx; gap: 14rpx; color: #aaa5b5; font-size: 23rpx; }
.bottom-space { height: 24rpx; }
</style>
