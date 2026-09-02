<template>
  <scroll-view class="form-scroll" scroll-y :show-scrollbar="false">
    <view class="form-content">
      <view class="form-section">
        <view class="section-header">
          <text class="section-title">{{ isEdit ? '编辑盘点单' : '新增盘点单' }}</text>
          <text class="section-count">{{ form.items.length }} 种商品</text>
        </view>
        <view class="field-row">
          <text class="field-label">单据日期</text>
          <picker mode="date" :value="formDate" @change="onDateChange" style="flex: 1;">
            <view class="picker-value">
              <text>{{ formDate }}</text>
              <uni-icons type="right" color="#999" :size="18" />
            </view>
          </picker>
        </view>
        <view class="field-row">
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
            style="flex: 1;"
          >
            <view class="picker-value">
              <text>{{ selectedCashierName || '请选择营业员' }}</text>
              <uni-icons type="right" color="#999" :size="18" />
            </view>
          </picker>
          <text v-else class="field-value">{{ form.cashierName || '-' }}</text>
        </view>
      </view>

      <view class="form-section">
        <view class="section-header">
          <text class="section-title">盘点商品</text>
          <text class="section-count">{{ form.items.length }} 种</text>
        </view>
        <view v-if="form.items.length" class="goods-list">
          <view v-for="(item, index) in form.items" :key="item.key || index" class="goods-card">
            <view class="goods-header">
              <view class="goods-index">{{ index + 1 }}</view>
              <view class="goods-main">
                <text class="goods-name">{{ item.goodsName || item.goodsId || '-' }}</text>
                <text class="goods-meta">
                  单位：{{ item.unit || '-' }}　账存：{{ formatQuantity(item.bookQuantity) }}
                </text>
              </view>
              <uni-icons type="trash" color="#e5484d" :size="20" @click="removeItem(index)" />
            </view>
            <view class="goods-fields">
              <view class="number-field">
                <text class="control-label">实盘数量</text>
                <input
                  v-model="item.actualQuantity"
                  class="number-input"
                  type="digit"
                  @blur="handleItemChange(item)"
                />
              </view>
              <view class="number-field">
                <text class="control-label">盈亏数量</text>
                <text class="calculated-value" :class="valueClass(item.profitLossQuantity)">
                  {{ formatQuantity(item.profitLossQuantity) }}
                </text>
              </view>
              <view class="number-field">
                <text class="control-label">盈亏金额</text>
                <text class="calculated-value" :class="valueClass(item.profitLossAmount)">
                  ¥ {{ formatAmount(item.profitLossAmount) }}
                </text>
              </view>
            </view>
            <view class="cost-row">
              <text>成本单价：¥ {{ formatAmount(item.unitPrice) }}</text>
              <input
                v-model="item.note"
                class="note-input"
                type="text"
                maxlength="100"
                placeholder="备注"
              />
            </view>
          </view>
        </view>
        <view v-else class="goods-empty">请先选择盘点商品</view>
        <button class="choose-goods-button" :disabled="saving" @click="openGoodsSelect">
          <uni-icons type="plusempty" color="#fff" :size="21" />
          <text>选择商品</text>
        </button>
      </view>

      <view class="form-section">
        <view class="field-row note-row">
          <text class="field-label">说明</text>
          <textarea
            v-model="form.note"
            class="note-textarea"
            maxlength="100"
            placeholder="请输入说明"
          />
        </view>
      </view>

      <view class="summary-section">
        <view class="summary-row">
          <text>盈亏数量</text>
          <text class="summary-value" :class="valueClass(totalQuantity)">
            {{ formatQuantity(totalQuantity) }}
          </text>
        </view>
        <view class="summary-row">
          <text>盈亏金额</text>
          <text class="summary-value" :class="valueClass(totalAmount)">
            ¥ {{ formatAmount(totalAmount) }}
          </text>
        </view>
      </view>

      <view class="form-actions">
        <button class="reset-button" :disabled="saving" @click="confirmReset">重置</button>
        <button class="save-button" :disabled="saving" @click="saveForm">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </view>
      <button
        v-if="isEdit"
        class="delete-button"
        :disabled="saving || deleting"
        @click="confirmDelete"
      >
        <uni-icons type="trash" color="#fff" :size="20" />
        <text>{{ deleting ? '删除中...' : '删除盘点单' }}</text>
      </button>
      <view class="bottom-space" />
    </view>
  </scroll-view>
