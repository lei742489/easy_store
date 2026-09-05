<template>
  <scroll-view class="form-scroll" scroll-y :show-scrollbar="false">
    <view class="form-content">
      <view class="form-section">
        <view class="section-header">
          <text class="section-title">进货单信息</text>
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
          <view class="field-row">
            <text class="field-label">营业员</text>
            <picker
              v-if="isRoot"
              :range="cashierOptions"
              range-key="label"
              :value="cashierIndex"
              @change="onCashierChange"
              style="width: 100%;"
            >
              <view class="picker-value">
                <text>{{ selectedCashierName || '请选择营业员' }}</text>
                <uni-icons type="right" color="#999" :size="18" />
              </view>
            </picker>
            <view v-else class="picker-value"><text>{{ selectedCashierName || '-' }}</text></view>
          </view>
          <view class="field-row">
            <text class="field-label">进货类型</text>
            <picker
              :range="orderTypeOptions"
              range-key="label"
              :value="orderTypeIndex"
              @change="onOrderTypeChange"
              style="width: 100%;"
            >
              <view class="picker-value">
                <text>{{ orderTypeOptions[orderTypeIndex].label }}</text>
                <uni-icons type="right" color="#999" :size="18" />
              </view>
            </picker>
          </view>
        </view>
      </view>

      <view class="form-section">
        <view class="field-row required-row">
          <text class="field-label">供应商</text>
          <supplier-autocomplete
            v-model="form.supplierName"
            placeholder="请选择供应商"
            @select-item="handleSupplierSelected"
            @select="handleSupplierTextSelected"
          />
        </view>
      </view>

      <view class="form-section goods-section">
        <view class="section-header">
          <text class="section-title">货品列表</text>
          <text v-if="form.items.length" class="section-count">{{ form.items.length }} 种</text>
        </view>
        <view v-if="form.items.length" class="goods-item-list">
          <view
            v-for="(item, index) in form.items"
            :key="item.key || index"
            class="goods-item-card"
          >
            <view class="goods-item-header">
              <view class="goods-item-index">{{ index + 1 }}</view>
              <view class="goods-item-main">
                <text class="goods-item-name">{{ item.goodsName || item.goodsId || '-' }}</text>
                <text class="goods-item-meta">{{ item.unit || '-' }} · 库存 {{ formatQuantity(item.stock) }}</text>
              </view>
              <uni-icons type="trash" color="#e5484d" :size="20" @click="removeItem(index)" />
            </view>
            <view class="goods-item-controls">
              <view class="number-control">
                <text class="control-label">单价</text>
                <input v-model="item.unitPrice" class="number-input" type="digit" @blur="handleItemChange(item)" />
              </view>
              <view class="number-control">
                <text class="control-label">数量</text>
                <input v-model="item.displayQuantity" class="number-input" type="digit" @blur="handleItemChange(item)" />
              </view>
              <view class="number-control">
                <text class="control-label">金额</text>
                <input
                  v-model="item.totalAmount"
                  class="number-input"
                  type="digit"
                  @blur="handleItemTotalChange(item)"
                />
              </view>
            </view>
            <view class="goods-item-note">
              <text class="control-label">备注</text>
              <input v-model="item.note" class="goods-note-input" type="text" maxlength="100" placeholder="请输入备注" />
            </view>
          </view>
        </view>
        <view v-else class="goods-empty">请先选择货品</view>
        <button class="choose-goods-button" @click="openGoodsSelect">
          <uni-icons type="plusempty" color="#fff" :size="21" />
          <text>选择货品</text>
        </button>
      </view>

      <view v-if="form.items.length">
        <view class="form-section">
          <view class="field-row">
            <text class="field-label">折扣率</text>
            <view class="inline-input">
              <input v-model="form.discountRate" class="text-input" type="digit" @blur="handleDiscountRateChange" />
              <text class="input-suffix">%</text>
            </view>
          </view>
          <view class="field-row">
            <text class="field-label">运费</text>
            <view class="inline-input">
              <input v-model="form.freightAmount" class="text-input" type="digit" @blur="normalizeFreightAmount" />
              <text class="input-suffix">元</text>
            </view>
          </view>
          <view class="field-row">
            <text class="field-label">本次付款</text>
            <view class="inline-input">
              <input v-model="form.paidAmount" class="text-input" type="digit" @blur="normalizeMoneyFields" />
              <text class="paid-state" @click="fillPaidAmount">已付</text>
            </view>
          </view>
          <view class="field-row note-row">
            <text class="field-label">备注</text>
            <textarea v-model="form.note" class="note-input" maxlength="100" placeholder="请输入备注" />
          </view>
          <view class="field-row">
            <text class="field-label">结算账户</text>
            <picker
              :range="settleOptions"
              range-key="label"
              :value="settleIndex"
              @change="onSettleChange"
              style="width: 100%;"
            >
              <view class="picker-value">
                <text :class="{ placeholder: !form.settleId }">{{ selectedSettleName || '请选择结算账户' }}</text>
                <uni-icons type="right" color="#999" :size="18" />
              </view>
            </picker>
          </view>
          <view v-if="isRoot" class="field-row">
            <text class="field-label">状态</text>
            <picker
              :range="statusOptions"
              range-key="label"
              :value="statusIndex"
              @change="onStatusChange"
              style="width: 100%;"
            >
              <view class="picker-value">
                <text>{{ statusOptions[statusIndex].label }}</text>
                <uni-icons type="right" color="#999" :size="18" />
              </view>
            </picker>
          </view>
        </view>
        <view class="summary-section">
          <view class="summary-row">
            <text>商品数量（{{ form.items.length }}）</text>
            <text>合计金额：<text class="summary-amount">{{ formatAmount(form.totalAmount) }}</text></text>
          </view>
          <view class="summary-row secondary"><text>折后金额</text><text>{{ formatAmount(form.discountedAmount) }}</text></view>
          <view class="summary-row secondary"><text>应付金额</text><text>{{ formatAmount(form.payableAmount) }}</text></view>
          <view class="summary-row secondary"><text>未付金额</text><text class="unpaid-amount">{{ formatAmount(form.unpaidAmount) }}</text></view>
        </view>
      </view>

      <view class="form-actions">
        <template v-if="form.items.length">
          <button class="reset-button" :disabled="saving" @click="confirmReset">重置</button>
          <button class="save-button" :disabled="saving" @click="saveForm">{{ saving ? '保存中...' : '保存' }}</button>
        </template>
      </view>
      <view class="form-actions delete-actions">
        <button v-if="isEditMode" class="delete-button" :disabled="saving" @click="deleteForm">
          <uni-icons type="trash" color="#ffffff" :size="20" />
          <text>删除</text>
        </button>
      </view>
      <view class="bottom-space" />
    </view>
  </scroll-view>
