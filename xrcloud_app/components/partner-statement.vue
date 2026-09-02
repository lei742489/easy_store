<template>
  <view class="page">
    <view id="statement-top" class="top-area">
      <view class="filter-panel">
        <view class="filter-row">
          <customer-autocomplete
            v-if="!isPayable"
            v-model="partnerName"
            placeholder="请选择客户"
            @select="handlePartnerChange"
            @select-item="handlePartnerSelected"
          />
          <supplier-autocomplete
            v-else
            v-model="partnerName"
            placeholder="请选择供应商"
            @select="handlePartnerChange"
            @select-item="handlePartnerSelected"
          />
          <button class="search-button" :disabled="loading" @click="queryStatement">
            <uni-icons type="search" color="#fff" :size="18" />
            <text>查询</text>
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
      </view>

      <view v-if="hasResult" class="summary-card">
        <view class="summary-heading">
          <text class="summary-title">{{ title }}</text>
          <text class="summary-name">{{ resultPartnerName || partnerName || '-' }}</text>
        </view>
        <view class="summary-grid">
          <view class="summary-item">
            <text class="summary-label">期初余额</text>
            <text class="summary-value">{{ formatAmount(result.openingBalance) }}</text>
          </view>
          <view class="summary-item">
            <text class="summary-label">{{ isPayable ? '增加应付款' : '增加应收款' }}</text>
            <text class="summary-value increase">{{ formatAmount(increaseTotal) }}</text>
          </view>
          <view class="summary-item">
            <text class="summary-label">{{ isPayable ? '付出应付款' : '收回应收款' }}</text>
            <text class="summary-value decrease">{{ formatAmount(decreaseTotal) }}</text>
          </view>
          <view class="summary-item">
            <text class="summary-label">期末余额</text>
            <text class="summary-value ending">{{ formatAmount(result.endingBalance) }}</text>
          </view>
        </view>
      </view>
    </view>

    <scroll-view
      class="statement-scroll"
      scroll-y
      :show-scrollbar="false"
      :style="{ height: `${scrollHeight}px` }"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="handleRefresh"
    >
      <view v-if="records.length" class="statement-list">
        <view
          v-for="record in records"
          :key="String(record.rowNo)"
          class="statement-row"
          :class="rowClass(record)"
        >
          <view class="row-main">
            <view class="row-title-line">
              <text class="row-title">{{ record.goodsName || '-' }}</text>
              <text v-if="record.rowType === 'item'" class="item-mark">货品</text>
            </view>
            <view class="row-meta">
              <text v-if="record.unit">单位：{{ record.unit }}</text>
              <text v-if="record.quantity !== undefined && record.quantity !== null">
                数量：{{ formatQuantity(record.quantity) }}
              </text>
              <text v-if="record.unitPrice !== undefined && record.unitPrice !== null">
                单价：{{ formatAmount(record.unitPrice) }}
              </text>
            </view>
            <view v-if="record.note" class="row-note">{{ record.note }}</view>
          </view>
          <view class="row-right">
            <text
              v-if="record.totalAmount !== undefined && record.totalAmount !== null"
              class="row-total"
            >
               {{ formatAmount(record.totalAmount) }}
            </text>
            <text
              v-if="record.freightAmount !== undefined && Number(record.freightAmount || 0) !== 0"
              class="row-extra"
            >
              运费 {{ formatAmount(record.freightAmount) }}
            </text>
            <text v-if="getIncrease(record) !== 0" class="row-amount increase">
              +{{ formatAmount(getIncrease(record)) }}
            </text>
            <text v-if="getDecrease(record) !== 0" class="row-amount decrease">
              -{{ formatAmount(getDecrease(record)) }}
            </text>
            <!--<text v-if="record.endingBalance !== undefined" class="row-balance">
              余额 {{ formatAmount(record.endingBalance) }}
            </text>-->
          </view>
        </view>
      </view>

      <view v-else-if="loading" class="state">
        <uni-icons type="spinner-cycle" color="#722ed1" :size="30" />
        <text>正在加载...</text>
      </view>
      <view v-else class="state">
        <uni-icons type="info" color="#b6b0c2" :size="44" />
        <text>{{ partnerId ? '暂无对账明细' : `请选择${isPayable ? '供应商' : '客户'}后查询` }}</text>
      </view>
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../common/auth'
import {
  getUserInfo,
  listPayableStatement,
  listReceivableStatement
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

export default {
  components: {
    CustomerAutocomplete,
    SupplierAutocomplete
  },
  props: {
    mode: {
      type: String,
      default: 'receivable'
    },
    initialPartnerId: {
      type: [String, Number],
      default: ''
    },
    initialStartDate: {
      type: String,
      default: ''
    },
    initialEndDate: {
      type: String,
      default: ''
    }
  },
  data() {
    const range = monthRange()
    return {
      user: getUser() || {},
      partnerId: this.initialPartnerId ? String(this.initialPartnerId) : '',
      partnerName: '',
      startDate: this.initialStartDate || range[0],
      endDate: this.initialEndDate || range[1],
      dateRange: [
        this.initialStartDate || range[0],
        this.initialEndDate || range[1]
      ],
      records: [],
      result: {},
      loading: false,
      refreshing: false,
      initialized: false,
      initializing: false,
      redirecting: false,
      scrollHeight: 1,
      measureTimer: null
    }
  },
  computed: {
    isPayable() {
      return this.mode === 'payable'
    },
    title() {
      return this.isPayable ? '应付对账单' : '应收对账单'
    },
    resultPartnerName() {
      return this.isPayable ? this.result.supplierName : this.result.customerName
    },
    increaseTotal() {
      return this.isPayable ? this.result.payableTotal : this.result.receivableTotal
    },
    decreaseTotal() {
      return this.isPayable ? this.result.paidTotal : this.result.receivedTotal
    },
    hasResult() {
      return Boolean(this.result && (this.result.customerId || this.result.supplierId))
    }
  },
  mounted() {
    this.handlePageShow()
  },
  methods: {
    async handlePageShow() {
      if (!isLoggedIn()) {
        this.redirectToLogin()
        return
      }
      this.scheduleMeasure()
      if (!this.initialized) {
        await this.initialize()
        return
      }
      if (this.partnerId) await this.queryStatement()
    },
    async initialize() {
      if (this.initializing || this.redirecting) return
      this.initializing = true
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...currentUser }
        updateUser(this.user)
        this.initialized = true
        if (this.partnerId) await this.queryStatement()
      } catch (error) {
        this.handleError(error, `${this.title}加载失败`)
      } finally {
        this.initializing = false
        this.scheduleMeasure()
      }
    },
    buildQuery() {
      const query = {
        userId: this.user.id,
        startDate: this.startDate || undefined,
        endDate: this.endDate || undefined
      }
      query[this.isPayable ? 'supplierId' : 'customerId'] = this.partnerId
      return query
    },
    async queryStatement() {
      if (this.loading || this.redirecting) return
      if (!this.partnerId) {
        this.records = []
        this.result = {}
        uni.showToast({
          title: `请选择${this.isPayable ? '供应商' : '客户'}`,
          icon: 'none'
        })
        return
      }
      this.loading = true
      try {
        const data = this.isPayable
          ? await listPayableStatement(this.buildQuery())
          : await listReceivableStatement(this.buildQuery())
        this.result = data || {}
        this.records = Array.isArray(this.result.records) ? this.result.records : []
        if (this.resultPartnerName) this.partnerName = this.resultPartnerName
      } catch (error) {
        this.handleError(error, `${this.title}加载失败`)
      } finally {
        this.loading = false
        this.scheduleMeasure()
      }
    },
    handlePartnerChange(value) {
      if (!value) {
        this.partnerId = ''
        this.partnerName = ''
        this.records = []
        this.result = {}
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
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
      this.startDate = range[0] || ''
      this.endDate = range[1] || ''
    },
    handleRefresh() {
      this.refreshing = true
      this.queryStatement().finally(() => {
        this.refreshing = false
      })
    },
    rowClass(record) {
      if (record.rowType === 'opening') return 'opening-row'
      if (record.rowType === 'item') return 'item-row'
      if (record.rowType === (this.isPayable ? 'purchase' : 'sale')) return 'document-row'
      return 'payment-row'
    },
    getIncrease(record) {
      return Number(this.isPayable ? record.payableAmount : record.receivableAmount) || 0
    },
    getDecrease(record) {
      return Number(this.isPayable ? record.paidAmount : record.receivedAmount) || 0
    },
    formatAmount(value) {
      const amount = Number(value || 0)
      return Number.isFinite(amount) ? `¥${amount.toFixed(2)}` : '¥0.00'
    },
    formatQuantity(value) {
      const quantity = Number(value || 0)
      if (!Number.isFinite(quantity)) return '0'
      return Number.isInteger(quantity) ? String(quantity) : quantity.toFixed(2)
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
        this.measureScrollHeight()
      }, 0)
    },
    measureScrollHeight() {
      if (this.redirecting) return
      const systemInfo = uni.getSystemInfoSync()
      try {
        uni.createSelectorQuery()
          .select('#statement-top')
          .boundingClientRect()
          .exec((rects) => {
            const topRect = rects && rects[0]
            if (!topRect || !topRect.height) return
            const topBottom = Number(topRect.bottom || topRect.top + topRect.height)
            const windowHeight = Number(systemInfo.windowHeight || 0)
            const marginTop = uni.upx2px ? uni.upx2px(16) : 8
            this.scrollHeight = Math.max(
              240,
              Math.floor(windowHeight - topBottom - marginTop)
            )
          })
      } catch (error) {
        this.scrollHeight = Math.max(
          240,
          Math.floor(Number(systemInfo.windowHeight || 0) * 0.65)
        )
      }
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

.top-area {
  flex: 0 0 auto;
}

.filter-panel,
.summary-card,
.statement-list {
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
}

.filter-panel {
  padding: 18rpx;
}

.filter-row,
.date-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.filter-row :deep(.customer-select),
.filter-row :deep(.supplier-select) {
  flex: 1;
  min-width: 0;
}

.filter-row :deep(.input-wrap) {
  height: 64rpx;
}

.search-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  flex: 0 0 124rpx;
  height: 64rpx;
  margin: 0;
  padding: 0 10rpx;
  color: #fff;
  font-size: 23rpx;
  line-height: 64rpx;
  background: #722ed1;
  border: 0;
  border-radius: 10rpx;
}

