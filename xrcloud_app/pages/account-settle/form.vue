<template>
  <view class="page">
    <view class="form-card">
      <view class="page-header">
        <text class="page-title">{{ isEdit ? '编辑结算账户' : '新增结算账户' }}</text>
        <text class="page-subtitle">维护账户名称、分类、银行信息和余额</text>
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
        <uni-forms-item name="name" label="账户名称" required>
          <uni-easyinput
            v-model="form.name"
            :input-border="true"
            maxlength="100"
            placeholder="请输入账户名称"
          />
        </uni-forms-item>

        <uni-forms-item name="typeId" label="账户分类">
          <picker
            mode="selector"
            :range="typeOptions"
            range-key="text"
            :value="typePickerIndex"
            @change="onTypeChange"
          >
            <view class="picker-field" :class="{ placeholder: !form.typeId }">
              <text>{{ typeDisplayText }}</text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </uni-forms-item>

        <view class="form-row">
          <view class="form-col">
            <uni-forms-item name="bankName" label="银行名称">
              <uni-easyinput
                v-model="form.bankName"
                :input-border="true"
                maxlength="100"
                placeholder="请输入银行名称"
              />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="bankCard" label="银行卡号">
              <uni-easyinput
                v-model="form.bankCard"
                :input-border="true"
                maxlength="100"
                placeholder="请输入银行卡号"
              />
            </uni-forms-item>
          </view>
        </view>

        <view class="section-title">余额信息</view>
        <view class="form-row">
          <view class="form-col">
            <uni-forms-item name="initPrc" label="初始余额">
              <uni-easyinput
                v-model="form.initPrc"
                type="number"
                :input-border="true"
                disabled
                placeholder="自动生成"
              />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="curPrc" label="当前余额">
              <uni-easyinput
                v-model="form.curPrc"
                type="number"
                :input-border="true"
                :disabled="isEdit"
                placeholder="请输入当前余额"
              />
            </uni-forms-item>
          </view>
        </view>

        <uni-forms-item name="note" label="备注">
          <uni-easyinput
            v-model="form.note"
            type="textarea"
            :input-border="true"
            :auto-height="true"
            maxlength="100"
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
        {{ deleting ? '删除中...' : '删除结算账户' }}
      </button>
    </view>
  </view>
</template>

<script>
import { clearSession, getUser, isLoggedIn, updateUser } from '../../common/auth'
import {
  addAccountSettle,
  editAccountSettle,
  getUserInfo,
  listAccountSettlePage,
  listAccountSettleTypes,
  removeAccountSettle
} from '../../common/api'

const emptyForm = () => ({
  id: '',
  name: '',
  typeId: '',
  typeId_dictText: '',
  bankName: '',
  bankCard: '',
  initPrc: '',
  curPrc: '',
  note: ''
})

const textValue = (value) => {
  if (value === undefined || value === null) return ''
  return String(value)
}

const numberValue = (value) => {
  if (value === undefined || value === null || value === '') return ''
  const number = Number(value)
  return Number.isFinite(number) ? number : ''
}