</template>

<script>
import SupplierAutocomplete from '../../../components/supplier-autocomplete.vue'
import {
  addPurchaseOrder,
  createPurchaseOrderNo,
  editPurchaseOrder,
  getUserInfo,
  listAccountSettles,
  listAppUsers,
  removePurchaseOrder
} from '../../../common/api'
import { clearSession, getUser, isLoggedIn, updateUser } from '../../../common/auth'

const createEmptyForm = () => ({
  id: undefined,
  orderNo: '',
  supplierId: '',
  supplierName: '',
  orderType: 1,
  settleId: '',
  cashierId: '',
  cashierName: '',
  status: 1,
  totalAmount: 0,
  payableAmount: 0,
  paidAmount: 0,
  unpaidAmount: 0,
  discountedAmount: 0,
  discountRate: 100,
  freightAmount: 0,
  note: '',
  createTime: new Date(),
  items: []
})

const toNumber = (value, fallback = 0) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

const roundQuantity = (value, fallback = 0) =>
  Math.round(toNumber(value, fallback) * 100) / 100

const formatDateOnly = (value) => {
  const date = value ? new Date(value) : new Date()
  if (Number.isNaN(date.getTime())) return formatDateOnly(new Date())
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export default {
  name: 'BusinessPurchaseForm',
  components: { SupplierAutocomplete },
  props: {
    routeOptions: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      user: getUser() || {},
      form: createEmptyForm(),
      cashierOptions: [{ label: '当前营业员', value: '' }],
      settleOptions: [],
      cashierIndex: 0,
      settleIndex: 0,
      orderTypeIndex: 0,
      statusIndex: 1,
      orderTypeOptions: [
        { label: '进货入库', value: 1 },
        { label: '进货退货', value: 2 }
      ],
      statusOptions: [
        { label: '待审核', value: 0 },
        { label: '正常', value: 1 }
      ],
      infoExpanded: true,
      saving: false,
      initialized: false,
      initializing: false,
      redirecting: false
    }
  },
  computed: {
    isRoot() { return Number(this.user.isRoot) === 1 },
    selectedCashierName() {
      const option = this.cashierOptions[this.cashierIndex]
      return this.form.cashierName || (option && option.label) || ''
    },
    selectedSettleName() {
      const option = this.settleOptions.find((item) => String(item.value) === String(this.form.settleId))
      return option ? option.label : ''
    },
    formDate() { return formatDateOnly(this.form.createTime) },
    isEditMode() {
      return this.form.id !== undefined && this.form.id !== null && this.form.id !== ''
    }
  },
  mounted() { this.initialize() },
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
        await Promise.all([this.loadCashiers(), this.loadSettles()])
        await this.loadRouteData()
        this.initialized = true
      } catch (error) {
        uni.showToast({ title: error && error.message ? error.message : '进货单初始化失败', icon: 'none' })
      } finally {
        this.initializing = false
      }
    },
    async loadCashiers() {
      if (!this.isRoot) {
        this.form.cashierId = String(this.user.id || '')
        this.form.cashierName = this.user.realName || this.user.userName || ''
        return
      }
      const users = await listAppUsers()
      const options = (Array.isArray(users) ? users : [])
        .filter((item) => Number(item.status) === 1)
        .map((item) => ({ label: item.realName || item.userName || `员工${item.id}`, value: String(item.id) }))
      this.cashierOptions = options.length
        ? options
        : [{ label: this.user.realName || this.user.userName || '当前营业员', value: String(this.user.id || '') }]
      this.syncCashierIndex()
    },
    async loadSettles() {
      const data = await listAccountSettles()
      this.settleOptions = (Array.isArray(data) ? data : [])
        .filter((item) => item && item.id !== undefined && item.id !== null)
        .map((item) => ({ label: item.name || `账户${item.id}`, value: String(item.id) }))
      if (!this.form.settleId && this.settleOptions.length) this.form.settleId = this.settleOptions[0].value
      this.syncSettleIndex()
    },
    async loadRouteData() {
      const options = this.routeOptions || {}
      let editItem = this.parseEditItem(options.editItem)
      if (!editItem && options.editStorageKey) {
        const storageKey = decodeURIComponent(String(options.editStorageKey))
        editItem = uni.getStorageSync(storageKey)
        uni.removeStorageSync(storageKey)
      }
      if (editItem && editItem.id) {
        this.applyOrder(editItem)
        return
      }
      this.resetForm(false)
      await this.generateOrderNo()
    },
    parseEditItem(value) {
      if (!value) return null
      if (typeof value === 'object') return value
      try { return JSON.parse(String(value)) } catch (error) { return null }
    },
    applyOrder(order) {
      const form = createEmptyForm()
      Object.assign(form, order)
      form.supplierId = String(order.supplierId || '')
      form.supplierName = order.supplierId_dictText || order.supplierName || ''
      form.cashierId = String(order.cashierId || this.user.id || '')
      form.cashierName = order.cashierName || order.cashierId_dictText || ''
      form.orderType = Number(order.orderType) === 2 ? 2 : 1
      form.discountRate = toNumber(order.discountRate, 100)
      form.paidAmount = toNumber(order.paidAmount)
      form.freightAmount = toNumber(order.freightAmount)
      form.status = Number(order.status) === 0 ? 0 : 1
      form.items = (Array.isArray(order.items) ? order.items : []).map((item, index) => {
        const quantity = Math.abs(roundQuantity(item.quantity, 1))
        return {
          ...item,
          key: `${item.id || item.goodsId || index}-${Date.now()}`,
          goodsId: String(item.goodsId || ''),
          goodsName: item.goodsName || item.goodsId_dictText || '',
          displayQuantity: quantity,
          quantity,
          unitPrice: toNumber(item.unitPrice),
          stock: toNumber(item.stock),
          totalAmount: toNumber(item.totalAmount),
          totalAmountEdited: item.totalAmount !== undefined && item.totalAmount !== null
        }
      })
      this.form = form
      this.orderTypeIndex = form.orderType === 2 ? 1 : 0
      this.syncStatusIndex()
      this.syncCashierIndex()
      this.syncSettleIndex()
      this.recalculate()
    },
    async generateOrderNo() {
      if (this.form.id || this.form.orderNo) return
      this.form.orderNo = await createPurchaseOrderNo()
    },
    handleSupplierSelected(payload) {
      if (!payload) return
      this.form.supplierId = String(payload.id || '')
      this.form.supplierName = String(payload.name || '')
    },
    handleSupplierTextSelected(value) {
      if (!value) {
        this.form.supplierId = ''
        this.form.supplierName = ''
      }
    },
    onDateChange(event) {
      const value = event && event.detail ? String(event.detail.value || '') : ''
      if (value) this.form.createTime = new Date(`${value}T00:00:00`)
    },
    onCashierChange(event) {
      this.cashierIndex = Number(event.detail.value || 0)
      const option = this.cashierOptions[this.cashierIndex]
      if (option) {
        this.form.cashierId = String(option.value || '')
        this.form.cashierName = option.label
      }
    },
    onSettleChange(event) {
      this.settleIndex = Number(event.detail.value || 0)
      const option = this.settleOptions[this.settleIndex]
      this.form.settleId = option ? String(option.value) : ''
    },
    onOrderTypeChange(event) {
      this.orderTypeIndex = Number(event.detail.value || 0)
      this.form.orderType = this.orderTypeOptions[this.orderTypeIndex].value
      this.recalculate()
    },
    onStatusChange(event) {
      this.statusIndex = Number(event.detail.value || 0)
      const option = this.statusOptions[this.statusIndex]
      this.form.status = option ? option.value : 1
    },
    openGoodsSelect() {
      const storageKey = `easy-store-purchase-selection-${Date.now()}`
      uni.setStorageSync(storageKey, this.form.items)
      uni.navigateTo({
        url: `/pages/goods-select/index?type=purchase&storageKey=${encodeURIComponent(storageKey)}`,
        success: (res) => {
          const channel = res && res.eventChannel
          if (channel) channel.on('selected', (items) => this.mergeSelectedItems(items))
        }
      })
    },
    mergeSelectedItems(items) {
      if (!Array.isArray(items)) return
      this.form.items = items
        .filter((item) => item && item.goodsId !== undefined && item.goodsId !== null)
        .map((item, index) => {
          const quantity = Math.max(0, roundQuantity(
            item.displayQuantity !== undefined ? item.displayQuantity : item.quantity, 1
          ))
          return {
            ...item,
            key: item.key || `${item.goodsId}-${Date.now()}-${index}`,
            goodsId: String(item.goodsId),
            goodsName: item.goodsName || item.title || item.goodsId_dictText || String(item.goodsId),
            displayQuantity: quantity,
            quantity,
            unitPrice: toNumber(item.unitPrice),
            totalAmount: toNumber(item.totalAmount),
            totalAmountEdited: item.totalAmountEdited === true
          }
        })
      this.recalculate()
    },
    handleItemChange(item) {
      item.displayQuantity = Math.max(0, roundQuantity(item.displayQuantity, 0))
      item.unitPrice = toNumber(item.unitPrice)
      item.totalAmountEdited = false
      this.recalculate()
    },
    handleItemTotalChange(item) {
      const quantity = Math.abs(roundQuantity(
        item.displayQuantity !== undefined ? item.displayQuantity : item.quantity,
        0
      ))
      const amount = Math.abs(toNumber(item.totalAmount))
      const sign = this.form.orderType === 2 ? -1 : 1
      item.displayQuantity = quantity
      item.quantity = quantity
      item.totalAmount = quantity > 0 ? this.roundMoney(amount * sign) : 0
      item.unitPrice = quantity > 0 ? this.roundMoney(amount / quantity) : 0
      item.totalAmountEdited = true
      this.recalculate(item)
    },
    removeItem(index) {
      this.form.items.splice(index, 1)
      this.recalculate()
    },
    recalculate(changedTotalItem = null) {
      const sign = this.form.orderType === 2 ? -1 : 1
      let total = 0
      this.form.items.forEach((item) => {
        const quantity = Math.max(0, roundQuantity(
          item.displayQuantity !== undefined ? item.displayQuantity : item.quantity
        ))
        item.quantity = quantity
        if (item !== changedTotalItem && !item.totalAmountEdited) {
          item.totalAmount = this.roundMoney(quantity * toNumber(item.unitPrice) * sign)
        }
        total += toNumber(item.totalAmount)
      })
      this.form.totalAmount = this.roundMoney(total)
      const rate = Math.max(0, toNumber(this.form.discountRate, 100))
      this.form.discountedAmount = this.roundMoney(total * rate / 100)
      this.form.payableAmount = this.roundMoney(this.form.discountedAmount + toNumber(this.form.freightAmount))
      this.form.paidAmount = this.roundMoney(toNumber(this.form.paidAmount))
      this.form.unpaidAmount = this.roundMoney(this.form.payableAmount - this.form.paidAmount)
    },
    handleDiscountRateChange() {
      this.form.discountRate = Math.min(100, Math.max(0, toNumber(this.form.discountRate, 100)))
      this.recalculate()
    },
    normalizeFreightAmount() {
      this.form.freightAmount = this.roundMoney(toNumber(this.form.freightAmount))
      this.recalculate()
    },
    normalizeMoneyFields() {
      this.form.paidAmount = this.roundMoney(toNumber(this.form.paidAmount))
      this.recalculate()
    },
    fillPaidAmount() {
      this.form.paidAmount = this.roundMoney(this.form.payableAmount)
      this.recalculate()
    },
    buildPayload() {
      this.recalculate()
      return {
        id: this.form.id,
        orderNo: this.form.orderNo,
        supplierId: this.form.supplierId,
        orderType: this.form.orderType,
        settleId: this.form.settleId,
        cashierId: this.form.cashierId,
        cashierName: this.form.cashierName,
        status: this.form.status,
        totalAmount: this.form.totalAmount,
        payableAmount: this.form.payableAmount,
        paidAmount: this.form.paidAmount,
        unpaidAmount: this.form.unpaidAmount,
        discountedAmount: this.form.discountedAmount,
        discountRate: this.form.discountRate,
        freightAmount: this.form.freightAmount,
        note: this.form.note,
        createTime: this.form.createTime,
        items: this.form.items.map((item) => ({
          id: item.id,
          goodsId: String(item.goodsId),
          goodsName: item.goodsName,
          goodsCode: item.goodsCode,
          categoryId: item.categoryId === undefined || item.categoryId === null ? '' : String(item.categoryId),
          unit: item.unit || '',
          quantity: this.form.orderType === 2
            ? -Math.abs(roundQuantity(item.displayQuantity))
            : Math.abs(roundQuantity(item.displayQuantity)),
          unitPrice: toNumber(item.unitPrice),
          totalAmount: this.form.orderType === 2 ? -Math.abs(toNumber(item.totalAmount)) : Math.abs(toNumber(item.totalAmount)),
          note: item.note || ''
        }))
      }
    },
    hasItemWithoutCategory() {
      return this.form.items.some((item) => {
        const categoryId = item && item.categoryId
        return categoryId === undefined || categoryId === null || categoryId === '' || Number(categoryId) <= 0
      })
    },
    async saveForm() {
      if (this.saving) return
      if (!this.form.supplierId) {
        uni.showToast({ title: '请选择供应商', icon: 'none' })
        return
      }
      if (!this.form.items.length) {
        uni.showToast({ title: '请选择货品', icon: 'none' })
        return
      }
      if (this.hasItemWithoutCategory()) {
        uni.showToast({ title: '所有商品必须选择分类', icon: 'none' })
        return
      }
      if (!this.form.settleId) {
        uni.showToast({ title: '请选择结算账户', icon: 'none' })
        return
      }
      this.saving = true
      uni.showLoading({ title: '保存中...', mask: true })
      try {
        const payload = { ...this.buildPayload(), userId: this.user.id }
        if (this.form.id) await editPurchaseOrder(payload)
        else await addPurchaseOrder(payload)
        uni.showToast({ title: '保存成功', icon: 'success' })
        await this.resetForm(false)
      } catch (error) {
        uni.showToast({ title: error && error.message ? error.message : '保存失败', icon: 'none' })
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
        await removePurchaseOrder({ id: this.form.id, userId: this.user.id })
        await this.resetForm(false)
        uni.showToast({ title: '删除成功', icon: 'success' })
        setTimeout(() => uni.navigateTo({ url: '/pages/purchase-order/list' }), 350)
      } catch (error) {
        uni.showToast({ title: error && error.message ? error.message : '删除失败', icon: 'none' })
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
            content: '将清空当前进货单内容，是否继续？',
            success: resolve
          })
        })
        if (!result.confirm) return
      }
      const defaultForm = createEmptyForm()
      defaultForm.cashierId = this.form.cashierId || String(this.user.id || '')
      defaultForm.cashierName = this.form.cashierName || this.user.realName || this.user.userName || ''
      defaultForm.settleId = this.form.settleId || (this.settleOptions[0] && this.settleOptions[0].value) || ''
      this.form = defaultForm
      this.orderTypeIndex = 0
      this.statusIndex = 1
      this.syncCashierIndex()
      this.syncStatusIndex()
      this.syncSettleIndex()
      await this.generateOrderNo()
    },
    syncCashierIndex() {
      const index = this.cashierOptions.findIndex((item) => String(item.value) === String(this.form.cashierId))
      this.cashierIndex = index >= 0 ? index : 0
    },
    syncStatusIndex() {
      const index = this.statusOptions.findIndex((item) => Number(item.value) === Number(this.form.status))
      this.statusIndex = index >= 0 ? index : 1
    },
    syncSettleIndex() {
      const index = this.settleOptions.findIndex((item) => String(item.value) === String(this.form.settleId))
      this.settleIndex = index >= 0 ? index : 0
    },
    confirmReset() { this.resetForm(true) },
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    },
    formatQuantity(value) {
      const number = toNumber(value)
      return Number.isInteger(number) ? String(number) : number.toFixed(2)
    },
    formatAmount(value) { return `¥${toNumber(value).toFixed(2)}` },
    roundMoney(value) { return Math.round(toNumber(value) * 100) / 100 }
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
.choose-goods-button { display: flex; align-items: center; justify-content: center; gap: 8rpx; width: 100%; height: 78rpx; margin: 18rpx 0 0; padding: 0; color: #fff; font-size: 27rpx; line-height: 78rpx; background: #722ed1; border-radius: 10rpx; }
.choose-goods-button::after, .form-actions button::after { border: 0; }
.goods-empty { margin-top: 16rpx; padding: 30rpx 0; color: #aaa5b5; font-size: 23rpx; text-align: center; background: #faf9fc; border-radius: 10rpx; }
.goods-item-list { margin-top: 16rpx; }
.goods-item-card { margin-bottom: 12rpx; padding: 16rpx; background: #faf9fc; border: 1rpx solid #eeeaf4; border-radius: 12rpx; }
.goods-item-header { display: flex; align-items: center; gap: 12rpx; }
.goods-item-index { display: flex; align-items: center; justify-content: center; flex: 0 0 42rpx; width: 42rpx; height: 42rpx; color: #fff; font-size: 20rpx; background: #75aef5; border-radius: 8rpx; }
.goods-item-main { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.goods-item-name { overflow: hidden; color: #30283d; font-size: 26rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.goods-item-meta { margin-top: 6rpx; color: #9a95a4; font-size: 20rpx; }
.goods-item-controls { display: flex; align-items: flex-end; gap: 12rpx; margin-top: 16rpx; padding-top: 14rpx; border-top: 1rpx solid #eeeaf4; }
.number-control, .goods-total { flex: 1; min-width: 0; }
.control-label { display: block; color: #918b99; font-size: 20rpx; }
.number-input, .text-input { width: 100%; height: 58rpx; margin-top: 6rpx; padding: 0 12rpx; box-sizing: border-box; color: #393044; font-size: 24rpx; text-align: right; background: #fff; border: 1rpx solid #ded9e8; border-radius: 8rpx; }
.goods-total { text-align: right; }
.goods-total-value { display: block; margin-top: 9rpx; color: #e5484d; font-size: 25rpx; font-weight: 600; }
.goods-item-note { margin-top: 16rpx; padding-top: 14rpx; border-top: 1rpx solid #eeeaf4; }
.goods-note-input { width: 100%; height: 58rpx; margin-top: 6rpx; padding: 0 12rpx; box-sizing: border-box; color: #393044; font-size: 24rpx; background: #fff; border: 1rpx solid #ded9e8; border-radius: 8rpx; }
.inline-input { display: flex; align-items: center; flex: 1; min-width: 0; gap: 10rpx; }
.input-suffix { flex: 0 0 auto; color: #6f6979; font-size: 24rpx; }
.paid-state { flex: 0 0 auto; min-width: 76rpx; padding: 7rpx 12rpx; color: #2db7b2; font-size: 20rpx; text-align: center; border: 2rpx solid #5ed4d0; border-radius: 24rpx; }
.note-row { align-items: flex-start; padding-top: 8rpx; }
.note-input { flex: 1; min-height: 74rpx; padding: 12rpx; box-sizing: border-box; color: #393044; font-size: 24rpx; background: #faf9fc; border: 1rpx solid #ded9e8; border-radius: 8rpx; }
.summary-section { padding: 18rpx 20rpx; }
.summary-row { display: flex; align-items: center; justify-content: space-between; min-height: 48rpx; color: #4d4858; font-size: 23rpx; }
.summary-row.secondary { color: #918b99; font-size: 21rpx; }
.summary-amount, .unpaid-amount { color: #e5484d; font-size: 28rpx; font-weight: 600; }
.form-actions { display: flex; gap: 12rpx; margin-top: 4rpx; }
.delete-actions { margin-top: 22rpx; }
.form-actions button { flex: 1; height: 76rpx; margin: 0; padding: 0 12rpx; font-size: 25rpx; line-height: 76rpx; border-radius: 10rpx; }
.delete-button { display: flex; align-items: center; justify-content: center; gap: 6rpx; color: #fff; background: #ff5e5e; }
.reset-button { color: #666; background: #ffffff; }
.save-button { color: #fff; background: #722ed1; }
.form-actions button[disabled] { opacity: .6; }
</style>