.search-button::after {
  border: 0;
}

.search-button[disabled] {
  opacity: .6;
}

.date-row {
  margin-top: 14rpx;
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
  height: 64rpx;
}

.date-row :deep(.uni-date-x--border) {
  border-color: #ded9e8;
  border-radius: 10rpx;
}

.summary-card {
  margin-top: 16rpx;
  overflow: hidden;
}

.summary-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14rpx;
  padding: 18rpx 20rpx;
  background: linear-gradient(135deg, #fff 0%, #f8f3ff 100%);
  border-bottom: 1rpx solid #eee9f5;
}

.summary-title {
  color: #77717e;
  font-size: 22rpx;
}

.summary-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #30283d;
  font-size: 28rpx;
  font-weight: 600;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4rpx;
  padding: 18rpx 6rpx;
}

.summary-item {
  min-width: 0;
  text-align: center;
}

.summary-label {
  display: block;
  color: #817a89;
  font-size: 19rpx;
}

.summary-value {
  display: block;
  margin-top: 8rpx;
  color: #7350d1;
  font-size: 22rpx;
  font-weight: 600;
  white-space: nowrap;
}

.summary-value.increase {
  color: #d56b35;
}

.summary-value.decrease {
  color: #537fe8;
}

.summary-value.ending {
  color: #722ed1;
}

