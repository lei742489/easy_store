<template>
  <view class="page">
    <view class="filter-panel">
      <view class="quick-search-row">
        <input
          v-model="filters.orderNo"
          class="quick-search-input"
          placeholder="请输入订单号"
          confirm-type="search"
          @confirm="handleSearch"
        />
        <button class="quick-search-button" @click="handleSearch">
          <uni-icons type="search" color="#fff" :size="20" />
          <text>查询</text>
        </button>
      </view>

      <view class="filter-actions">
        <button class="more-filter-button" @click="openMoreFilters">
          <text>展开更多</text>
          <uni-icons type="down" color="#722ed1" :size="16" />
          <text v-if="activeFilterCount" class="filter-count">{{ activeFilterCount }}</text>
        </button>
        <button class="reset-button" @click="handleReset">
          <uni-icons type="refresh" color="#666" :size="18" />
          <text>重置</text>
        </button>
      </view>
    </view>

    <uni-popup ref="filterPopup" type="bottom" :safe-area="true" :is-mask-click="true">
      <view class="filter-popup">
        <view class="popup-header">
          <text class="popup-title">更多筛选</text>
          <uni-icons type="closeempty" color="#999" :size="22" @click="closeMoreFilters" />
        </view>

        <view class="popup-form-item">
          <text class="popup-label">{{ partnerLabel }}</text>
          <customer-autocomplete
            v-if="mode === 'receive'"
            v-model="filters.partnerName"
            placeholder="名称 / 联系人 / 手机 / 拼音"
            @select="handlePartnerChange"
            @select-item="handlePartnerSelected"
          />
          <supplier-autocomplete
            v-else
            v-model="filters.partnerName"
            placeholder="名称 / 联系人 / 手机 / 拼音"
            @select="handlePartnerChange"
            @select-item="handlePartnerSelected"
          />
        </view>

        <view class="popup-form-item">
          <text class="popup-label">状态</text>
          <picker :range="statusOptions" range-key="label" :value="statusIndex" @change="onStatusChange">
            <view class="popup-picker">
              <text :class="{ placeholder: statusIndex === 0 }">{{ statusOptions[statusIndex].label }}</text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </view>

        <view v-if="isRoot" class="popup-form-item">
          <text class="popup-label">营业员</text>
          <picker :range="cashierOptions" range-key="label" :value="cashierIndex" @change="onCashierChange">
            <view class="popup-picker">
              <text :class="{ placeholder: cashierIndex === 0 }">{{ cashierOptions[cashierIndex].label }}</text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </view>

        <view class="popup-form-item">
          <text class="popup-label">日期</text>
          <view class="popup-date-picker">
            <uni-datetime-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :clear-icon="true"
            />
          </view>
        </view>

        <view class="popup-actions">
          <button class="popup-cancel-button" @click="closeMoreFilters">取消</button>
          <button class="popup-confirm-button" @click="applyMoreFilters">应用筛选</button>
        </view>
      </view>
    </uni-popup>

    <view class="list-summary">
      <text>{{ pageTitle }}</text>
      <text>共 {{ total }} 条</text>
    </view>

    <view v-if="records.length" class="voucher-list">
      <view
        v-for="voucher in records"
        :key="voucher.id"
        class="voucher-card"
        @click="openEditForm(voucher)"
      >
        <view class="card-header">
          <view class="voucher-main">
            <text class="order-no">{{ voucher.orderNo || '-' }}</text>
            <text class="voucher-type">{{ mode === 'receive' ? '收款' : '付款' }}</text>
          </view>
          <text class="status-tag" :class="Number(voucher.status) === 0 ? 'pending' : 'normal'">
            {{ statusText(voucher.status) }}
          </text>
        </view>

        <view class="partner-row">
          <view class="partner-name">
            <uni-icons :type="mode === 'receive' ? 'person' : 'shop'" color="#722ed1" :size="18" />
            <text>{{ getPartnerName(voucher) }}</text>
          </view>
          <text class="voucher-date">{{ formatDate(voucher.createTime) }}</text>
        </view>

        <view class="amount-panel">
          <text class="amount-label">{{ mode === 'receive' ? '收款金额' : '付款金额' }}</text>
          <text class="amount-value">{{ formatAmount(voucher.amount) }}</text>
        </view>

        <view v-if="voucher.note" class="note-row">
          <text class="note-label">备注</text>
          <text class="note-text">{{ voucher.note }}</text>
        </view>

        <view class="card-footer">
          <text>营业员：{{ voucher.cashierName || voucher.cashierId_dictText || '-' }}</text>
          <text v-if="voucher.updateBy">更新人：{{ voucher.updateBy }}</text>
        </view>
      </view>
    </view>

    <view v-else-if="!loading" class="empty-state">
      <uni-icons type="info" color="#aaa5b5" :size="52" />
      <text>暂无{{ mode === 'receive' ? '收款' : '付款' }}单</text>
    </view>

    <uni-load-more v-if="records.length || loading" :status="loadStatus" />
    <view class="bottom-space" />

    <image
      v-if="showGoTop"
      class="go-top-button"
      src="/static/ico/go-top.png"
      mode="aspectFit"
      @click="scrollToTop"
    />
  </view>
