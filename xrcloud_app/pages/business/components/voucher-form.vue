<template>
  <scroll-view class="form-scroll" scroll-y :show-scrollbar="false">
    <view class="form-content">
      <view class="form-section">
        <view class="section-header">
          <text class="section-title">{{ pageTitle }}信息</text>
          <text class="collapse-action" @click="infoExpanded = !infoExpanded">
            {{ infoExpanded ? '收起' : '展开' }}
            <uni-icons :type="infoExpanded ? 'top' : 'bottom'" color="#722ed1" :size="16" />
          </text>
        </view>
        <view v-if="infoExpanded" class="field-list">
          <view class="field-row required-row">
            <text class="field-label">单据日期</text>
            <picker mode="date" :value="formDate" @change="onDateChange" style="width: 100%;">
              <view class="picker-value">
                <text>{{ formDate }}</text>
                <uni-icons type="right" color="#999" :size="18" />
              </view>
            </picker>
          </view>
          <view class="field-row required-row">
            <text class="field-label">单号</text>
            <text class="field-value">{{ form.orderNo || '生成中...' }}</text>
          </view>
        </view>
      </view>

      <view class="form-section">
        <view class="field-row required-row">
          <text class="field-label">{{ partnerLabel }}</text>
          <customer-autocomplete
            v-if="mode === 'receive'"
            v-model="form.partnerName"
            :disabled="isEditMode"
            :placeholder="`请选择${partnerLabel}`"
            @select-item="handlePartnerSelected"
            @select="handlePartnerTextSelected"
          />
          <supplier-autocomplete
            v-else
            v-model="form.partnerName"
            :disabled="isEditMode"
            :placeholder="`请选择${partnerLabel}`"
            @select-item="handlePartnerSelected"
            @select="handlePartnerTextSelected"
          />
        </view>
      </view>

      <view class="form-section">
        <view class="section-header">
          <text class="section-title">{{ accountTitle }}</text>
          <text class="section-count">{{ filledSettleItems.length }} 条</text>
        </view>
        <view v-if="settleItems.length" class="settle-list">
          <view v-for="(item, index) in settleItems" :key="item.key" class="settle-card">
            <view class="settle-card-header">
              <text class="settle-index">{{ index + 1 }}</text>
              <picker
                :range="settleOptions"
                range-key="label"
                :value="getSettleIndex(item)"
                @change="(event) => onSettleChange(event, item)"
                style="flex: 1;"
              >
                <view class="picker-value settle-picker-value">
                  <text :class="{ placeholder: !item.settleId }">
                    {{ getSettleName(item) || '请选择结算账户' }}
                  </text>
                  <uni-icons type="right" color="#999" :size="18" />
                </view>
              </picker>
              <uni-icons
                type="trash"
                color="#e5484d"
                :size="20"
                @click="removeSettleItem(index)"
              />
            </view>
            <view class="settle-fields">
              <view class="settle-amount-field">
                <text class="control-label">金额</text>
                <input
                  v-model="item.amount"
                  class="number-input"
                  type="digit"
                  @blur="normalizeSettleAmount(item)"
                />
              </view>
              <view class="settle-note-field">
                <text class="control-label">备注</text>
                <input
                  v-model="item.note"
                  class="note-input"
                  type="text"
                  maxlength="100"
                  placeholder="请输入备注"
                />
              </view>
            </view>
          </view>
        </view>
        <button class="outline-button" @click="addSettleItem">
          <uni-icons type="plusempty" color="#722ed1" :size="18" />
          <text>添加{{ accountTitle }}</text>
        </button>
        <view class="total-row">
          <text>本次{{ mode === 'receive' ? '收款' : '付款' }}</text>
          <text class="total-amount">{{ formatAmount(totalAmount) }}</text>
        </view>
      </view>

      <view class="form-section">
        <view class="section-header">
          <text class="section-title">订单结算</text>
          <text class="section-count">{{ selectedOrderItems.length }} 条</text>
        </view>
        <view v-if="!form.partnerId" class="section-hint">请先选择{{ partnerLabel }}，再选择关联单据</view>
        <view v-else-if="ordersLoading" class="section-hint">正在加载未结清单据...</view>
        <view v-else-if="!orderItems.length" class="section-hint">暂无未结清单据</view>
        <view v-else class="order-settle-list">
          <view
            v-for="item in orderItems"
            :key="item.key"
            class="order-settle-card"
            :class="{ selected: isOrderSelected(item) }"
            @click="toggleOrder(item)"
          >
            <view class="order-settle-header">
              <view class="order-check">
                <uni-icons
                  :type="isOrderSelected(item) ? 'checkbox-filled' : 'circle'"
                  :color="isOrderSelected(item) ? '#722ed1' : '#b8b2c0'"
                  :size="22"
                />
                <text>{{ item.orderNo || '-' }}</text>
              </view>
              <text class="order-date">{{ formatDate(item.createTime) }}</text>
            </view>
            <view class="order-settle-summary">
              <text>应{{ mode === 'receive' ? '收' : '付' }} {{ formatAmount(item.payableAmount) }}</text>
              <text>未{{ mode === 'receive' ? '收' : '付' }} {{ formatAmount(item.unpaidAmount) }}</text>
            </view>
            <view v-if="isOrderSelected(item)" class="order-settle-fields" @click.stop>
              <view class="order-amount-field">
                <text class="control-label">本次{{ mode === 'receive' ? '收款' : '付款' }}</text>
                <input
                  v-model="item.amount"
                  class="number-input"
                  type="digit"
                  @blur="normalizeOrderAmount(item)"
                />
              </view>
              <view class="order-note-field">
                <text class="control-label">备注</text>
                <input
                  v-model="item.note"
                  class="note-input"
                  type="text"
                  maxlength="100"
                  placeholder="请输入备注"
                />
              </view>
            </view>
          </view>
        </view>
        <view v-if="orderItems.length" class="order-actions">
          <button class="small-outline-button" @click="selectAllOrders">全部{{ mode === 'receive' ? '收款' : '付款' }}</button>
          <button class="small-plain-button" @click="clearOrderSelection">清空选择</button>
        </view>
      </view>

      <view class="form-section">
        <view class="field-row note-row">
          <text class="field-label">备注</text>
          <textarea v-model="form.note" class="note-textarea" maxlength="100" placeholder="请输入备注" />
        </view>
        <view v-if="isRoot" class="field-row">
          <text class="field-label">状态</text>
          <picker :range="statusOptions" range-key="label" :value="statusIndex" @change="onStatusChange" style="width: 100%;">
            <view class="picker-value">
              <text>{{ statusOptions[statusIndex].label }}</text>
              <uni-icons type="right" color="#999" :size="18" />
            </view>
          </picker>
        </view>
      </view>

      <view class="summary-section">
        <view class="summary-row">
          <text>账户数量（{{ filledSettleItems.length }}）</text>
          <text>合计金额：<text class="summary-amount">{{ formatAmount(totalAmount) }}</text></text>
        </view>
        <view class="summary-row secondary">
          <text>关联单据</text>
          <text>{{ selectedOrderItems.length }} 条</text>
        </view>
      </view>

      <view class="form-actions">
        
        <button class="reset-button" :disabled="saving" @click="confirmReset">重置</button>
        <button class="save-button" :disabled="saving" @click="saveForm">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </view>
      <view v-if="isEditMode" class="form-actions delete-actions">
        <button class="delete-button" :disabled="saving" @click="deleteForm">
          <uni-icons type="trash" color="#fff" :size="20" />
          <text>删除{{ pageTitle }}</text>
        </button>
      </view>
      <view class="bottom-space" />
    </view>
  </scroll-view>
