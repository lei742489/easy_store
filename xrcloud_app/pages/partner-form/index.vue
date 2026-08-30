<template>
  <view class="page">
    <view class="form-card">
      <view class="page-header">
        <text class="page-title">{{ pageTitle }}</text>
        <text class="page-subtitle">{{ pageSubtitle }}</text>
      </view>

      <uni-forms
        ref="formRef"
        :model-value="form"
        :rules="rules"
        label-position="top"
        label-width="auto"
        validate-trigger="submit"
      >
        <view class="section-title">基础信息</view>
        <uni-forms-item name="name" :label="nameLabel" required>
          <uni-easyinput
            v-model="form.name"
            :input-border="true"
            :placeholder="namePlaceholder"
          />
        </uni-forms-item>

        <view class="form-row">
          <view class="form-col">
            <uni-forms-item name="contactName" label="联系人">
              <uni-easyinput v-model="form.contactName" :input-border="true" placeholder="请输入联系人" />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="mobile" label="手机">
              <uni-easyinput v-model="form.mobile" :input-border="true" placeholder="请输入手机号" />
            </uni-forms-item>
          </view>
        </view>

        <view class="form-row">
          <view class="form-col">
            <uni-forms-item name="phone" label="电话">
              <uni-easyinput v-model="form.phone" :input-border="true" placeholder="请输入电话" />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="mail" label="邮箱">
              <uni-easyinput v-model="form.mail" :input-border="true" placeholder="请输入邮箱" />
            </uni-forms-item>
          </view>
        </view>

        <view class="form-row">
          <view v-if="isCustomer" class="form-col">
            <uni-forms-item name="birthday" label="生日">
              <view class="date-picker-wrap">
                <uni-datetime-picker v-model="form.birthday" type="date" :clear-icon="true" />
              </view>
            </uni-forms-item>
          </view>
          <view v-else class="form-col">
            <uni-forms-item name="postal" label="邮编">
              <uni-easyinput v-model="form.postal" :input-border="true" placeholder="请输入邮编" />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="qq" label="QQ">
              <uni-easyinput v-model="form.qq" :input-border="true" placeholder="请输入QQ号" />
            </uni-forms-item>
          </view>
        </view>

        <uni-forms-item name="address" label="详细地址">
          <uni-easyinput
            v-model="form.address"
            type="textarea"
            :input-border="true"
            :auto-height="true"
            placeholder="请输入详细地址"
          />
        </uni-forms-item>

        <view v-if="isCustomer">
          <view class="section-title">客户信息</view>
          <view class="form-row">
            <view class="form-col">
              <uni-forms-item name="categoryId" label="客户分类">
                <picker
                  mode="selector"
                  :range="categoryOptions"
                  range-key="text"
                  :value="categoryPickerIndex"
                  @change="onCategoryChange"
                >
                  <view class="picker-field" :class="{ placeholder: !form.categoryId }">
                    <text>{{ categoryDisplayText }}</text>
                    <uni-icons type="down" color="#999" :size="16" />
                  </view>
                </picker>
              </uni-forms-item>
            </view>
            
          </view>

          <view class="form-row">
            <view class="form-col">
              <uni-forms-item name="discount" label="折扣">
                <uni-easyinput v-model="form.discount" type="number" :input-border="true" placeholder="请输入折扣" />
              </uni-forms-item>
            </view>
            <view class="form-col">
              <uni-forms-item name="status" label="状态">
                <picker
                  mode="selector"
                  :range="statusOptions"
                  range-key="text"
                  :value="statusPickerIndex"
                  @change="onStatusChange"
                >
                  <view class="picker-field" :class="{ placeholder: form.status === undefined }">
                    <text>{{ statusDisplayText }}</text>
                    <uni-icons type="down" color="#999" :size="16" />
                  </view>
                </picker>
              </uni-forms-item>
            </view>
          </view>
        </view>

        <view v-else>
          <view class="section-title">账务信息</view>
          <view class="form-row">
            <view class="form-col">
              <uni-forms-item name="defPayable" label="初期应付款">
                <uni-easyinput v-model="form.defPayable" type="number" :input-border="true" placeholder="请输入初期应付款" />
              </uni-forms-item>
            </view>
            <view class="form-col">
              <uni-forms-item name="payable" label="当前应付款">
                <uni-easyinput v-model="form.payable" type="number" :input-border="true" placeholder="请输入当前应付款" />
              </uni-forms-item>
            </view>
          </view>

          <uni-forms-item name="status" label="状态">
            <picker
              mode="selector"
              :range="statusOptions"
              range-key="text"
              :value="statusPickerIndex"
              @change="onStatusChange"
            >
              <view class="picker-field" :class="{ placeholder: form.status === undefined }">
                <text>{{ statusDisplayText }}</text>
                <uni-icons type="down" color="#999" :size="16" />
              </view>
            </picker>
          </uni-forms-item>
        </view>

        <view class="section-title">备注</view>
        <uni-forms-item name="note" label="备注">
          <uni-easyinput
            v-model="form.note"
            type="textarea"
            :input-border="true"
            :auto-height="true"
            placeholder="请输入备注"
          />
        </uni-forms-item>
      </uni-forms>

      <view class="form-actions">
        <button class="secondary-button" :disabled="saving" @click="confirmReset">重置</button>
        <button class="primary-button" :disabled="saving" @click="submitForm">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </view>

      <button
        v-if="isEdit"
        class="delete-button"
        :disabled="saving || deleting"
        @click="confirmDelete"
      >
        {{ deleting ? '删除中...' : deleteButtonText }}
      </button>
    </view>
  </view>