</template>

<script>
import {
  addStockCheck,
  createStockCheckNo,
  editStockCheck,
  getUserInfo,
  listAppUsers,
  removeStockCheck
} from '../../common/api'
import { clearSession, getUser, isLoggedIn, updateUser } from '../../common/auth'

const today = () => {
  const date = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const emptyForm = () => ({
  id: '',
  orderNo: '',
  cashierId: '',
  cashierName: '',
  profitLossQuantity: 0,
  profitLossAmount: 0,
  note: '',
  createTime: new Date(),
  items: []
})

const toNumber = (value, fallback = 0) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

const roundQuantity = (value) => Math.round(toNumber(value) * 100) / 100
const roundAmount = (value) => Math.round(toNumber(value) * 100) / 100

export default {
  data() {
    return {
      user: getUser() || {},
      form: emptyForm(),
      originalForm: emptyForm(),
      cashierOptions: [],
      cashierIndex: 0,
      initialized: false,
      loading: false,
      saving: false,
      deleting: false,
      channel: null,
      storageKey: ''
    }
  },
  computed: {
    isRoot() {
      return Number(this.user.isRoot) === 1
    },
    isEdit() {
      return Boolean(this.form.id)
    },
    formDate() {
      const value = this.form.createTime
      if (!value) return today()
      const text = String(value)
      if (/^\d{4}-\d{2}-\d{2}/.test(text)) return text.slice(0, 10)
      const date = new Date(value)
      return Number.isNaN(date.getTime()) ? today() : date.toISOString().slice(0, 10)
    },
    selectedCashierName() {
      const option = this.cashierOptions[this.cashierIndex]
      return this.form.cashierName || (option && option.label) || ''
    },
    totalQuantity() {
      return this.form.items.reduce(
        (total, item) => total + toNumber(item.profitLossQuantity),
        0
      )
    },
    totalAmount() {
      return this.form.items.reduce(
        (total, item) => total + toNumber(item.profitLossAmount),
        0
      )
    }
  },
  onLoad(options) {
    this.channel = this.getOpenerEventChannel ? this.getOpenerEventChannel() : null
    this.storageKey = options && options.storageKey
      ? decodeURIComponent(String(options.storageKey))
      : ''
    this.loadForm(options && options.id ? String(options.id) : '')
  },
  methods: {
    async loadForm(id) {
      if (!isLoggedIn()) {
        this.redirectToLogin()
        return
      }
      this.loading = true
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...currentUser }
        updateUser(this.user)
        await this.loadCashiers()
        const cached = this.storageKey ? uni.getStorageSync(this.storageKey) : null
        if (this.storageKey) uni.removeStorageSync(this.storageKey)
        if (id && cached && String(cached.id) === id) {
          this.applyRecord(cached)
        } else {
          this.form = emptyForm()
          this.form.cashierId = String(this.user.id || '')
          this.form.cashierName = this.user.realName || this.user.userName || ''
          this.form.createTime = new Date()
          this.form.orderNo = await createStockCheckNo()
        }
        this.initialized = true
        this.originalForm = JSON.parse(JSON.stringify(this.normalizeForm(this.form)))
        this.setCashierIndex()
      } catch (error) {
        this.handleError(error, '盘点单加载失败')
      } finally {
        this.loading = false
      }
    },
    async loadCashiers() {
      if (!this.isRoot) return
      const users = await listAppUsers()
      this.cashierOptions = (Array.isArray(users) ? users : [])
        .filter((item) => Number(item.status) === 1)
        .map((item) => ({
          label: item.realName || item.userName || `员工${item.id}`,
          value: String(item.id)
        }))
      if (!this.cashierOptions.length) {
        this.cashierOptions = [{
          label: this.user.realName || this.user.userName || '当前营业员',
          value: String(this.user.id || '')
        }]
      }
    },
    applyRecord(record) {
      const form = emptyForm()
      Object.assign(form, record || {})
      form.id = String(record.id)
      form.cashierId = String(record.cashierId || '')
      form.cashierName = record.cashierName || record.cashierId_dictText || ''
      form.note = record.note || ''
      form.createTime = record.createTime || new Date()
      form.items = (Array.isArray(record.items) ? record.items : []).map((item, index) => {
        const bookQuantity = roundQuantity(item.bookQuantity)
        const actualQuantity = roundQuantity(item.actualQuantity)
        const unitPrice = toNumber(item.unitPrice)
        return {
          ...item,
          key: `${item.id || item.goodsId || index}-${Date.now()}`,
          goodsId: String(item.goodsId || ''),
          goodsName: item.goodsName || item.goodsId_dictText || '',
          bookQuantity,
          actualQuantity,
          unitPrice,
          profitLossQuantity: roundQuantity(
            item.profitLossQuantity !== undefined
              ? item.profitLossQuantity
              : actualQuantity - bookQuantity
          ),
          profitLossAmount: roundAmount(
            item.profitLossAmount !== undefined
              ? item.profitLossAmount
              : (actualQuantity - bookQuantity) * unitPrice
          ),
          note: item.note || ''
        }
      })
      this.form = form
    },
    normalizeForm(form) {
      return {
        ...form,
        id: form.id || '',
        items: (Array.isArray(form.items) ? form.items : []).map((item) => ({ ...item }))
      }
    },
    onDateChange(event) {
      const value = event && event.detail ? String(event.detail.value || '') : ''
      if (value) this.form.createTime = new Date(`${value}T00:00:00`)
    },
    onCashierChange(event) {
      this.cashierIndex = Number(event && event.detail ? event.detail.value || 0 : 0)
      const option = this.cashierOptions[this.cashierIndex]
      if (option) {
        this.form.cashierId = String(option.value)
        this.form.cashierName = option.label
      }
    },
    setCashierIndex() {
      const index = this.cashierOptions.findIndex(
        (item) => String(item.value) === String(this.form.cashierId)
      )
      this.cashierIndex = index >= 0 ? index : 0
    },
    openGoodsSelect() {
      const storageKey = `easy-store-stock-check-selection-${Date.now()}`
      uni.setStorageSync(storageKey, this.form.items)
      uni.navigateTo({
        url: `/pages/goods-select/index?type=stockCheck&storageKey=${encodeURIComponent(storageKey)}`,
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
          const bookQuantity = roundQuantity(
            item.bookQuantity !== undefined ? item.bookQuantity : item.stock
          )
          const actualQuantity = roundQuantity(
            item.actualQuantity !== undefined ? item.actualQuantity : bookQuantity
          )
          const unitPrice = toNumber(
            item.unitPrice !== undefined ? item.unitPrice : item.costPrice
          )
          const result = {
            ...item,
            key: item.key || `${item.goodsId}-${Date.now()}-${index}`,
            goodsId: String(item.goodsId),
            goodsName: item.goodsName || item.title || item.goodsId_dictText || String(item.goodsId),
            unit: item.unit || '',
            bookQuantity,
            actualQuantity,
            unitPrice,
            note: item.note || ''
          }
          this.updateItem(result)
          return result
        })
    },
    handleItemChange(item) {
      item.actualQuantity = Math.max(0, roundQuantity(item.actualQuantity))
      this.updateItem(item)
    },
    updateItem(item) {
      item.bookQuantity = roundQuantity(item.bookQuantity)
      item.actualQuantity = roundQuantity(item.actualQuantity)
      item.unitPrice = toNumber(item.unitPrice)
      item.profitLossQuantity = roundQuantity(
        item.actualQuantity - item.bookQuantity
      )
      item.profitLossAmount = roundAmount(
        item.profitLossQuantity * item.unitPrice
      )
    },
    removeItem(index) {
      this.form.items.splice(index, 1)
    },
    buildPayload() {
      return {
        id: this.form.id || undefined,
        orderNo: this.form.orderNo,
        cashierId: this.form.cashierId,
        cashierName: this.form.cashierName,
        profitLossQuantity: roundQuantity(this.totalQuantity),
        profitLossAmount: roundAmount(this.totalAmount),
        note: this.form.note || '',
        createTime: this.form.createTime,
        items: this.form.items.map((item) => ({
          id: item.id,
          goodsId: String(item.goodsId),
          goodsName: item.goodsName,
          unit: item.unit || '',
          bookQuantity: roundQuantity(item.bookQuantity),
          actualQuantity: roundQuantity(item.actualQuantity),
          profitLossQuantity: roundQuantity(item.profitLossQuantity),
          unitPrice: toNumber(item.unitPrice),
          profitLossAmount: roundAmount(item.profitLossAmount),
          note: item.note || ''
        })),
        userId: this.user.id
      }
    },
    async saveForm() {
      if (this.saving || !this.initialized) return
      if (!this.form.items.length) {
        uni.showToast({ title: '请至少选择一项盘点商品', icon: 'none' })
        return
      }
      this.saving = true
      uni.showLoading({ title: '保存中...', mask: true })
      try {
        const payload = this.buildPayload()
        if (this.isEdit) await editStockCheck(payload)
        else await addStockCheck(payload)
        if (this.channel && this.channel.emit) {
          this.channel.emit('saved', { id: this.form.id })
        }
        uni.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 350)
      } catch (error) {
        this.handleError(error, '保存失败')
      } finally {
        uni.hideLoading()
        this.saving = false
      }
    },
    confirmReset() {
      uni.showModal({
        title: '确认重置',
        content: '将清空当前已填写内容，是否继续？',
        success: (result) => {
          if (result.confirm) this.resetForm()
        }
      })
    },
    resetForm() {
      if (this.isEdit) {
        this.form = JSON.parse(JSON.stringify(this.originalForm))
        this.setCashierIndex()
        return
      }
      this.form = emptyForm()
      this.form.cashierId = String(this.user.id || '')
      this.form.cashierName = this.user.realName || this.user.userName || ''
      this.form.createTime = new Date()
      createStockCheckNo().then((value) => { this.form.orderNo = value })
      this.setCashierIndex()
    },
    confirmDelete() {
      if (!this.isEdit || this.deleting) return
      uni.showModal({
        title: '确认删除',
        content: '删除后将无法恢复，是否继续？',
        confirmText: '删除',
        confirmColor: '#e5484d',
        success: (result) => {
          if (result.confirm) this.deleteForm()
        }
      })
    },
    async deleteForm() {
      if (this.deleting || !this.form.id) return
      this.deleting = true
      uni.showLoading({ title: '删除中...', mask: true })
      try {
        await removeStockCheck({ id: this.form.id, userId: this.user.id })
        if (this.channel && this.channel.emit) {
          this.channel.emit('saved', { id: this.form.id, deleted: true })
        }
        uni.showToast({ title: '删除成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 350)
      } catch (error) {
        this.handleError(error, '删除失败')
      } finally {
        uni.hideLoading()
        this.deleting = false
      }
    },
    formatQuantity(value) {
      return roundQuantity(value).toFixed(2)
    },
    formatAmount(value) {
      return toNumber(value).toFixed(2)
    },
    valueClass(value) {
      return Number(value || 0) < 0 ? 'negative' : 'positive'
    },
    handleError(error, fallback) {
      const message = error && error.message ? error.message : fallback
      if (/登录|token|过期/i.test(message)) this.redirectToLogin()
      else uni.showToast({ title: message, icon: 'none' })
    },
    redirectToLogin() {
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
.form-scroll { width: 100%; height: 100%; box-sizing: border-box; }
.form-content { min-height: 100%; padding: 18rpx 20rpx calc(36rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.form-section, .summary-section { margin-bottom: 16rpx; padding: 20rpx; background: #fff; border: 1rpx solid #ece9f2; border-radius: 16rpx; box-shadow: 0 5rpx 16rpx rgba(67, 47, 119, .05); }
.section-header { display: flex; align-items: center; justify-content: space-between; }
.section-title { color: #454252; font-size: 29rpx; font-weight: 600; }
.section-count { color: #722ed1; font-size: 21rpx; }
.field-row { display: flex; align-items: center; min-height: 76rpx; gap: 18rpx; padding: 10rpx 0; border-bottom: 1rpx solid #f0edf5; }
.field-row:last-child { border-bottom: 0; }
.field-label { flex: 0 0 126rpx; color: #5f596b; font-size: 24rpx; }
.field-value, .picker-value { flex: 1; min-width: 0; color: #393044; font-size: 25rpx; }
.picker-value { display: flex; align-items: center; justify-content: space-between; min-height: 72rpx; }
.picker-value text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.goods-list { margin-top: 16rpx; }
.goods-card { margin-bottom: 12rpx; padding: 16rpx; background: #faf9fc; border: 1rpx solid #eeeaf4; border-radius: 12rpx; }
.goods-header { display: flex; align-items: center; gap: 12rpx; }
.goods-index { display: flex; align-items: center; justify-content: center; flex: 0 0 42rpx; width: 42rpx; height: 42rpx; color: #fff; font-size: 20rpx; background: #75aef5; border-radius: 8rpx; }
.goods-main { flex: 1; min-width: 0; }
.goods-name { display: block; overflow: hidden; color: #30283d; font-size: 26rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.goods-meta { display: block; margin-top: 6rpx; color: #9a95a4; font-size: 20rpx; }
.goods-fields { display: flex; gap: 12rpx; margin-top: 16rpx; padding-top: 14rpx; border-top: 1rpx solid #eeeaf4; }
.number-field { flex: 1; min-width: 0; }
.control-label { display: block; color: #918b99; font-size: 20rpx; }
.number-input { width: 100%; height: 58rpx; margin-top: 6rpx; padding: 0 12rpx; box-sizing: border-box; color: #393044; font-size: 24rpx; text-align: right; background: #fff; border: 1rpx solid #ded9e8; border-radius: 8rpx; }
.calculated-value { display: block; margin-top: 20rpx; overflow: hidden; font-size: 23rpx; font-weight: 600; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
.positive { color: #00a870; }
.negative { color: #e5484d; }
.cost-row { display: flex; align-items: center; gap: 12rpx; margin-top: 16rpx; padding-top: 14rpx; color: #8b8594; font-size: 20rpx; border-top: 1rpx solid #eeeaf4; }
.cost-row text { flex: 0 0 auto; }
.note-input { flex: 1; min-width: 0; height: 52rpx; padding: 0 10rpx; box-sizing: border-box; color: #393044; font-size: 22rpx; background: #fff; border: 1rpx solid #ded9e8; border-radius: 8rpx; }
.goods-empty { margin-top: 16rpx; padding: 30rpx 0; color: #aaa5b5; font-size: 23rpx; text-align: center; background: #faf9fc; border-radius: 10rpx; }
.choose-goods-button { display: flex; align-items: center; justify-content: center; gap: 8rpx; width: 100%; height: 78rpx; margin: 18rpx 0 0; padding: 0; color: #fff; font-size: 27rpx; line-height: 78rpx; background: #722ed1; border-radius: 10rpx; }
.choose-goods-button::after, .form-actions button::after, .delete-button::after { border: 0; }
.note-row { align-items: flex-start; border-bottom: 0; }
.note-textarea { flex: 1; min-height: 82rpx; padding: 12rpx; box-sizing: border-box; color: #393044; font-size: 24rpx; background: #faf9fc; border: 1rpx solid #ded9e8; border-radius: 8rpx; }
.summary-section { padding: 18rpx 20rpx; }
.summary-row { display: flex; align-items: center; justify-content: space-between; min-height: 52rpx; color: #4d4858; font-size: 23rpx; }
.summary-value { font-size: 27rpx; font-weight: 600; }
.form-actions { display: flex; gap: 18rpx; margin-top: 4rpx; }
.form-actions button { flex: 1; height: 78rpx; margin: 0; font-size: 27rpx; line-height: 78rpx; border-radius: 10rpx; }
.reset-button { color: #666; background: #f2f3f5; }
.save-button { color: #fff; background: #722ed1; }
.delete-button { display: flex; align-items: center; justify-content: center; gap: 8rpx; width: 100%; height: 78rpx; margin: 20rpx 0 0; padding: 0; color: #fff; font-size: 27rpx; line-height: 78rpx; background: #e5484d; border-radius: 10rpx; }
.form-actions button[disabled], .delete-button[disabled] { opacity: .6; }
.bottom-space { height: 30rpx; }
</style>