</template>

<script>
import CustomerAutocomplete from './customer-autocomplete.vue'
import SupplierAutocomplete from './supplier-autocomplete.vue'
import { getUser, isLoggedIn, clearSession, updateUser } from '../common/auth'
import {
  getUserInfo,
  listAppUsers,
  listPaymentVouchers,
  listReceivePaymentVouchers
} from '../common/api'

const createFilters = () => ({
  partnerId: '',
  partnerName: '',
  cashierId: '',
  orderNo: '',
  status: undefined
})

export default {
  components: {
    CustomerAutocomplete,
    SupplierAutocomplete
  },
  props: {
    mode: {
      type: String,
      default: 'receive'
    }
  },
  data() {
    return {
      user: getUser() || {},
      loading: false,
      initializing: false,
      initialized: false,
      redirecting: false,
      showGoTop: false,
      current: 1,
      pageSize: 15,
      total: 0,
      records: [],
      filters: createFilters(),
      dateRange: [],
      statusIndex: 0,
      cashierIndex: 0,
      statusOptions: [
        { label: '全部状态', value: undefined },
        { label: '待审核', value: 0 },
        { label: '正常', value: 1 }
      ],
      cashierOptions: [{ label: '全部营业员', value: '' }]
    }
  },
  mounted() {
    this.handlePageShow()
  },
  computed: {
    pageTitle() {
      return this.mode === 'receive' ? '收款单' : '付款单'
    },
    partnerLabel() {
      return this.mode === 'receive' ? '客户' : '供应商'
    },
    isRoot() {
      return Number(this.user.isRoot) === 1
    },
    hasMore() {
      return this.records.length < this.total
    },
    loadStatus() {
      if (this.loading) return 'loading'
      return this.hasMore ? 'more' : 'noMore'
    },
    activeFilterCount() {
      let count = 0
      if (this.filters.partnerId) count += 1
      if (this.filters.status !== undefined) count += 1
      if (this.isRoot && this.filters.cashierId) count += 1
      if (this.dateRange && this.dateRange.length === 2) count += 1
      return count
    }
  },
  methods: {
    async handlePageShow() {
      if (!isLoggedIn()) {
        this.redirectToLogin()
        return
      }
      if (!this.initialized) {
        await this.initialize()
        return
      }
      await this.queryVouchers(true)
    },
    handlePageScroll(scrollTop) {
      const visible = Number(scrollTop || 0) >= 500
      if (visible !== this.showGoTop) this.showGoTop = visible
    },
    async initialize() {
      if (this.loading || this.initializing) return
      this.initializing = true
      try {
        const user = await getUserInfo()
        this.user = { ...this.user, ...user }
        updateUser(this.user)
        if (this.isRoot) await this.loadCashiers()
        this.initialized = true
        await this.queryVouchers(true)
      } catch (error) {
        this.redirectToLogin()
      } finally {
        this.initializing = false
      }
    },
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
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
    buildQuery(current) {
      const params = {
        current,
        pageSize: this.pageSize,
        column: 'createTime',
        order: 'desc',
        userId: this.user.id
      }
      const partnerId = String(this.filters.partnerId || '').trim()
      const orderNo = String(this.filters.orderNo || '').trim()
      if (partnerId) {
        params[this.mode === 'receive' ? 'customerId' : 'supplierId'] = partnerId
      }
      if (orderNo) params.orderNo = orderNo
      if (this.filters.status !== undefined) params.status = this.filters.status
      if (this.isRoot && this.filters.cashierId) params.cashierId = this.filters.cashierId
      if (this.dateRange && this.dateRange.length === 2) {
        params.createTime_begin = this.dateRange[0]
        params.createTime_end = this.dateRange[1]
      }
      return params
    },
    getListApi() {
      return this.mode === 'receive' ? listReceivePaymentVouchers : listPaymentVouchers
    },
    async queryVouchers(reset = false, pageNumber, showPageLoading = false) {
      if (this.loading || this.redirecting) return
      const targetPage = reset ? 1 : (pageNumber || this.current)
      this.loading = true
      if (showPageLoading) uni.showLoading({ title: '查询中...', mask: true })
      try {
        const page = await this.getListApi()(this.buildQuery(targetPage))
        const list = page && Array.isArray(page.records) ? page.records : []
        this.records = reset ? list : this.records.concat(list)
        this.current = Number(page && page.current ? page.current : targetPage)
        this.total = Number(page && page.total ? page.total : 0)
      } catch (error) {
        const message = error && error.message ? error.message : `${this.pageTitle}加载失败`
        if (/登录|token|过期/i.test(message)) {
          this.redirectToLogin()
        } else {
          uni.showToast({ title: message, icon: 'none' })
        }
      } finally {
        this.loading = false
        if (showPageLoading) uni.hideLoading()
      }
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.queryVouchers(false, this.current + 1)
    },
    handlePullDownRefresh() {
      return this.queryVouchers(true)
    },
    handleSearch() {
      this.closeMoreFilters()
      this.queryVouchers(true, undefined, true)
    },
    handleReset() {
      this.filters = createFilters()
      this.dateRange = []
      this.statusIndex = 0
      this.cashierIndex = 0
      this.closeMoreFilters()
      this.queryVouchers(true, undefined, true)
    },
    handlePartnerChange(value) {
      if (!value) this.filters.partnerId = ''
    },
    handlePartnerSelected(payload) {
      if (!payload) {
        this.filters.partnerId = ''
        return
      }
      this.filters.partnerId = payload.id === undefined || payload.id === null
        ? ''
        : String(payload.id)
      this.filters.partnerName = String(payload.name || '').trim()
    },
    openEditForm(voucher) {
      if (!voucher || voucher.id === undefined || voucher.id === null) return
      const storageKey = `easy-store-${this.mode}-edit-${Date.now()}`
      uni.setStorageSync(storageKey, voucher)
      uni.setStorageSync(
        this.mode === 'receive'
          ? 'easy-store-receive-edit-key'
          : 'easy-store-payment-edit-key',
        storageKey
      )
      uni.setStorageSync('easy-store-business-tab', this.mode)
      uni.switchTab({ url: '/pages/business/index' })
    },
    openMoreFilters() {
      this.$refs.filterPopup && this.$refs.filterPopup.open()
    },
    closeMoreFilters() {
      this.$refs.filterPopup && this.$refs.filterPopup.close()
    },
    applyMoreFilters() {
      this.closeMoreFilters()
      this.queryVouchers(true, undefined, true)
    },
    scrollToTop() {
      uni.pageScrollTo({ scrollTop: 0, duration: 300 })
    },
    onStatusChange(event) {
      this.statusIndex = Number(event.detail.value || 0)
      this.filters.status = this.statusOptions[this.statusIndex].value
    },
    onCashierChange(event) {
      this.cashierIndex = Number(event.detail.value || 0)
      this.filters.cashierId = this.cashierOptions[this.cashierIndex].value
    },
    getPartnerName(voucher) {
      const value = this.mode === 'receive'
        ? (voucher.customerId_dictText || voucher.customerId)
        : (voucher.supplierId_dictText || voucher.supplierId)
      return value || `未设置${this.partnerLabel}`
    },
    statusText(status) {
      return Number(status) === 0 ? '待审核' : '正常'
    },
    formatDate(value) {
      return value ? String(value).split(' ')[0] : '-'
    },
    formatAmount(value) {
      const amount = Number(value || 0)
      return `¥${Number.isFinite(amount) ? amount.toFixed(2) : '0.00'}`
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 22rpx;
  box-sizing: border-box;
  color: #33303f;
  background: #f5f4fb;
}

.filter-panel,
.voucher-card {
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
}

.filter-panel { padding: 20rpx 22rpx 16rpx; }
.quick-search-row { display: flex; align-items: center; gap: 14rpx; }
.quick-search-input,
.popup-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 72rpx;
  padding: 0 20rpx;
  box-sizing: border-box;
  color: #393044;
  font-size: 25rpx;
  background: #fff;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}
.quick-search-input { flex: 1; min-width: 0; height: 58rpx; }
.quick-search-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  flex: 0 0 124rpx;
  height: 58rpx;
  margin: 0;
  padding: 0;
  color: #fff;
  font-size: 25rpx;
  line-height: 58rpx;
  background: #722ed1;
  border-radius: 10rpx;
}
.quick-search-button::after,
.reset-button::after,
.more-filter-button::after,
.popup-actions button::after { border: 0; }
.filter-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-top: 16rpx;
}
.reset-button,
.more-filter-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 58rpx;
  margin: 0;
  padding: 0 18rpx;
  font-size: 23rpx;
  line-height: 58rpx;
  border-radius: 8rpx;
}
.reset-button { color: #666; background: #f2f3f5; }
.more-filter-button { position: relative; color: #722ed1; background: #f4efff; }
.filter-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 5rpx;
  color: #fff;
  font-size: 17rpx;
  line-height: 28rpx;
  background: #722ed1;
  border-radius: 18rpx;
}
.filter-popup {
  padding: 28rpx 28rpx calc(22rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
}
.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 26rpx;
}
.popup-title { color: #30283d; font-size: 32rpx; font-weight: 600; }
.popup-form-item { margin-bottom: 22rpx; }
.popup-label {
  display: block;
  margin-bottom: 10rpx;
  color: #6b6676;
  font-size: 22rpx;
}
.popup-picker,
.popup-date-picker { width: 100%; }
.placeholder { color: #aaa5b5; }
.popup-actions { display: flex; gap: 18rpx; margin-top: 30rpx; }
.popup-actions button {
  flex: 1;
  height: 78rpx;
  margin: 0;
  font-size: 26rpx;
  line-height: 78rpx;
  border-radius: 10rpx;
}
.popup-cancel-button { color: #666; background: #f2f3f5; }
.popup-confirm-button { color: #fff; background: #722ed1; }
.list-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 6rpx 16rpx;
  color: #777482;
  font-size: 23rpx;
}
.list-summary text:first-child { color: #454252; font-size: 29rpx; font-weight: 600; }
.voucher-card { margin-bottom: 18rpx; padding: 22rpx; }
.card-header,
.partner-row,
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.voucher-main,
.partner-name { display: flex; align-items: center; min-width: 0; }
.order-no {
  max-width: 420rpx;
  overflow: hidden;
  color: #30283d;
  font-size: 27rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.voucher-type {
  margin-left: 12rpx;
  padding: 3rpx 10rpx;
  color: #1677ff;
  font-size: 19rpx;
  background: #e8f3ff;
  border-radius: 8rpx;
}
.status-tag { padding: 6rpx 14rpx; font-size: 20rpx; border-radius: 20rpx; }
.status-tag.pending { color: #e5484d; background: #fff0f0; }
.status-tag.normal { color: #00a870; background: #e8f8f2; }
.partner-row { margin-top: 18rpx; color: #6f6979; font-size: 23rpx; }
.partner-name { flex: 1; gap: 8rpx; overflow: hidden; }
.partner-name text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.voucher-date { flex: 0 0 auto; margin-left: 18rpx; color: #9a95a4; }
.amount-panel {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 20rpx;
  padding: 20rpx 22rpx;
  background: #faf9fc;
  border-radius: 12rpx;
}
.amount-label { color: #918b99; font-size: 22rpx; }
.amount-value { color: #722ed1; font-size: 34rpx; font-weight: 600; }
.note-row { display: flex; margin-top: 16rpx; font-size: 22rpx; }
.note-label { flex: 0 0 62rpx; color: #918b99; }
.note-text { flex: 1; color: #686171; word-break: break-all; }
.card-footer {
  gap: 12rpx;
  margin-top: 18rpx;
  padding-top: 15rpx;
  color: #9a95a4;
  font-size: 20rpx;
  border-top: 1rpx solid #eeeaf4;
}
.card-footer text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 500rpx;
  gap: 18rpx;
  color: #aaa5b5;
  font-size: 24rpx;
}
.bottom-space { height: 30rpx; }
.go-top-button {
  position: fixed;
  right: 28rpx;
  bottom: calc(42rpx + env(safe-area-inset-bottom));
  z-index: 20;
  width: 88rpx;
  height: 88rpx;
  padding: 10rpx;
  box-sizing: border-box;
  background: rgba(255, 255, 255, .94);
  border: 1rpx solid #e5e0ec;
  border-radius: 50%;
  opacity: .9;
  box-shadow: 0 8rpx 22rpx rgba(67, 47, 119, .2);
}

:deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }
:deep(.uni-date-editor--x) { height: 72rpx; }
</style>