.statement-scroll {
  flex: 0 0 auto;
  min-height: 240px;
  margin-top: 16rpx;
}

.statement-list {
  overflow: hidden;
}

.statement-row {
  display: flex;
  justify-content: space-between;
  gap: 14rpx;
  min-height: 112rpx;
  padding: 18rpx;
  box-sizing: border-box;
  border-bottom: 1rpx solid #f0edf4;
}

.statement-row:last-child {
  border-bottom: 0;
}

.statement-row.opening-row,
.statement-row.payment-row {
  background: #fffde8;
}

.statement-row.document-row {
  background: #edf9ff;
}

.statement-row.item-row {
  padding-left: 34rpx;
  background: #fff;
}

.row-main {
  flex: 1;
  min-width: 0;
}

.row-title-line {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.row-title {
  overflow: hidden;
  color: #30283d;
  font-size: 25rpx;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-row .row-title {
  color: #585260;
  font-size: 23rpx;
  font-weight: 500;
}

.item-mark {
  flex: 0 0 auto;
  padding: 3rpx 8rpx;
  color: #7c6cae;
  font-size: 17rpx;
  background: #f2edff;
  border-radius: 6rpx;
}

.row-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx 16rpx;
  margin-top: 9rpx;
  color: #8b8594;
  font-size: 19rpx;
}

.row-note {
  margin-top: 8rpx;
  overflow: hidden;
  color: #77717e;
  font-size: 20rpx;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-right {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  min-width: 150rpx;
  text-align: right;
}

.row-total,
.row-extra,
.row-amount,
.row-balance {
  white-space: nowrap;
}

.row-total {
  color: #5e5968;
  font-size: 21rpx;
}

.row-extra {
  margin-top: 5rpx;
  color: #8b8594;
  font-size: 19rpx;
}

.row-amount {
  margin-top: 6rpx;
  font-size: 22rpx;
  font-weight: 600;
}

.row-amount.increase {
  color: #d56b35;
}

.row-amount.decrease {
  color: #537fe8;
}

.row-balance {
  margin-top: 8rpx;
  color: #77717e;
  font-size: 19rpx;
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
