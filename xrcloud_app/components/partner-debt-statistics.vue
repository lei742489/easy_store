<template>
  <view class="page">
    <view class="summary-card">
      <view class="summary-main">
        <view class="summary-main-item">
          <text class="summary-label">期初{{ subject }}款</text>
          <text class="summary-value">¥ {{ formatAmount(summary.openingTotal) }}</text>
        </view>
        <view class="summary-main-item">
          <text class="summary-label">期末{{ subject }}款</text>
          <text class="summary-value">¥ {{ formatAmount(summary.endingTotal) }}</text>
        </view>
      </view>

      <view class="summary-sub">
        <view class="summary-sub-item">
          <text class="summary-label">增加{{ subject }}款</text>
          <text class="summary-sub-value">{{ formatAmount(summary.increaseTotal) }}</text>
        </view>
        <view class="summary-sub-item">
          <text class="summary-label">{{ subject === '应收' ? '收回' : '付出' }}{{ subject }}款</text>
          <text class="summary-sub-value blue">{{ formatAmount(summary.paidTotal) }}</text>
        </view>
        <view class="summary-sub-item">
          <text class="summary-label">抹零</text>
          <text class="summary-sub-value orange">{{ formatAmount(0) }}</text>
        </view>
        <view class="summary-sub-item">
          <text class="summary-label">{{ subject === '应收' ? '客户' : '供应商' }}数量</text>
          <text class="summary-sub-value">{{ records.length }}</text>
        </view>
      </view>
    </view>

    <view class="filter-panel">
      <view class="filter-row">
        <customer-autocomplete
          v-if="!isPayable"
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
        <button class="search-button" :disabled="loading" @click="queryStatistics">
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

    <view class="table-card">
      <view class="table-header">
        
        <text class="name-cell">{{ subject === '应收' ? '客户名称' : '供应商名称' }}</text>
        <text class="amount-cell">期初{{ subject }}款</text>
        <view class="amount-cell sortable" @click="toggleSort">
          <text>期末{{ subject }}款</text>
            <uni-icons
            :type="sortAsc === null ? 'arrowdown' : (sortAsc ? 'arrowup' : 'arrowdown')"
            color="#b2adb9"
            :size="13"
          />
        </view>
        <text class="arrow-cell"></text>
      </view>

      <view v-if="loading && !records.length" class="loading-state">
        <uni-icons type="spinner-cycle" color="#722ed1" :size="28" />
        <text>正在加载...</text>
      </view>

      <view v-else-if="filteredRecords.length" class="table-body">
        <view
          v-for="(item, index) in filteredRecords"
          :key="String(item.id || item.name || index)"
          class="table-row"
          @click="openDetail(item)"
        >
          
          <text class="name-cell">{{ item.name || '-' }}</text>
          <text class="amount-cell" :class="{ negative: isNegative(item.openingBalance) }">
            ¥ {{ formatAmount(item.openingBalance) }}
          </text>
          <text class="amount-cell" :class="{ negative: isNegative(item.endingBalance) }">
            ¥ {{ formatAmount(item.endingBalance) }}
          </text>
          <view class="arrow-cell">
            <uni-icons type="right" color="#aaa5b5" :size="17" />
          </view>
        </view>
      </view>

      <view v-else-if="!loading" class="empty-state">
        <uni-icons type="info" color="#b6b0c2" :size="42" />
        <text>暂无{{ subject }}统计数据</text>
      </view>
    </view>

    <view class="bottom-space" />
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../common/auth'
import {
  getUserInfo,
  listDebtStatistics,
  listPayableStatistics
} from '../common/api'
import CustomerAutocomplete from './customer-autocomplete.vue'
import SupplierAutocomplete from './supplier-autocomplete.vue'