</template>

<script>
import { clearSession, isLoggedIn } from '../../common/auth'
import {
  addCustomer,
  addSupplier,
  editCustomer,
  editSupplier,
  listCustomerCategories,
  listCustomerLevels,
  listCustomerPage,
  listSupplierPage,
  removeCustomer,
  removeSupplier
} from '../../common/api'

const storageKeys = {
  customer: 'easy-store-edit-customer',
  supplier: 'easy-store-edit-supplier'
}

const emptyForm = () => ({
  id: '',
  name: '',
  contactName: '',
  mobile: '',
  phone: '',
  mail: '',
  postal: '',
  birthday: '',
  address: '',
  qq: '',
  note: '',
  categoryId: '',
  categoryId_dictText: '',
  levelId: '',
  levelId_dictText: '',
  discount: '',
  defPayable: '',
  payable: '',
  status: 1
})

const normalizeText = (value) => String(value === undefined || value === null ? '' : value).trim()

const toInputValue = (value) => {
  if (value === undefined || value === null || value === '') return ''
  return String(value)
}

const normalizeDateValue = (value) => {
  const text = normalizeText(value)
  if (!text) return ''
  return text.indexOf(' ') > -1 ? text.slice(0, 10) : text.slice(0, 10)
}

const flattenCategoryOptions = (items, depth = 0, target = []) => {
  ;(Array.isArray(items) ? items : []).forEach((item) => {
    if (!item || item.id === undefined || item.id === null) return
    if (Number(item.id) === 0) {
      flattenCategoryOptions(item.children, depth + 1, target)
      return
    }
    const label = item.title || '未命名分类'
    target.push({
      value: String(item.id),
      text: `${depth ? '  '.repeat(depth) : ''}${label}`
    })
    if (Array.isArray(item.children) && item.children.length) {
      flattenCategoryOptions(item.children, depth + 1, target)
    }
  })
  return target
}

const flattenLevelOptions = (items) => {
  return (Array.isArray(items) ? items : [])
    .filter((item) => item && item.id !== undefined && item.id !== null)
    .map((item) => ({
      value: String(item.id),
      text: item.title || '未命名等级'
    }))
}

