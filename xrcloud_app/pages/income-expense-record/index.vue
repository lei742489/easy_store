<template>
  <view class="page">
    <view id="income-expense-top" class="top-area">
      <view class="filter-panel">
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

        <view class="filter-actions">
          <button class="more-filter-button" :disabled="loading" @click="openMoreFilters">
            <text>展开更多</text>
            <uni-icons type="down" color="#722ed1" :size="16" />
            <text v-if="activeFilterCount" class="filter-count">{{ activeFilterCount }}</text>
          </button>
          <view class="action-group">
            <button class="reset-button" :disabled="loading" @click="handleReset">
              <uni-icons type="refresh" color="#666" :size="18" />
              <text>重置</text>
            </button>
            <button class="search-button" :disabled="loading" @click="handleSearch">
              <uni-icons type="search" color="#fff" :size="18" />
              <text>查询</text>
            </button>
          </view>
        </view>
      </view>

      <uni-popup ref="filterPopup" type="bottom" :safe-area="true" :is-mask-click="true">
        <view class="filter-popup">
          <view class="popup-header">
            <text class="popup-title">更多筛选</text>
            <uni-icons type="closeempty" color="#999" :size="22" @click="closeMoreFilters" />
          </view>

          <view class="popup-form-item">
            <text class="popup-label">客户</text>
            <customer-autocomplete
              v-model="filters.customerName"
              placeholder="全部客户"
              @select="handleCustomerChange"
              @select-item="handleCustomerSelected"
            />
          </view>

          <view class="popup-form-item">
            <text class="popup-label">供应商</text>
            <supplier-autocomplete
              v-model="filters.supplierName"
              placeholder="全部供应商"
              @select="handleSupplierChange"
              @select-item="handleSupplierSelected"
            />
          </view>

          <view class="popup-form-item">
            <text class="popup-label">收支项目</text>
            <picker
              class="picker-wrap"
              :range="itemOptions"
              range-key="label"
              :value="itemIndex"
              @change="handleItemChange"
            >
              <view class="popup-picker">
                <text :class="{ placeholder: itemIndex === 0 }">{{ itemOptions[itemIndex].label }}</text>
                <uni-icons type="down" color="#999" :size="16" />
              </view>
            </picker>
          </view>

          <view class="popup-form-item">
            <text class="popup-label">收支类型</text>
            <picker
              class="picker-wrap"
              :range="typeOptions"
              range-key="label"
              :value="typeIndex"
              @change="handleTypeChange"
            >
              <view class="popup-picker">
                <text :class="{ placeholder: typeIndex === 0 }">{{ typeOptions[typeIndex].label }}</text>
                <uni-icons type="down" color="#999" :size="16" />
              </view>
            </picker>
          </view>

          <view class="popup-actions">
            <button class="popup-cancel-button" :disabled="loading" @click="closeMoreFilters">取消</button>
            <button class="popup-confirm-button" :disabled="loading" @click="applyMoreFilters">应用筛选</button>
          </view>
        </view>
      </uni-popup>

      <view class="toolbar">
        <view class="toolbar-title">
          <text class="page-title">收支记录</text>
          <text class="record-count">共 {{ total }} 条</text>
        </view>
        <button v-if="isRoot" class="add-button" @click="showManual">
          <uni-icons type="plusempty" color="#fff" :size="17" />
          <text>记账</text>
        </button>
      </view>

      <view class="summary-card">
        <view class="summary-item">
          <text class="summary-label">期初结余</text>
          <text class="summary-value">{{ formatAmount(result.openingBalance) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">本期收入</text>
          <text class="summary-value income">{{ formatAmount(result.incomeTotal) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">本期支出</text>
          <text class="summary-value expense">{{ formatAmount(result.expenseTotal) }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">期末结余</text>
          <text class="summary-value" :class="{ negative: Number(result.endingBalance || 0) < 0 }">
            {{ formatAmount(result.endingBalance) }}
          </text>
        </view>
      </view>
    </view>

    <scroll-view
      class="record-scroll"
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
          v-for="(record, index) in records"
          :key="String(record.orderNo || record.rowNo || index)"
          class="record-card"
          :class="{ 'opening-card': isOpeningRecord(record) }"
        >
          <view class="record-header">
            <view class="record-title-wrap">
              <text class="record-title">{{ isOpeningRecord(record) ? '期初结余' : (record.summary || '收支记录') }}</text>
              <text class="record-date">{{ record.businessDate || '-' }}</text>
            </view>
            <text v-if="isOpeningRecord(record)" class="opening-badge">期初</text>
            <text v-else class="record-no">{{ record.orderNo || '-' }}</text>
          </view>

          <view v-if="!isOpeningRecord(record)" class="record-meta">
            <text>往来单位：{{ record.counterparty || '-' }}</text>
            <text>收支项目：{{ record.fundItem || '-' }}</text>
          </view>

          <view class="amount-grid">
            <view class="amount-item">
              <text class="amount-label">收入</text>
              <text class="amount-value income">{{ formatAmount(record.income) }}</text>
            </view>
            <view class="amount-item">
              <text class="amount-label">支出</text>
              <text class="amount-value expense">{{ formatAmount(record.expense) }}</text>
            </view>
            <view class="amount-item">
              <text class="amount-label">结余</text>
              <text class="amount-value" :class="{ negative: Number(record.balance || 0) < 0 }">
                {{ formatAmount(record.balance) }}
              </text>
            </view>
          </view>
        </view>
      </view>

      <view v-else-if="loading" class="state">
        <uni-icons type="spinner-cycle" color="#722ed1" :size="30" />
        <text>正在加载...</text>
      </view>
      <view v-else class="state">
        <uni-icons type="info" color="#b6b0c2" :size="44" />
        <text>暂无收支记录</text>
      </view>

      <uni-load-more v-if="records.length || loading" :status="loadStatus" />
      <view class="bottom-space" />
    </scroll-view>

    <uni-popup ref="manualPopup" type="bottom" :safe-area="true" :is-mask-click="false">
      <view class="manual-popup">
        <view class="popup-header">
          <text class="popup-title">记账</text>
          <uni-icons type="closeempty" color="#999" :size="22" @click="closeManual" />
        </view>

        <view class="form-row">
          <view class="form-item half">
            <text class="form-label">记账类型</text>
            <picker :range="flowOptions" range-key="label" :value="manualFlowIndex" @change="handleFlowChange">
              <view class="form-picker">
                <text>{{ flowOptions[manualFlowIndex].label }}</text>
                <uni-icons type="down" color="#999" :size="16" />
              </view>
            </picker>
          </view>
          <view class="form-item half">
            <text class="form-label">业务日期</text>
            <picker mode="date" :value="manualForm.businessDate" @change="handleManualDateChange">
              <view class="form-picker">
                <text>{{ manualForm.businessDate }}</text>
                <uni-icons type="calendar" color="#999" :size="16" />
              </view>
            </picker>
          </view>
        </view>

        <view class="form-item">
          <text class="form-label">{{ manualFlowIndex === 0 ? '收入项目' : '支出项目' }}</text>
          <picker :range="manualItemOptions" range-key="label" :value="manualItemIndex" @change="handleManualItemChange">
            <view class="form-picker">
              <text :class="{ placeholder: manualItemIndex === 0 }">{{ manualItemOptions[manualItemIndex].label }}</text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </view>

        <view class="form-item">
          <text class="form-label">{{ manualFlowIndex === 0 ? '收入金额' : '支出金额' }}</text>
          <input
            v-model="manualForm.amount"
            class="form-input"
            type="digit"
            placeholder="请输入金额"
          />
        </view>

        <view class="form-item">
          <text class="form-label">往来单位</text>
          <input v-model="manualForm.counterparty" class="form-input" maxlength="100" placeholder="请输入客户或供应商名称" />
        </view>

        <view class="form-item">
          <text class="form-label">结算账户</text>
          <picker :range="accountOptions" range-key="label" :value="accountIndex" @change="handleAccountChange">
            <view class="form-picker">
              <text :class="{ placeholder: !manualForm.settleId }">{{ selectedAccountName || '请选择结算账户' }}</text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </view>

        <view class="form-item">
          <text class="form-label">营业员</text>
          <picker :range="cashierOptions" range-key="label" :value="cashierIndex" @change="handleCashierChange">
            <view class="form-picker">
              <text :class="{ placeholder: !manualForm.cashierId }">{{ selectedCashierName || '请选择营业员' }}</text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </view>

        <view class="form-item">
          <text class="form-label">说明</text>
          <textarea v-model="manualForm.summary" class="form-textarea" maxlength="200" placeholder="请输入业务说明" />
        </view>

        <view class="popup-actions">
          <button class="cancel-button" :disabled="saving" @click="closeManual">取消</button>
          <button class="confirm-button" :disabled="saving" @click="submitManual">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </view>
      </view>
    </uni-popup>
  </view>
</template>

<script>
import CustomerAutocomplete from '../../components/customer-autocomplete.vue'
import SupplierAutocomplete from '../../components/supplier-autocomplete.vue'
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import {
  addIncomeExpenseRecord,
  getUserInfo,
  listAccountSettles,
  listAppUsers,
  listIncomeExpenseItems,
  listIncomeExpenseRecords
} from '../../common/api'

const pad = (value) => String(value).padStart(2, '0')

const monthRange = () => {
  const date = new Date()
  const end = new Date(date.getFullYear(), date.getMonth() + 1, 0)
  return [
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-01`,
    `${end.getFullYear()}-${pad(end.getMonth() + 1)}-${pad(end.getDate())}`
  ]
}

const today = () => {
  const date = new Date()
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const numberValue = (value) => {
  const number = Number(value || 0)
  return Number.isFinite(number) ? number : 0
}

export default {
  components: {
    CustomerAutocomplete,
    SupplierAutocomplete
  },
  data() {
    const range = monthRange()
    return {
      user: getUser() || {},
      filters: {
        customerId: '',
        customerName: '',
        supplierId: '',
        supplierName: '',
        fundItem: '',
        incomeExpenseType: ''
      },
      typeOptions: [
        { label: '全部收支类型', value: '' },
        { label: '收入', value: 'income' },
        { label: '支出', value: 'expense' }
      ],
      itemOptions: [{ label: '全部收支项目', value: '' }],
      typeIndex: 0,
      itemIndex: 0,
      startDate: range[0],
      endDate: range[1],
      dateRange: range,
      result: {},
      records: [],
      current: 1,
      pageSize: 20,
      total: 0,
      loadedCount: 0,
      loading: false,
      refreshing: false,
      saving: false,
      initialized: false,
      initializing: false,
      redirecting: false,
      scrollHeight: 1,
      measureTimer: null,
      accountOptions: [],
      accountIndex: 0,
      cashierOptions: [],
      cashierIndex: 0,
      flowOptions: [
        { label: '记收入', value: 'income' },
        { label: '记支出', value: 'expense' }
      ],
      manualFlowIndex: 0,
      manualItemOptions: [{ label: '请选择收支项目', value: '' }],
      manualItemIndex: 0,
      manualForm: {
        businessDate: today(),
        fundItem: '',
        amount: '',
        counterparty: '',
        settleId: '',
        cashierId: '',
        summary: ''
      }
    }
  },
  computed: {
    isRoot() {
      return Number(this.user.isRoot) === 1
    },
    hasMore() {
      return this.loadedCount < this.total
    },
    loadStatus() {
      if (this.loading) return 'loading'
      return this.hasMore ? 'more' : 'noMore'
    },
    activeFilterCount() {
      let count = 0
      if (this.filters.customerId) count += 1
      if (this.filters.supplierId) count += 1
      if (this.filters.fundItem) count += 1
      if (this.filters.incomeExpenseType) count += 1
      return count
    },
    selectedAccountName() {
      const option = this.accountOptions[this.accountIndex]
      return option && option.value ? option.label : ''
    },
    selectedCashierName() {
      const option = this.cashierOptions[this.cashierIndex]
      return option && option.value ? option.label : ''
    }
  },
  onLoad() {
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
    if (this.initialized) this.loadRecords(true)
  },
  onHide() {
    this.clearMeasureTimer()
  },
  onUnload() {
    this.clearMeasureTimer()
  },
  onPullDownRefresh() {
    this.loadRecords(true).finally(() => uni.stopPullDownRefresh())
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
          .select('#income-expense-top')
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
          Math.floor(Number(systemInfo.windowHeight || 0) * 0.6)
        )
      }
    },
    async initialize() {
      if (this.initializing || this.redirecting) return
      this.initializing = true
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...(currentUser || {}) }
        updateUser(this.user)
        if (!this.isRoot) {
          uni.showToast({ title: '仅老板账号可以访问', icon: 'none' })
          setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 500)
          return
        }
        await Promise.all([this.loadFilterItems(), this.loadManualOptions()])
        this.initialized = true
        await this.loadRecords(true)
      } catch (error) {
        this.handleError(error, '收支记录加载失败')
      } finally {
        this.initializing = false
        this.scheduleMeasure()
      }
    },
    async loadFilterItems() {
      const list = await listIncomeExpenseItems({ userId: this.user.id })
      const options = (Array.isArray(list) ? list : [])
        .filter((item) => item && item.name)
        .map((item) => ({ label: String(item.name), value: String(item.name) }))
      this.itemOptions = [{ label: '全部收支项目', value: '' }, ...options]
      this.itemIndex = 0
    },
    async loadManualOptions() {
      const [accounts, users] = await Promise.all([
        listAccountSettles(this.user.id),
        listAppUsers()
      ])
      this.accountOptions = (Array.isArray(accounts) ? accounts : [])
        .filter((item) => item && item.id !== undefined && item.id !== null)
        .map((item) => ({
          label: String(item.name || item.title || `账户${item.id}`),
          value: String(item.id)
        }))
      this.cashierOptions = (Array.isArray(users) ? users : [])
        .filter((item) => item && Number(item.status) !== 0)
        .map((item) => ({
          label: String(item.realName || item.userName || `员工${item.id}`),
          value: String(item.id)
        }))
      this.accountIndex = 0
      this.cashierIndex = Math.max(
        0,
        this.cashierOptions.findIndex((item) => String(item.value) === String(this.user.id))
      )
      this.manualForm.settleId = this.accountOptions[0] ? this.accountOptions[0].value : ''
      this.manualForm.cashierId = this.cashierOptions[this.cashierIndex]
        ? this.cashierOptions[this.cashierIndex].value
        : ''
      await this.loadManualItems()
    },
    async loadManualItems() {
      const type = this.flowOptions[this.manualFlowIndex].value
      const list = await listIncomeExpenseItems({
        userId: this.user.id,
        itemType: type,
        enabledOnly: true
      })
      const options = (Array.isArray(list) ? list : [])
        .filter((item) => item && item.name)
        .map((item) => ({ label: String(item.name), value: String(item.name) }))
      this.manualItemOptions = [{ label: '请选择收支项目', value: '' }, ...options]
      this.manualItemIndex = 0
      this.manualForm.fundItem = ''
    },
    buildQuery(page) {
      const query = {
        userId: this.user.id,
        customerId: this.filters.customerId || undefined,
        supplierId: this.filters.supplierId || undefined,
        fundItem: this.filters.fundItem || undefined,
        incomeExpenseType: this.filters.incomeExpenseType || undefined,
        startDate: this.startDate || undefined,
        endDate: this.endDate || undefined,
        businessDateOrder: 'asc',
        current: page,
        pageSize: this.pageSize
      }
      return query
    },
    async loadRecords(reset = false, pageNumber) {
      if (this.loading || this.redirecting || !this.initialized) return false
      const targetPage = reset ? 1 : (pageNumber || this.current)
      this.loading = true
      try {
        const data = await listIncomeExpenseRecords(this.buildQuery(targetPage))
        const source = data || {}
        const rows = Array.isArray(source.records) ? source.records : []
        const eventRows = rows.filter((row) => !this.isOpeningRecord(row))
        this.records = reset ? rows : this.records.concat(rows)
        this.loadedCount = reset ? eventRows.length : this.loadedCount + eventRows.length
        this.current = Number(source.current || targetPage)
        this.pageSize = Number(source.pageSize || this.pageSize)
        this.total = Number(source.total || 0)
        this.result = source
        return true
      } catch (error) {
        this.handleError(error, '收支记录加载失败')
        return false
      } finally {
        this.loading = false
        this.scheduleMeasure()
      }
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.loadRecords(false, this.current + 1)
    },
    handleSearch() {
      this.closeMoreFilters()
      this.loadRecords(true)
    },
    handleReset() {
      const range = monthRange()
      this.filters = {
        customerId: '',
        customerName: '',
        supplierId: '',
        supplierName: '',
        fundItem: '',
        incomeExpenseType: ''
      }
      this.typeIndex = 0
      this.itemIndex = 0
      this.startDate = range[0]
      this.endDate = range[1]
      this.dateRange = range
      this.closeMoreFilters()
      this.loadRecords(true)
    },
    openMoreFilters() {
      this.$refs.filterPopup && this.$refs.filterPopup.open()
    },
    closeMoreFilters() {
      this.$refs.filterPopup && this.$refs.filterPopup.close()
    },
    applyMoreFilters() {
      this.closeMoreFilters()
      this.loadRecords(true)
    },
    handleRefresh() {
      this.refreshing = true
      this.loadRecords(true).finally(() => {
        this.refreshing = false
      })
    },
    handleCustomerChange(value) {
      if (!value) {
        this.filters.customerId = ''
        this.filters.customerName = ''
      }
    },
    handleCustomerSelected(payload) {
      this.filters.customerId = payload && payload.id !== undefined
        ? String(payload.id)
        : ''
      this.filters.customerName = payload && payload.name ? String(payload.name) : ''
    },
    handleSupplierChange(value) {
      if (!value) {
        this.filters.supplierId = ''
        this.filters.supplierName = ''
      }
    },
    handleSupplierSelected(payload) {
      this.filters.supplierId = payload && payload.id !== undefined
        ? String(payload.id)
        : ''
      this.filters.supplierName = payload && payload.name ? String(payload.name) : ''
    },
    handleItemChange(event) {
      this.itemIndex = Number(event && event.detail ? event.detail.value || 0 : 0)
      this.filters.fundItem = this.itemOptions[this.itemIndex]
        ? this.itemOptions[this.itemIndex].value
        : ''
    },
    handleTypeChange(event) {
      this.typeIndex = Number(event && event.detail ? event.detail.value || 0 : 0)
      this.filters.incomeExpenseType = this.typeOptions[this.typeIndex]
        ? this.typeOptions[this.typeIndex].value
        : ''
    },
    handleDateChange(value) {
      const range = Array.isArray(value) ? value : []
      this.dateRange = range
      this.startDate = range[0] || ''
      this.endDate = range[1] || ''
    },
    isOpeningRecord(record) {
      return Boolean(record && record.orderNo === '' && record.summary === '期初结存')
    },
    showManual() {
      if (!this.isRoot) return
      this.resetManualForm()
      this.loadManualItems()
        .then(() => this.$refs.manualPopup && this.$refs.manualPopup.open())
        .catch((error) => this.handleError(error, '收支项目加载失败'))
    },
    closeManual() {
      this.$refs.manualPopup && this.$refs.manualPopup.close()
    },
    resetManualForm() {
      this.manualFlowIndex = 0
      this.manualItemIndex = 0
      this.manualForm.businessDate = today()
      this.manualForm.fundItem = ''
      this.manualForm.amount = ''
      this.manualForm.counterparty = ''
      this.manualForm.settleId = this.accountOptions[0] ? this.accountOptions[0].value : ''
      this.manualForm.cashierId = this.cashierOptions[this.cashierIndex]
        ? this.cashierOptions[this.cashierIndex].value
        : String(this.user.id || '')
      this.accountIndex = Math.max(0, this.accountOptions.findIndex(
        (item) => String(item.value) === String(this.manualForm.settleId)
      ))
      this.manualForm.summary = ''
    },
    handleFlowChange(event) {
      this.manualFlowIndex = Number(event && event.detail ? event.detail.value || 0 : 0)
      this.loadManualItems().catch((error) => this.handleError(error, '收支项目加载失败'))
    },
    handleManualDateChange(event) {
      this.manualForm.businessDate = event && event.detail ? event.detail.value : today()
    },
    handleManualItemChange(event) {
      this.manualItemIndex = Number(event && event.detail ? event.detail.value || 0 : 0)
      this.manualForm.fundItem = this.manualItemOptions[this.manualItemIndex]
        ? this.manualItemOptions[this.manualItemIndex].value
        : ''
    },
    handleAccountChange(event) {
      this.accountIndex = Number(event && event.detail ? event.detail.value || 0 : 0)
      this.manualForm.settleId = this.accountOptions[this.accountIndex]
        ? this.accountOptions[this.accountIndex].value
        : ''
    },
    handleCashierChange(event) {
      this.cashierIndex = Number(event && event.detail ? event.detail.value || 0 : 0)
      this.manualForm.cashierId = this.cashierOptions[this.cashierIndex]
        ? this.cashierOptions[this.cashierIndex].value
        : ''
    },
    async submitManual() {
      if (this.saving) return
      const amount = numberValue(this.manualForm.amount)
      if (!this.manualForm.fundItem) {
        uni.showToast({ title: '请选择收支项目', icon: 'none' })
        return
      }
      if (amount <= 0) {
        uni.showToast({ title: '请输入大于0的金额', icon: 'none' })
        return
      }
      if (!this.manualForm.settleId) {
        uni.showToast({ title: '请选择结算账户', icon: 'none' })
        return
      }
      if (!this.manualForm.cashierId) {
        uni.showToast({ title: '请选择营业员', icon: 'none' })
        return
      }
      this.saving = true
      try {
        await addIncomeExpenseRecord({
          userId: this.user.id,
          settleId: this.manualForm.settleId,
          cashierId: this.manualForm.cashierId,
          businessDate: this.manualForm.businessDate,
          summary: String(this.manualForm.summary || '').trim(),
          counterparty: String(this.manualForm.counterparty || '').trim(),
          fundItem: this.manualForm.fundItem,
          income: this.manualFlowIndex === 0 ? amount : 0,
          expense: this.manualFlowIndex === 1 ? amount : 0
        })
        this.closeManual()
        uni.showToast({ title: '保存成功', icon: 'success' })
        await this.loadRecords(true)
      } catch (error) {
        this.handleError(error, '保存失败')
      } finally {
        this.saving = false
      }
    },
    formatAmount(value) {
      const amount = Number(value || 0)
      return Number.isFinite(amount) ? `￥${amount.toFixed(2)}` : '￥0.00'
    },
    handleError(error, fallback) {
      const message = error && error.message ? error.message : fallback
      if (/登录|token|过期/i.test(message) || !isLoggedIn()) {
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

.top-area { flex: 0 0 auto; }

.filter-panel,
.summary-card,
.record-card,
.manual-popup {
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
}

.filter-panel { padding: 18rpx; }

.date-row,
.filter-actions,
.action-group,
.toolbar,
.toolbar-title,
.record-header,
.record-meta,
.popup-header,
.form-row,
.form-picker,
.popup-picker,
.popup-actions {
  display: flex;
  align-items: center;
}

.picker-wrap { flex: 1; min-width: 0; }

.picker-field,
.form-picker,
.popup-picker,
.form-input {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 62rpx;
  padding: 0 16rpx;
  box-sizing: border-box;
  color: #393044;
  font-size: 23rpx;
  background: #fff;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}

.picker-field text,
.form-picker text,
.popup-picker text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.placeholder { color: #aaa5b5; }
.date-row { gap: 12rpx; }
.date-label { flex: 0 0 auto; color: #6f6979; font-size: 22rpx; }
.date-row :deep(.uni-date) { flex: 1; min-width: 0; }
.date-row :deep(.uni-date-editor--x) { height: 62rpx; }
.date-row :deep(.uni-date-x--border) { border-color: #ded9e8; border-radius: 10rpx; }

.filter-actions {
  justify-content: space-between;
  gap: 14rpx;
  margin-top: 14rpx;
}

.action-group {
  flex: 0 0 auto;
  gap: 12rpx;
}

.search-button,
.reset-button,
.more-filter-button,
.add-button,
.cancel-button,
.confirm-button,
.popup-cancel-button,
.popup-confirm-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  height: 58rpx;
  margin: 0;
  padding: 0 18rpx;
  font-size: 23rpx;
  line-height: 58rpx;
  border: 0;
  border-radius: 10rpx;
}

.search-button,
.add-button,
.confirm-button,
.popup-confirm-button { color: #fff; background: #722ed1; }
.reset-button,
.cancel-button,
.popup-cancel-button { color: #666; background: #f2f3f5; }
.search-button { flex: 0 0 124rpx; }
.reset-button { flex: 0 0 124rpx; }
.more-filter-button {
  position: relative;
  flex: 1;
  min-width: 0;
  color: #722ed1;
  background: #f4efff;
}

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

.search-button::after,
.reset-button::after,
.more-filter-button::after,
.add-button::after,
.cancel-button::after,
.confirm-button::after,
.popup-cancel-button::after,
.popup-confirm-button::after { border: 0; }

.filter-popup {
  padding: 28rpx 28rpx calc(22rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
}

.filter-popup .popup-header { margin-bottom: 26rpx; }
.popup-form-item { margin-bottom: 22rpx; }
.popup-label {
  display: block;
  margin-bottom: 10rpx;
  color: #6b6676;
  font-size: 22rpx;
}
.filter-popup :deep(.input-wrap),
.popup-picker { height: 72rpx; }
.popup-actions button { flex: 1; }

.toolbar {
  justify-content: space-between;
  gap: 12rpx;
  margin: 16rpx 4rpx 12rpx;
}

.toolbar-title { gap: 14rpx; min-width: 0; }
.page-title { color: #454252; font-size: 29rpx; font-weight: 600; }
.record-count { color: #9993a2; font-size: 21rpx; }
.add-button { flex: 0 0 124rpx; }

.summary-card {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4rpx;
  padding: 20rpx 6rpx;
}

.summary-item,
.amount-item { min-width: 0; text-align: center; }
.summary-label,
.amount-label { display: block; color: #817a89; font-size: 19rpx; }
.summary-value {
  display: block;
  margin-top: 8rpx;
  color: #722ed1;
  font-size: 23rpx;
  font-weight: 600;
  white-space: nowrap;
}

.income { color: #00a870 !important; }
.expense { color: #e5484d !important; }
.negative { color: #e5484d !important; }

.record-scroll {
  flex: 0 0 auto;
  min-height: 240px;
  margin-top: 14rpx;
}

.record-list { display: flex; flex-direction: column; gap: 14rpx; }
.record-card { padding: 20rpx; }
.opening-card { background: #fffdf0; border-color: #f3e5a8; }
.record-header { justify-content: space-between; gap: 12rpx; }
.record-title-wrap { flex: 1; min-width: 0; }
.record-title {
  display: block;
  overflow: hidden;
  color: #30283d;
  font-size: 27rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.record-date { display: block; margin-top: 7rpx; color: #9a95a4; font-size: 20rpx; }
.record-no {
  flex: 0 0 auto;
  max-width: 260rpx;
  overflow: hidden;
  color: #722ed1;
  font-size: 20rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.opening-badge {
  flex: 0 0 auto;
  padding: 5rpx 12rpx;
  color: #ad7a00;
  font-size: 19rpx;
  background: #fff4c2;
  border-radius: 16rpx;
}
.record-meta {
  flex-wrap: wrap;
  gap: 8rpx 18rpx;
  margin-top: 16rpx;
  color: #77717e;
  font-size: 21rpx;
}
.amount-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6rpx;
  margin-top: 18rpx;
  padding: 16rpx 4rpx;
  background: rgba(250, 249, 252, .8);
  border-radius: 10rpx;
}
.amount-value {
  display: block;
  margin-top: 7rpx;
  color: #4d4858;
  font-size: 23rpx;
  font-weight: 600;
  white-space: nowrap;
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

.bottom-space { height: 24rpx; }

.manual-popup {
  max-height: 90vh;
  padding: 28rpx 28rpx calc(24rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  border-radius: 26rpx 26rpx 0 0;
  overflow-y: auto;
}

.popup-header { justify-content: space-between; margin-bottom: 12rpx; }
.popup-title { color: #30283d; font-size: 32rpx; font-weight: 600; }
.form-row { align-items: flex-start; gap: 14rpx; }
.form-item { margin-top: 18rpx; }
.form-item.half { flex: 1; min-width: 0; }
.form-label { display: block; margin-bottom: 10rpx; color: #6b6676; font-size: 22rpx; }
.form-picker,
.form-input { height: 70rpx; }
.form-input { display: block; padding: 0 18rpx; }
.form-textarea {
  width: 100%;
  min-height: 130rpx;
  padding: 14rpx 18rpx;
  box-sizing: border-box;
  color: #393044;
  font-size: 24rpx;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}
.popup-actions { gap: 16rpx; margin-top: 26rpx; }
.popup-actions button { flex: 1; height: 76rpx; font-size: 25rpx; line-height: 76rpx; }

@media (max-width: 420px) {
  .record-no { max-width: 220rpx; }
}
</style>