export default {
  data() {
    return {
      user: getUser() || {},
      accountId: '',
      form: emptyForm(),
      originalForm: emptyForm(),
      typeOptions: [{ value: '', text: '请选择账户分类' }],
      initialized: false,
      saving: false,
      deleting: false,
      channel: null,
      rules: {
        name: { rules: [{ required: true, errorMessage: '请输入账户名称' }] }
      }
    }
  },
  computed: {
    isEdit() {
      return Boolean(this.accountId)
    },
    typePickerIndex() {
      const index = this.typeOptions.findIndex(
        (item) => String(item.value) === String(this.form.typeId)
      )
      return index >= 0 ? index : 0
    },
    typeDisplayText() {
      const option = this.typeOptions[this.typePickerIndex]
      return this.form.typeId && option ? option.text : '请选择账户分类'
    }
  },
  onLoad(options) {
    this.accountId = options && options.id ? String(options.id) : ''
    this.channel = this.getOpenerEventChannel ? this.getOpenerEventChannel() : null
    this.setPageTitle()
    this.loadForm()
  },
  methods: {
    setPageTitle() {
      uni.setNavigationBarTitle({
        title: this.isEdit ? '编辑结算账户' : '新增结算账户'
      })
    },
    async loadForm() {
      if (!isLoggedIn()) {
        this.redirectToLogin()
        return
      }
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...currentUser }
        updateUser(this.user)
        this.form = emptyForm()
        if (this.isEdit) {
          const cached = uni.getStorageSync('easy-store-edit-account-settle')
          if (cached && String(cached.id) === this.accountId) {
            this.form = this.normalizeForm(cached)
          } else {
            await this.loadRecordById()
          }
          uni.removeStorageSync('easy-store-edit-account-settle')
        }
        await this.loadTypes()
        this.originalForm = JSON.parse(JSON.stringify(this.form))
        this.initialized = true
      } catch (error) {
        this.handleError(error, '结算账户加载失败')
      }
    },
    async loadRecordById() {
      const page = await listAccountSettlePage({
        current: 1,
        pageSize: 1,
        id: this.accountId,
        userId: this.user.id
      })
      const record = page && Array.isArray(page.records) ? page.records[0] : null
      if (!record || String(record.id) !== this.accountId) {
        throw new Error('未找到需要编辑的结算账户')
      }
      this.form = this.normalizeForm(record)
    },
    async loadTypes() {
      const data = await listAccountSettleTypes(this.user.id)
      this.typeOptions = [{ value: '', text: '请选择账户分类' }]
        .concat(
          (Array.isArray(data) ? data : [])
            .filter((item) => item && item.id !== undefined && item.id !== null)
            .map((item) => ({
              value: String(item.id),
              text: item.title || item.name || `分类${item.id}`
            }))
        )
    },
    normalizeForm(record) {
      const source = record || {}
      return {
        ...emptyForm(),
        ...source,
        id: textValue(source.id),
        name: textValue(source.name),
        typeId: textValue(source.typeId),
        typeId_dictText: textValue(source.typeId_dictText),
        bankName: textValue(source.bankName),
        bankCard: textValue(source.bankCard),
        initPrc: numberValue(source.initPrc),
        curPrc: numberValue(source.curPrc),
        note: textValue(source.note)
      }
    },
    onTypeChange(event) {
      const index = Number(event && event.detail ? event.detail.value || 0 : 0)
      const option = this.typeOptions[index]
      this.form.typeId = option ? String(option.value) : ''
      this.form.typeId_dictText = option ? option.text : ''
    },
    buildPayload() {
      const payload = {
        ...this.form,
        userId: this.user.id,
        name: textValue(this.form.name).trim(),
        typeId: textValue(this.form.typeId).trim(),
        bankName: textValue(this.form.bankName).trim(),
        bankCard: textValue(this.form.bankCard).trim(),
        note: textValue(this.form.note).trim()
      }
      if (this.isEdit) payload.id = this.accountId
      if (payload.typeId === '') delete payload.typeId
      ;['initPrc', 'curPrc'].forEach((field) => {
        if (payload[field] === '' || payload[field] === null || payload[field] === undefined) {
          delete payload[field]
        } else {
          payload[field] = Number(payload[field])
        }
      })
      return payload
    },
    async submitForm() {
      if (this.saving || !this.initialized) return
      const errors = await new Promise((resolve) => {
        this.$refs.formRef.validate((validationErrors) => resolve(validationErrors))
      })
      if (errors) return
      this.saving = true
      uni.showLoading({ title: '保存中...', mask: true })
      try {
        const payload = this.buildPayload()
        if (this.isEdit) await editAccountSettle(payload)
        else await addAccountSettle(payload)
        if (this.channel && this.channel.emit) {
          this.channel.emit('saved', { id: this.accountId })
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
      this.form = this.isEdit
        ? JSON.parse(JSON.stringify(this.originalForm))
        : emptyForm()
      this.$refs.formRef && this.$refs.formRef.clearValidate()
    },
    confirmDelete() {
      if (!this.isEdit || this.deleting) return
      uni.showModal({
        title: '确认删除',
        content: '删除后该结算账户将从列表中移除，是否继续？',
        confirmText: '删除',
        confirmColor: '#e5484d',
        success: (result) => {
          if (result.confirm) this.deleteAccount()
        }
      })
    },
    async deleteAccount() {
      if (this.deleting || !this.accountId) return
      this.deleting = true
      uni.showLoading({ title: '删除中...', mask: true })
      try {
        await removeAccountSettle({ id: this.accountId, userId: this.user.id })
        if (this.channel && this.channel.emit) {
          this.channel.emit('saved', { id: this.accountId, deleted: true })
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
    handleError(error, fallback) {
      const message = error && error.message ? error.message : fallback
      if (/登录|token|过期/i.test(message)) {
        this.redirectToLogin()
        return
      }
      uni.showToast({ title: message, icon: 'none' })
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

.picker-field text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.placeholder { color: #aaa5b5; }

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

:deep(.uni-forms-item) { margin-bottom: 20rpx; }
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
  .form-row { gap: 12rpx; }
}
</style>