const startOfMonth = () => {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01`
}

const endOfMonth = () => {
  const date = new Date()
  const end = new Date(date.getFullYear(), date.getMonth() + 1, 0)
  return `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, '0')}-${String(end.getDate()).padStart(2, '0')}`
}

export default {
  components: {
    CustomerAutocomplete,
    SupplierAutocomplete
  },
  props: {
    mode: {
      type: String,
      default: 'debt'
    }
  },
  data() {
    return {
      user: getUser() || {},
      partnerId: '',
      partnerName: '',
      loading: false,
      initialized: false,
      initializing: false,
      redirecting: false,
      records: [],
      summary: {
        openingTotal: 0,
        increaseTotal: 0,
        paidTotal: 0,
        endingTotal: 0
      },
      startDate: startOfMonth(),
      endDate: endOfMonth(),
      dateRange: [startOfMonth(), endOfMonth()],
      sortAsc: null
    }
  },
  computed: {
    isPayable() {
      return this.mode === 'payable'
    },
    subject() {
      return this.isPayable ? '应付' : '应收'
    },
    filteredRecords() {
      const list = this.records.slice()
      if (this.sortAsc === null) return list
      return list.sort((a, b) => {
        const left = Number(a.endingBalance || 0)
        const right = Number(b.endingBalance || 0)
        return this.sortAsc ? left - right : right - left
      })
    },
    detailPath() {
      return this.isPayable ? '/pages/payable-detail/index' : '/pages/debt-detail/index'
    }
  },
  mounted() {
    this.initialize()
  },
  onShow() {
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    if (!this.initialized) this.initialize()
    else this.queryStatistics()
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
        await this.queryStatistics()
      } catch (error) {
        this.handleError(error, '统计数据加载失败')
      } finally {
        this.initializing = false
      }
    },
    async queryStatistics() {
      if (this.loading || this.redirecting) return
      this.loading = true
      try {
        const query = {
          userId: this.user.id,
          startDate: this.startDate,
          endDate: this.endDate
        }
        if (this.partnerId) {
          query[this.isPayable ? 'supplierId' : 'customerId'] = this.partnerId
        }
        const data = this.isPayable
          ? await listPayableStatistics(query)
          : await listDebtStatistics(query)
        const source = data || {}
        this.summary = {
          openingTotal: Number(source.openingTotal || 0),
          increaseTotal: Number(this.isPayable ? source.payableTotal || 0 : source.receivableTotal || 0),
          paidTotal: Number(source.paidTotal || (this.isPayable ? 0 : source.receivedTotal || 0)),
          endingTotal: Number(source.endingTotal || 0)
        }
        this.records = (Array.isArray(source.records) ? source.records : [])
          .filter((item) => item && (item.id !== undefined || item.customerId !== undefined || item.supplierId !== undefined))
          .map((item) => ({
            ...item,
            id: item.id || (this.isPayable ? item.supplierId : item.customerId),
            name: item.name || (this.isPayable ? item.supplierName : item.customerName)
          }))
      } catch (error) {
        this.handleError(error, '统计数据加载失败')
      } finally {
        this.loading = false
      }
    },
    toggleSort() {
      if (this.sortAsc === null) {
        this.sortAsc = false
      } else if (this.sortAsc === false) {
        this.sortAsc = true
      } else {
        this.sortAsc = null
      }
    },
    refresh() {
      return this.queryStatistics()
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
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
	  
      this.startDate = range[0] || undefined
      this.endDate = range[1] || undefined
    },
    openDetail(item) {
      if (!item || item.id === undefined || item.id === null) return
      const params = [`id=${encodeURIComponent(item.id)}`]
      if (this.startDate) {
        params.push(`startDate=${encodeURIComponent(this.startDate)}`)
      }
      if (this.endDate) {
        params.push(`endDate=${encodeURIComponent(this.endDate)}`)
      }
      const query = params.join('&')
      uni.navigateTo({ url: `${this.detailPath}?${query}` })
    },
    formatAmount(value) {
      const amount = Number(value || 0)
      return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
    },
    isNegative(value) {
      return Number(value || 0) < 0
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
.table-card {
  overflow: hidden;
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
}

.summary-card {
  background: linear-gradient(135deg, #fff 0%, #f8f3ff 100%);
  border-color: #e7daf8;
}

.summary-main {
  display: flex;
  padding: 24rpx 8rpx 22rpx;
  border-bottom: 1rpx solid #eee9f5;
}

.summary-main-item,
.summary-sub-item {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.summary-label {
  display: block;
  color: #726d79;
  font-size: 22rpx;
  line-height: 1.3;
}

.summary-value {
  display: block;
  margin-top: 10rpx;
  color: #7350d1;
  font-size: 31rpx;
  font-weight: 600;
}

.summary-sub {
  display: flex;
  padding: 22rpx 4rpx 24rpx;
}

.summary-sub-value {
  display: block;
  margin-top: 8rpx;
  color: #8b62d6;
  font-size: 24rpx;
  font-weight: 600;
}

.summary-sub-value.blue { color: #537fe8; }
.summary-sub-value.orange { color: #f28c3d; }

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

.search-bar {
  display: flex;
  align-items: center;
  height: 62rpx;
  margin: 16rpx 0;
  padding: 0 22rpx;
  box-sizing: border-box;
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 12rpx;
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  margin-left: 12rpx;
  color: #34313d;
  font-size: 24rpx;
}

.table-header,
.table-row {
  display: flex;
  align-items: center;
  min-height: 78rpx;
  box-sizing: border-box;
}

.table-header {
  color: #625d69;
  font-size: 21rpx;
  background: #faf9fc;
}

.table-row {
  min-height: 96rpx;
  border-top: 1rpx solid #f0edf4;
}

.table-row:active { background: #faf7ff; }

.index-cell {
  flex: 0 0 58rpx;
  padding-left: 18rpx;
  box-sizing: border-box;
  color: #6d6874;
  font-size: 22rpx;
}

.name-cell {
  flex: 1.3;
  min-width: 0;
  padding: 0 18rpx;
  overflow: hidden;
  color: #3d3944;
  font-size: 23rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
  
}

.amount-cell {
	
  flex: 1;
  min-width: 0;
  padding: 0 4rpx;
  overflow: hidden;
  color: #5a5660;
  font-size: 21rpx;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-header .amount-cell { color: #625d69; }
.sortable {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4rpx;
}

.negative { color: #f04e4e; }

.arrow-cell {
  flex: 0 0 40rpx;
  padding-right: 10rpx;
  box-sizing: border-box;
  text-align: right;
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

.bottom-space { height: 20rpx; }
</style>