</template>

<script>
import CustomerAutocomplete from '../../../components/customer-autocomplete.vue'
import SupplierAutocomplete from '../../../components/supplier-autocomplete.vue'
import {
  addPaymentVoucher,
  addReceivePaymentVoucher,
  createPaymentVoucherNo,
  createReceivePaymentVoucherNo,
  editPaymentVoucher,
  editReceivePaymentVoucher,
  getUserInfo,
  listAccountSettles,
  listPaymentSettleItems,
  listPurchaseOrders,
  listReceivePaymentSettleItems,
  listSaleOrders,
  removePaymentVoucher,
  removeReceivePaymentVoucher
} from '../../../common/api'
import { clearSession, getUser, isLoggedIn, updateUser } from '../../../common/auth'

const createEmptyForm = () => ({
  id: undefined,
  orderNo: '',
  partnerId: '',
  partnerName: '',
  amount: 0,
  note: '',
  status: 1,
  createTime: new Date()
})

const toNumber = (value, fallback = 0) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

const formatDateOnly = (value) => {
  if (!value) {
    const now = new Date()
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return formatDateOnly()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export default {
  name: 'BusinessVoucherForm',
  components: {
    CustomerAutocomplete,
    SupplierAutocomplete
  },
  props: {
    mode: {
      type: String,
      default: 'receive'
    },
    routeOptions: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      user: getUser() || {},
      form: createEmptyForm(),
      infoExpanded: true,
      saving: false,
      initializing: false,
      initialized: false,
      redirecting: false,
      ordersLoading: false,
      settleOptions: [],
      settleItems: [],
      orderItems: [],
      statusIndex: 1,
      statusOptions: [
        { label: '待审核', value: 0 },
        { label: '正常', value: 1 }
      ]
    }
  },
  computed: {
    pageTitle() {
      return this.mode === 'receive' ? '收款单' : '付款单'
    },
    partnerLabel() {
      return this.mode === 'receive' ? '客户' : '供应商'
    },
    accountTitle() {
      return this.mode === 'receive' ? '收款账户' : '付款账户'
    },
    isRoot() {
      return Number(this.user.isRoot) === 1
    },
    isEditMode() {
      return this.form.id !== undefined && this.form.id !== null && this.form.id !== ''
    },
    formDate() {
      return formatDateOnly(this.form.createTime)
    },
    filledSettleItems() {
      return this.settleItems.filter((item) => item.settleId || toNumber(item.amount) !== 0)
    },
    totalAmount() {
      return this.roundMoney(this.filledSettleItems.reduce((sum, item) => sum + toNumber(item.amount), 0))
    },
    selectedOrderItems() {
      return this.orderItems.filter((item) => item.amount !== undefined && item.amount !== null && item.amount !== '')
    },
    orderListApi() {
      return this.mode === 'receive' ? listSaleOrders : listPurchaseOrders
    }
  },
  mounted() {
    this.initialize()
  },
  methods: {
    async initialize() {
      if (this.initializing || this.initialized || this.redirecting) return
      this.initializing = true
      try {
        if (!isLoggedIn()) {
          this.redirectToLogin()
          return
        }
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...currentUser }
        updateUser(this.user)
        await this.loadSettleOptions()
        await this.loadRouteData()
        this.initialized = true
      } catch (error) {
        uni.showToast({
          title: error && error.message ? error.message : `${this.pageTitle}初始化失败`,
          icon: 'none'
        })
      } finally {
        this.initializing = false
      }
    },
    async loadSettleOptions() {
      const data = await listAccountSettles()
      this.settleOptions = (Array.isArray(data) ? data : [])
        .filter((item) => item && item.id !== undefined && item.id !== null)
        .map((item) => ({ label: item.name || `账户${item.id}`, value: String(item.id) }))
    },
    parseEditItem(value) {
      if (!value) return null
      if (typeof value === 'object') return value
      try {
        return JSON.parse(String(value))
      } catch (error) {
        return null
      }
    },
    async loadRouteData() {
      const options = this.routeOptions || {}
      let editItem = this.parseEditItem(options.editItem)
      if (!editItem && options.editStorageKey) {
        const storageKey = decodeURIComponent(String(options.editStorageKey))
        editItem = uni.getStorageSync(storageKey)
        uni.removeStorageSync(storageKey)
      }
      if (editItem && editItem.id !== undefined && editItem.id !== null) {
        await this.applyVoucher(editItem)
        return
      }
      await this.resetForm(false)
    },
    async applyVoucher(item) {
      const form = createEmptyForm()
      Object.assign(form, item)
      form.partnerId = String(
        this.mode === 'receive' ? (item.customerId || '') : (item.supplierId || '')
      )
      form.partnerName = this.mode === 'receive'
        ? (item.customerId_dictText || item.customerName || '')
        : (item.supplierId_dictText || item.supplierName || '')
      form.amount = toNumber(item.amount)
      form.status = Number(item.status) === 0 ? 0 : 1
      this.form = form
      this.statusIndex = form.status === 0 ? 0 : 1
      await this.loadSettleItems(item.id)
      await this.loadOrderItems(item.id)
    },
    async loadSettleItems(id) {
      const data = this.mode === 'receive'
        ? await listReceivePaymentSettleItems(id, this.user.id)
        : await listPaymentSettleItems(id, this.user.id)
      this.settleItems = (Array.isArray(data) ? data : []).map((item, index) => ({
        ...item,
        key: `settle-${item.id || index}-${Date.now()}`,
        settleId: item.settleId === undefined || item.settleId === null ? '' : String(item.settleId),
        amount: item.amount === undefined || item.amount === null ? '' : item.amount
      }))
      if (!this.settleItems.length) this.addSettleItem()
    },
    async loadOrderItems(voucherId) {
      if (!this.form.partnerId) {
        this.orderItems = []
        return
      }
      this.ordersLoading = true
      try {
        const params = voucherId
          ? { orderId: voucherId, userId: this.user.id }
          : {
              current: 1,
              pageSize: 200,
              order: 'desc',
              column: 'createTime',
              unpaidOnly: 1,
              userId: this.user.id,
              [this.mode === 'receive' ? 'customerId' : 'supplierId']: this.form.partnerId
            }
        const result = await this.orderListApi(params)
        const list = result && Array.isArray(result.records) ? result.records : []
        this.orderItems = list.map((item, index) => ({
          ...item,
          key: `order-${item.id || item.orderNo || index}-${Date.now()}`,
          amount: item.amount === undefined || item.amount === null ? undefined : item.amount,
          note: item.note || ''
        }))
      } finally {
        this.ordersLoading = false
      }
    },
    async generateOrderNo() {
      if (this.form.id || this.form.orderNo) return
      this.form.orderNo = this.mode === 'receive'
        ? await createReceivePaymentVoucherNo()
        : await createPaymentVoucherNo()
    },
    handlePartnerSelected(payload) {
      if (!payload || this.isEditMode) return
      this.form.partnerId = payload.id === undefined || payload.id === null ? '' : String(payload.id)
      this.form.partnerName = String(payload.name || '').trim()
      this.loadOrderItems()
    },
    handlePartnerTextSelected(value) {
      if (!value && !this.isEditMode) {
        this.form.partnerId = ''
        this.form.partnerName = ''
        this.orderItems = []
      }
    },
    onDateChange(event) {
      const value = event && event.detail ? String(event.detail.value || '') : ''
      if (value) this.form.createTime = new Date(`${value}T00:00:00`)
    },
    addSettleItem() {
      this.settleItems.push({
        id: undefined,
        key: `settle-new-${Date.now()}-${Math.random()}`,
        settleId: '',
        amount: '',
        note: ''
      })
    },
    removeSettleItem(index) {
      if (this.settleItems.length === 1) {
        this.settleItems[0] = {
          ...this.settleItems[0],
          id: undefined,
          settleId: '',
          amount: '',
          note: ''
        }
        return
      }
      this.settleItems.splice(index, 1)
    },
    getSettleIndex(item) {
      const index = this.settleOptions.findIndex((option) => String(option.value) === String(item.settleId))
      return index >= 0 ? index : 0
    },
    getSettleName(item) {
      const option = this.settleOptions.find((entry) => String(entry.value) === String(item.settleId))
      return option ? option.label : item.settleId_dictText || ''
    },
    onSettleChange(event, item) {
      const index = Number(event && event.detail ? event.detail.value || 0 : 0)
      const option = this.settleOptions[index]
      item.settleId = option ? String(option.value) : ''
      item.settleId_dictText = option ? option.label : ''
    },
    normalizeSettleAmount(item) {
      item.amount = this.roundMoney(Math.max(0, toNumber(item.amount)))
    },
    toggleOrder(item) {
      if (this.isOrderSelected(item)) {
        item.amount = undefined
        return
      }
      item.amount = this.roundMoney(Math.max(0, toNumber(item.unpaidAmount)))
    },
    isOrderSelected(item) {
      return item.amount !== undefined && item.amount !== null && item.amount !== ''
    },
    normalizeOrderAmount(item) {
      const unpaid = Math.max(0, toNumber(item.unpaidAmount))
      item.amount = this.roundMoney(Math.min(unpaid, Math.max(0, toNumber(item.amount))))
    },
    selectAllOrders() {
      this.orderItems.forEach((item) => {
        item.amount = this.roundMoney(Math.max(0, toNumber(item.unpaidAmount)))
      })
    },
    clearOrderSelection() {
      this.orderItems.forEach((item) => {
        item.amount = undefined
      })
    },
    onStatusChange(event) {
      this.statusIndex = Number(event && event.detail ? event.detail.value || 0 : 0)
      this.form.status = this.statusOptions[this.statusIndex].value
    },
    buildPayload() {
      const amountItems = this.selectedOrderItems.map((item) => ({
        id: item.id,
        orderNo: item.orderNo,
        amount: this.roundMoney(toNumber(item.amount)),
        note: item.note || ''
      }))
      const settleItems = this.filledSettleItems.map((item) => ({
        id: item.id,
        settleId: String(item.settleId),
        amount: this.roundMoney(toNumber(item.amount)),
        note: item.note || ''
      }))
      return {
        id: this.form.id,
        orderNo: this.form.orderNo,
        userId: this.user.id,
        [this.mode === 'receive' ? 'customerId' : 'supplierId']: this.form.partnerId,
        status: this.form.status,
        amount: this.totalAmount,
        note: this.form.note || '',
        createTime: this.form.createTime,
        settleItems,
        amountItems
      }
    },
    async saveForm() {
      if (this.saving) return
      if (!this.form.partnerId) {
        uni.showToast({ title: `请选择${this.partnerLabel}`, icon: 'none' })
        return
      }
      if (!this.filledSettleItems.length) {
        uni.showToast({ title: `请录入${this.accountTitle}`, icon: 'none' })
        return
      }
      const invalid = this.filledSettleItems.some((item) => !item.settleId || toNumber(item.amount) <= 0)
      if (invalid) {
        uni.showToast({ title: `${this.accountTitle}录入不完整`, icon: 'none' })
        return
      }
      this.saving = true
      uni.showLoading({ title: '保存中...', mask: true })
      try {
        const payload = this.buildPayload()
        if (this.isEditMode) {
          if (this.mode === 'receive') await editReceivePaymentVoucher(payload)
          else await editPaymentVoucher(payload)
        } else if (this.mode === 'receive') {
          await addReceivePaymentVoucher(payload)
        } else {
          await addPaymentVoucher(payload)
        }
        uni.showToast({ title: '保存成功', icon: 'success' })
        await this.resetForm(false)
      } catch (error) {
        uni.showToast({
          title: error && error.message ? error.message : '保存失败',
          icon: 'none'
        })
      } finally {
        uni.hideLoading()
        this.saving = false
      }
    },
    async deleteForm() {
      if (!this.isEditMode || this.saving) return
      const result = await new Promise((resolve) => {
        uni.showModal({
          title: '确认删除',
          content: '删除后将无法恢复，是否继续？',
          success: resolve
        })
      })
      if (!result.confirm) return
      this.saving = true
      uni.showLoading({ title: '删除中...', mask: true })
      try {
        const payload = { id: this.form.id, userId: this.user.id }
        if (this.mode === 'receive') await removeReceivePaymentVoucher(payload)
        else await removePaymentVoucher(payload)
        await this.resetForm(false)
        uni.showToast({ title: '删除成功', icon: 'success' })
        setTimeout(() => {
          uni.navigateTo({ url: this.mode === 'receive' ? '/pages/receive-payment/list' : '/pages/payment/list' })
        }, 350)
      } catch (error) {
        uni.showToast({
          title: error && error.message ? error.message : '删除失败',
          icon: 'none'
        })
      } finally {
        uni.hideLoading()
        this.saving = false
      }
    },
    async resetForm(showConfirm = true) {
      if (showConfirm) {
        const result = await new Promise((resolve) => {
          uni.showModal({
            title: '确认重置',
            content: `将清空当前${this.pageTitle}内容，是否继续？`,
            success: resolve
          })
        })
        if (!result.confirm) return
      }
      this.form = createEmptyForm()
      this.settleItems = []
      this.orderItems = []
      this.statusIndex = 1
      this.addSettleItem()
      await this.generateOrderNo()
    },
    openHistory() {
      uni.navigateTo({
        url: this.mode === 'receive' ? '/pages/receive-payment/list' : '/pages/payment/list'
      })
    },
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    },
    formatDate(value) {
      return value ? String(value).split(' ')[0] : '-'
    },
    formatAmount(value) {
      return `¥${this.toNumber(value).toFixed(2)}`
    },
    toNumber(value) {
      return toNumber(value)
    },
    roundMoney(value) {
      return Math.round(toNumber(value) * 100) / 100
    }
  }
}
</script>