export default {
  data() {
    return {
      mode: 'customer',
      partnerId: '',
      form: emptyForm(),
      originalForm: emptyForm(),
      initialized: false,
      saving: false,
      deleting: false,
      categoryOptions: [{ value: '', text: '请选择分类' }],
      levelOptions: [{ value: '', text: '请选择等级' }],
      statusOptions: [
        { value: 1, text: '启用' },
        { value: 0, text: '停用' }
      ],
      rules: {
        name: { rules: [{ required: true, errorMessage: '请输入名称' }] }
      },
      channel: null,
      prefillName: ''
    }
  },
  computed: {
    isCustomer() {
      return this.mode === 'customer'
    },
    isSupplier() {
      return this.mode === 'supplier'
    },
    isEdit() {
      return Boolean(this.partnerId)
    },
    pageTitle() {
      if (this.isCustomer) return this.isEdit ? '编辑客户' : '新增客户'
      return this.isEdit ? '编辑供应商' : '新增供应商'
    },
    pageSubtitle() {
      return this.isCustomer
        ? '完善客户基础信息、分类等级和账务信息'
        : '完善供应商基础信息和账务信息'
    },
    nameLabel() {
      return this.isCustomer ? '客户名称' : '供应商名称'
    },
    namePlaceholder() {
      return this.isCustomer ? '请输入客户名称' : '请输入供应商名称'
    },
    deleteButtonText() {
      return this.isCustomer ? '删除客户' : '删除供应商'
    },
    categoryPickerIndex() {
      const index = this.categoryOptions.findIndex((item) => String(item.value) === String(this.form.categoryId))
      return index >= 0 ? index : 0
    },
    levelPickerIndex() {
      const index = this.levelOptions.findIndex((item) => String(item.value) === String(this.form.levelId))
      return index >= 0 ? index : 0
    },
    statusPickerIndex() {
      const index = this.statusOptions.findIndex((item) => Number(item.value) === Number(this.form.status))
      return index >= 0 ? index : 0
    },
    categoryDisplayText() {
      return this.form.categoryId
        ? (this.categoryOptions[this.categoryPickerIndex] && this.categoryOptions[this.categoryPickerIndex].text) || '请选择分类'
        : '请选择分类'
    },
    levelDisplayText() {
      return this.form.levelId
        ? (this.levelOptions[this.levelPickerIndex] && this.levelOptions[this.levelPickerIndex].text) || '请选择等级'
        : '请选择等级'
    },
    statusDisplayText() {
      return (this.statusOptions[this.statusPickerIndex] && this.statusOptions[this.statusPickerIndex].text) || '请选择状态'
    }
  },
  onLoad(options) {
    this.mode = options && options.mode === 'supplier' ? 'supplier' : 'customer'
    this.partnerId = options && options.id ? String(options.id) : ''
    this.prefillName = normalizeText(options && (options.name || options.keyword || options.selectedName))
    this.channel = this.getOpenerEventChannel ? this.getOpenerEventChannel() : null
    this.setPageTitle()
    this.loadForm()
  },
  methods: {
    setPageTitle() {
      uni.setNavigationBarTitle({ title: this.pageTitle })
    },
    getListApi() {
      return this.isCustomer ? listCustomerPage : listSupplierPage
    },
    getAddApi() {
      return this.isCustomer ? addCustomer : addSupplier
    },
    getEditApi() {
      return this.isCustomer ? editCustomer : editSupplier
    },
    getRemoveApi() {
      return this.isCustomer ? removeCustomer : removeSupplier
    },
    getStorageKey() {
      return storageKeys[this.mode]
    },
    async loadForm() {
      if (!isLoggedIn()) {
        this.redirectToLogin()
        return
      }

      try {
        this.form = emptyForm()
        if (this.prefillName && !this.isEdit) {
          this.form.name = this.prefillName
        }

        if (this.isEdit) {
          const cached = uni.getStorageSync(this.getStorageKey())
          if (cached && String(cached.id) === this.partnerId) {
            this.form = this.normalizeForm(cached)
          } else {
            await this.loadRecordById()
          }
        }

        await this.loadLookups()
        this.originalForm = JSON.parse(JSON.stringify(this.form))
        this.initialized = true
      } catch (error) {
        uni.showToast({
          title: error && error.message ? error.message : '表单加载失败',
          icon: 'none'
        })
      }
    },
    async loadRecordById() {
      const page = await this.getListApi()({
        current: 1,
        pageSize: 1,
        id: this.partnerId
      })
      const record = page && Array.isArray(page.records) ? page.records[0] : null
      if (!record || String(record.id) !== this.partnerId) {
        throw new Error('未找到需要编辑的数据')
      }
      this.form = this.normalizeForm(record)
    },
    async loadLookups() {
      if (!this.isCustomer) {
        this.categoryOptions = [{ value: '', text: '请选择分类' }]
        this.levelOptions = [{ value: '', text: '请选择等级' }]
        return
      }

      const [categories, levels] = await Promise.all([listCustomerCategories(), listCustomerLevels()])
      const categoryOptions = flattenCategoryOptions(categories)
      const levelOptions = flattenLevelOptions(levels)
      this.categoryOptions = [{ value: '', text: '请选择分类' }, ...categoryOptions]
      this.levelOptions = [{ value: '', text: '请选择等级' }, ...levelOptions]
    },
    normalizeForm(record) {
      const source = record || {}
      const result = { ...emptyForm(), ...source }
      result.id = toInputValue(source.id)
      result.name = toInputValue(source.name)
      result.contactName = toInputValue(source.contactName)
      result.mobile = toInputValue(source.mobile)
      result.phone = toInputValue(source.phone)
      result.mail = toInputValue(source.mail)
      result.postal = toInputValue(source.postal)
      result.birthday = normalizeDateValue(source.birthday)
      result.address = toInputValue(source.address)
      result.qq = toInputValue(source.qq)
      result.note = toInputValue(source.note)
      result.categoryId = toInputValue(source.categoryId)
      result.categoryId_dictText = toInputValue(source.categoryId_dictText)
      result.levelId = toInputValue(source.levelId)
      result.levelId_dictText = toInputValue(source.levelId_dictText)
      result.discount = toInputValue(source.discount)
      result.defPayable = toInputValue(source.defPayable)
      result.payable = toInputValue(source.payable)
      result.status = Number(source.status === undefined || source.status === null ? 1 : source.status)
      return result
    },
    toNumberOrEmpty(value) {
      const text = normalizeText(value)
      if (!text) return ''
      const numberValue = Number(text)
      return Number.isFinite(numberValue) ? numberValue : ''
    },
    onCategoryChange(event) {
      const index = Number(event.detail.value || 0)
      const item = this.categoryOptions[index]
      this.form.categoryId = item ? String(item.value) : ''
      this.form.categoryId_dictText = item ? String(item.text || '') : ''
    },
    onLevelChange(event) {
      const index = Number(event.detail.value || 0)
      const item = this.levelOptions[index]
      this.form.levelId = item ? String(item.value) : ''
      this.form.levelId_dictText = item ? String(item.text || '') : ''
    },
    onStatusChange(event) {
      const index = Number(event.detail.value || 0)
      const item = this.statusOptions[index]
      this.form.status = item ? Number(item.value) : 1
    },
    buildPayload() {
      const payload = { ...this.form }
      payload.name = normalizeText(payload.name)
      payload.contactName = normalizeText(payload.contactName)
      payload.mobile = normalizeText(payload.mobile)
      payload.phone = normalizeText(payload.phone)
      payload.mail = normalizeText(payload.mail)
      payload.postal = normalizeText(payload.postal)
      payload.birthday = normalizeText(payload.birthday)
      payload.address = normalizeText(payload.address)
      payload.qq = normalizeText(payload.qq)
      payload.note = normalizeText(payload.note)
      payload.categoryId = normalizeText(payload.categoryId)
      payload.levelId = normalizeText(payload.levelId)
      ;['discount', 'defPayable', 'payable'].forEach((field) => {
        const value = this.toNumberOrEmpty(payload[field])
        if (value === '') delete payload[field]
        else payload[field] = value
      })
      payload.status = Number(payload.status === undefined || payload.status === null ? 1 : payload.status)
      if (this.isEdit) payload.id = this.partnerId
      return payload
    },
    async submitForm() {
      if (this.saving || !this.initialized) return

      const errors = await new Promise((resolve) => {
        this.$refs.formRef.validate((validationErrors) => resolve(validationErrors))
      })
      if (errors) return

      const payload = this.buildPayload()
      this.saving = true
      uni.showLoading({ title: '保存中...', mask: true })
      try {
        if (this.isEdit) await this.getEditApi()(payload)
        else await this.getAddApi()(payload)
        uni.removeStorageSync(this.getStorageKey())
        if (this.channel && this.channel.emit) {
          this.channel.emit('saved', {
            id: this.partnerId,
            mode: this.mode,
            name: payload.name
          })
        }
        uni.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 350)
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
      this.form = this.isEdit ? JSON.parse(JSON.stringify(this.originalForm)) : emptyForm()
      if (!this.isEdit && this.prefillName) {
        this.form.name = this.prefillName
      }
      this.$refs.formRef && this.$refs.formRef.clearValidate()
    },
    confirmDelete() {
      if (!this.isEdit || this.deleting) return
      uni.showModal({
        title: '确认删除',
        content: `删除后该${this.isCustomer ? '客户' : '供应商'}将从列表中移除，是否继续？`,
        confirmText: '删除',
        confirmColor: '#e5484d',
        success: (result) => {
          if (result.confirm) this.deletePartner()
        }
      })
    },
    async deletePartner() {
      if (this.deleting || !this.partnerId) return
      this.deleting = true
      uni.showLoading({ title: '删除中...', mask: true })
      try {
        await this.getRemoveApi()(this.partnerId)
        uni.removeStorageSync(this.getStorageKey())
        if (this.channel && this.channel.emit) {
          this.channel.emit('saved', {
            id: this.partnerId,
            mode: this.mode,
            deleted: true
          })
        }
        uni.showToast({ title: '删除成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 350)
      } catch (error) {
        uni.showToast({
          title: error && error.message ? error.message : '删除失败',
          icon: 'none'
        })
      } finally {
        uni.hideLoading()
        this.deleting = false
      }
    },
    redirectToLogin() {
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 22rpx;
  box-sizing: border-box;
  background: #f5f4fb;
}

.form-card {
  padding: 28rpx 24rpx calc(36rpx + env(safe-area-inset-bottom));
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 8rpx 24rpx rgba(67, 47, 119, .08);
}

.page-header {
  margin-bottom: 24rpx;
  padding-bottom: 22rpx;
  border-bottom: 1rpx solid #f0edf5;
}

.page-title {
  display: block;
  color: #30283d;
  font-size: 36rpx;
  font-weight: 700;
}

.page-subtitle {
  display: block;
  margin-top: 8rpx;
  color: #9a95a4;
  font-size: 22rpx;
}

.section-title {
  margin: 12rpx 0 8rpx;
  padding-left: 14rpx;
  color: #722ed1;
  font-size: 27rpx;
  font-weight: 600;
  border-left: 6rpx solid #722ed1;
}

.form-row {
  display: flex;
  gap: 18rpx;
}

.form-col {
  flex: 1;
  min-width: 0;
}

.picker-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 72rpx;
  padding: 0 20rpx;
  box-sizing: border-box;
  color: #393044;
  font-size: 25rpx;
  background: #fff;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}

.placeholder {
  color: #aaa5b5;
}

.date-picker-wrap {
  width: 100%;
}

.form-actions {
  display: flex;
  gap: 18rpx;
  margin-top: 18rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #f0edf5;
}

.form-actions button {
  flex: 1;
  height: 78rpx;
  margin: 0;
  font-size: 27rpx;
  line-height: 78rpx;
  border-radius: 10rpx;
}

.secondary-button {
  color: #666;
  background: #f2f3f5;
}

.primary-button {
  color: #fff;
  background: #722ed1;
}

.delete-button {
  width: 100%;
  height: 78rpx;
  margin: 20rpx 0 0;
  color: #fff;
  font-size: 27rpx;
  line-height: 78rpx;
  background: #e5484d;
  border-radius: 10rpx;
}

.delete-button::after,
.form-actions button::after {
  border: 0;
}

.form-actions button[disabled],
.delete-button[disabled] {
  opacity: .6;
}

:deep(.uni-forms-item) {
  margin-bottom: 20rpx;
}

:deep(.uni-forms-item__label) {
  color: #5f596b;
  font-size: 23rpx;
}

:deep(.uni-easyinput__content) {
  min-height: 72rpx;
  border-color: #ded9e8;
  border-radius: 10rpx;
}

:deep(.uni-easyinput__content-input) {
  color: #393044;
  font-size: 25rpx;
}

@media (max-width: 420px) {
  .form-row {
    gap: 12rpx;
  }
}
</style>
