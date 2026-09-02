<template>
  <view class="page">
    <view class="summary-card">
      <text class="summary-title">{{ title }}</text>
      <text class="summary-name">{{ partnerName || '-' }}</text>
      <view class="summary-grid">
        <view class="summary-item">
          <text class="summary-label">期初余额</text>
          <text class="summary-value">¥ {{ formatAmount(result.openingBalance) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">{{ isPayable ? '增加应付款' : '增加应收款' }}</text>
          <text class="summary-value">¥ {{ formatAmount(increaseTotal) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">{{ isPayable ? '付出应付款' : '收回应收款' }}</text>
          <text class="summary-value">¥ {{ formatAmount(paidTotal) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">期末余额</text>
          <text class="summary-value">¥ {{ formatAmount(result.endingBalance) }}</text>
        </view>
      </view>
    </view>

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
        <button class="search-button" :disabled="loading" @click="loadDetail">
          <uni-icons type="search" color="#fff" :size="18" />
          <text>查询</text>
        </button>
      </view>
      <view class="date-filter" v-if="dateRange.length>0">
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

    <view v-if="records.length" class="detail-list">
      <view v-for="item in records" :key="String(item.rowNo)" class="detail-row">
        <view class="detail-main">
          <text class="detail-date">{{ item.businessDate || '-' }}</text>
          <text class="detail-summary">{{ item.summary || item.orderNo || '-' }}</text>
          <text v-if="item.orderNo" class="detail-order">{{ item.orderNo }}</text>
        </view>
        <view class="detail-amounts">
          <text v-if="Number(item.receivableAmount || item.payableAmount || 0) !== 0" class="amount increase">
            +¥ {{ formatAmount(item.receivableAmount || item.payableAmount) }}
          </text>
          <text v-if="Number(item.receivedAmount || item.paidAmount || 0) !== 0" class="amount paid">
            -¥ {{ formatAmount(item.receivedAmount || item.paidAmount) }}
          </text>
          <text class="balance">余额 ¥ {{ formatAmount(item.endingBalance) }}</text>
        </view>
      </view>
    </view>

    <view v-else-if="!loading" class="empty-state">
      <uni-icons type="info" color="#b6b0c2" :size="42" />
      <text>暂无明细数据</text>
    </view>

    <view v-if="loading" class="loading-state">
      <uni-icons type="spinner-cycle" color="#722ed1" :size="28" />
      <text>正在加载...</text>
    </view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../common/auth'
import { getUserInfo, listDebtDetail, listPayableDetail } from '../common/api'
import CustomerAutocomplete from './customer-autocomplete.vue'
import SupplierAutocomplete from './supplier-autocomplete.vue'

export default {
  components: {
    CustomerAutocomplete,
    SupplierAutocomplete
  },
  props: {
    mode: {
      type: String,
      default: 'debt'
    },
    partnerId: {
      type: [String, Number],
      default: ''
    },
    startDate: {
      type: String,
      default: ''
    },
    endDate: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      user: getUser() || {},
      loading: false,
      redirecting: false,
      partnerName: '',
      selectedPartnerId: this.partnerId,
      queryStartDate: this.startDate || '',
      queryEndDate: this.endDate || '',
      dateRange: [],
      result: {},
      records: []
    }
  },
  computed: {
    isPayable() {
      return this.mode === 'payable'
    },
    title() {
      return this.isPayable ? '应付明细' : '欠款明细'
    },
    increaseTotal() {
      return this.isPayable ? this.result.payableTotal : this.result.receivableTotal
    },
    paidTotal() {
      return this.isPayable ? this.result.paidTotal : this.result.receivedTotal
    }
  },
  mounted() {
    this.dateRange = [
      this.startDate || this.startOfMonth(),
      this.endDate || this.endOfMonth()
    ]
    this.queryStartDate = this.dateRange[0]
    this.queryEndDate = this.dateRange[1]
	console.log(this.dateRange);
    this.loadDetail()
  },
  onShow() {
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    this.loadDetail()
  },
  methods: {
    async loadDetail() {
      if (this.loading || this.redirecting || !this.selectedPartnerId) {
        if (!this.selectedPartnerId) {
          this.records = []
          uni.showToast({ title: `请选择${this.isPayable ? '供应商' : '客户'}`, icon: 'none' })
        }
        return
      }
      this.loading = true
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...currentUser }
        updateUser(this.user)
        const query = {
          userId: this.user.id,
          startDate: this.queryStartDate || undefined,
          endDate: this.queryEndDate || undefined,
          ...(this.isPayable
            ? { supplierId: this.selectedPartnerId }
            : { customerId: this.selectedPartnerId })
        }
        const data = this.isPayable
          ? await listPayableDetail(query)
          : await listDebtDetail(query)
        this.result = data || {}
        this.partnerName = this.isPayable
          ? this.result.supplierName || ''
          : this.result.customerName || ''
        this.records = Array.isArray(this.result.records) ? this.result.records : []
      } catch (error) {
        this.handleError(error, '明细加载失败')
      } finally {
        this.loading = false
      }
    },
    startOfMonth() {
      const date = new Date()
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01`
    },
    endOfMonth() {
      const date = new Date()
      const end = new Date(date.getFullYear(), date.getMonth() + 1, 0)
      return `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, '0')}-${String(end.getDate()).padStart(2, '0')}`
    },
    handlePartnerChange(value) {
      if (!value) {
        this.selectedPartnerId = ''
        this.partnerName = ''
        this.result = {}
        this.records = []
      }
    },
    handlePartnerSelected(payload) {
      if (!payload) {
        this.selectedPartnerId = ''
        this.partnerName = ''
        return
      }
      this.selectedPartnerId = payload.id === undefined || payload.id === null
        ? ''
        : String(payload.id)
      this.partnerName = String(payload.name || '').trim()
    },
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      
      this.dateRange = range
      this.queryStartDate = range[0] || ''
      this.queryEndDate = range[1] || ''
    },
    formatAmount(value) {
      const amount = Number(value || 0)
      return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
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
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 18rpx 14rpx calc(24rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  color: #34313d;
  background: #f5f4fb;
}

.summary-card,
.detail-list {
  overflow: hidden;
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
}

.summary-card {
  padding: 24rpx 20rpx 20rpx;
  background: linear-gradient(135deg, #fff 0%, #f8f3ff 100%);
  border-color: #e7daf8;
}

.summary-title {
  display: block;
  color: #77717e;
  font-size: 23rpx;
}

.summary-name {
  display: block;
  margin-top: 8rpx;
  color: #30283d;
  font-size: 32rpx;
  font-weight: 600;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20rpx 8rpx;
  margin-top: 22rpx;
}

.summary-item {
  min-width: 0;
}

.summary-label {
  display: block;
  color: #8b8491;
  font-size: 21rpx;
}

.summary-value {
  display: block;
  margin-top: 7rpx;
  color: #7350d1;
  font-size: 24rpx;
  font-weight: 600;
}

.filter-panel {
  margin-top: 16rpx;
  padding: 18rpx;
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
}

.filter-row {
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

.date-filter {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 14rpx;
}

.date-label {
  flex: 0 0 auto;
  color: #6f6979;
  font-size: 22rpx;
}

.date-filter :deep(.uni-date) {
  flex: 1;
  min-width: 0;
}

.date-filter :deep(.uni-date-editor--x) {
  height: 64rpx;
}

.date-filter :deep(.uni-date-x--border) {
  border-color: #ded9e8;
  border-radius: 10rpx;
}

.detail-list {
  margin-top: 16rpx;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  gap: 14rpx;
  min-height: 118rpx;
  padding: 20rpx 18rpx;
  box-sizing: border-box;
  border-bottom: 1rpx solid #f0edf4;
}

.detail-row:last-child {
  border-bottom: 0;
}

.detail-main,
.detail-amounts {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.detail-main {
  flex: 1;
}

.detail-amounts {
  flex: 0 0 auto;
  align-items: flex-end;
}

.detail-date {
  color: #77717e;
  font-size: 21rpx;
}

.detail-summary {
  margin-top: 8rpx;
  overflow: hidden;
  color: #3d3944;
  font-size: 25rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-order {
  margin-top: 6rpx;
  color: #aaa5b5;
  font-size: 20rpx;
}

.amount {
  font-size: 22rpx;
  font-weight: 600;
}

.amount.increase {
  color: #7350d1;
}

.amount.paid {
  margin-top: 5rpx;
  color: #537fe8;
}

.balance {
  margin-top: 8rpx;
  color: #77717e;
  font-size: 20rpx;
}

.loading-state,
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
</style>