<style lang="scss" scoped>
.form-scroll { width: 100%; height: 100%; box-sizing: border-box; }
.form-content { min-height: 100%; padding: 18rpx 20rpx calc(36rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.form-section, .summary-section { margin-bottom: 16rpx; padding: 20rpx; background: #fff; border: 1rpx solid #ece9f2; border-radius: 16rpx; box-shadow: 0 5rpx 16rpx rgba(67, 47, 119, .05); }
.section-header { display: flex; align-items: center; justify-content: space-between; }
.section-title { color: #454252; font-size: 28rpx; font-weight: 600; }
.section-count, .collapse-action { color: #722ed1; font-size: 21rpx; }
.collapse-action { display: flex; align-items: center; gap: 4rpx; }
.field-list { margin-top: 8rpx; }
.field-row { display: flex; align-items: center; min-height: 76rpx; gap: 18rpx; border-bottom: 1rpx solid #f0edf5; padding: 10rpx 0; }
.field-row:last-child { border-bottom: 0; }
.required-row .field-label::before { content: '*'; margin-right: 4rpx; color: #e5484d; }
.field-label { flex: 0 0 126rpx; color: #5f596b; font-size: 24rpx; }
.field-value, .picker-value { flex: 1; min-width: 0; color: #393044; font-size: 25rpx; }
.picker-value { display: flex; align-items: center; justify-content: space-between; min-height: 72rpx; width: 100%; }
.picker-value text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.placeholder { color: #aaa5b5; }
.settle-list, .order-settle-list { margin-top: 16rpx; }
.settle-card, .order-settle-card { margin-bottom: 12rpx; padding: 16rpx; background: #faf9fc; border: 1rpx solid #eeeaf4; border-radius: 12rpx; }
.settle-card-header, .order-settle-header, .order-settle-summary, .total-row, .order-actions { display: flex; align-items: center; justify-content: space-between; }
.settle-card-header { gap: 10rpx; }
.settle-index { display: flex; align-items: center; justify-content: center; flex: 0 0 42rpx; width: 42rpx; height: 42rpx; color: #fff; font-size: 20rpx; background: #75aef5; border-radius: 8rpx; }
.settle-picker-value { min-height: 54rpx; }
.settle-fields, .order-settle-fields { display: flex; gap: 12rpx; margin-top: 12rpx; padding-top: 12rpx; border-top: 1rpx solid #eeeaf4; }
.settle-amount-field, .order-amount-field { flex: 0 0 34%; min-width: 0; }
.settle-note-field, .order-note-field { flex: 1; min-width: 0; }
.control-label { display: block; color: #918b99; font-size: 20rpx; }
.number-input, .note-input { width: 100%; height: 58rpx; margin-top: 6rpx; padding: 0 12rpx; box-sizing: border-box; color: #393044; font-size: 24rpx; background: #fff; border: 1rpx solid #ded9e8; border-radius: 8rpx; }
.number-input { text-align: right; }
.outline-button, .small-outline-button, .small-plain-button { display: flex; align-items: center; justify-content: center; gap: 6rpx; margin: 12rpx 0 0; color: #722ed1; background: #f4efff; border: 1rpx solid #d8c7f5; border-radius: 9rpx; }
.outline-button { width: 100%; height: 66rpx; font-size: 24rpx; line-height: 66rpx; }
.small-outline-button, .small-plain-button { flex: 1; height: 56rpx; font-size: 22rpx; line-height: 56rpx; }
.small-plain-button { color: #666; background: #f2f3f5; border-color: #e3e4e8; }
.outline-button::after, .small-outline-button::after, .small-plain-button::after, .form-actions button::after { border: 0; }
.total-row { margin-top: 16rpx; padding-top: 14rpx; color: #6f6979; font-size: 23rpx; border-top: 1rpx solid #eeeaf4; }
.total-amount, .summary-amount { color: #e5484d; font-size: 28rpx; font-weight: 600; }
.section-hint { margin-top: 16rpx; padding: 26rpx 0; color: #aaa5b5; font-size: 23rpx; text-align: center; background: #faf9fc; border-radius: 10rpx; }
.order-settle-card.selected { border-color: #cbb5ef; background: #fbf9ff; }
.order-check { display: flex; align-items: center; min-width: 0; gap: 8rpx; color: #393044; font-size: 24rpx; }
.order-check text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.order-date { flex: 0 0 auto; margin-left: 12rpx; color: #9a95a4; font-size: 20rpx; }
.order-settle-summary { margin-top: 12rpx; color: #8b8495; font-size: 21rpx; }
.order-actions { gap: 12rpx; margin-top: 4rpx; }
.note-row { align-items: flex-start; }
.note-textarea { flex: 1; min-height: 82rpx; padding: 12rpx; box-sizing: border-box; color: #393044; font-size: 24rpx; background: #faf9fc; border: 1rpx solid #ded9e8; border-radius: 8rpx; }
.summary-section { padding: 18rpx 20rpx; }
.summary-row { display: flex; align-items: center; justify-content: space-between; min-height: 48rpx; color: #4d4858; font-size: 23rpx; }
.summary-row.secondary { color: #918b99; font-size: 21rpx; }
.form-actions { display: flex; gap: 12rpx; margin-top: 4rpx; }
.delete-actions { margin-top: 22rpx; }
.form-actions button { flex: 1; height: 76rpx; margin: 0; padding: 0 10rpx; font-size: 24rpx; line-height: 76rpx; border-radius: 10rpx; }
.history-button { display: flex; align-items: center; justify-content: center; gap: 6rpx; color: #722ed1; background: #f4efff; }
.reset-button { color: #666; background: #fff; }
.save-button { color: #fff; background: #722ed1; }
.delete-button { display: flex; align-items: center; justify-content: center; gap: 6rpx; color: #fff; background: #ff5e5e; }
.form-actions button[disabled] { opacity: .6; }
.bottom-space { height: 30rpx; }
</style>
