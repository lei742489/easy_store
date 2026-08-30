<template>
  <view class="page">
    <view class="form-card">
      <view class="page-header">
        <text class="page-title">{{ isEdit ? '编辑货品' : '新增货品' }}</text>
        <text class="page-subtitle">完善货品基础信息和库存价格信息</text>
      </view>

      <uni-forms
        ref="formRef"
        :model-value="form"
        :rules="rules"
        label-position="top"
        label-width="auto"
        validate-trigger="submit"
      >
        <uni-forms-item name="title" label="货品名称" required>
          <uni-easyinput v-model="form.title" :input-border="true" placeholder="请输入货品名称" />
        </uni-forms-item>

        <view class="form-row">
          <view class="form-col">
            <uni-forms-item name="goodsCode" label="货品代码">
              <uni-easyinput v-model="form.goodsCode" :input-border="true" placeholder="请输入货品代码" />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="categoryId" label="货品分类" required>
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
            <uni-forms-item name="unit" label="单位">
              <picker
                mode="selector"
                :range="unitOptions"
                range-key="text"
                :value="unitPickerIndex"
                @change="onUnitChange"
              >
                <view class="picker-field" :class="{ placeholder: !form.unit }">
                  <text>{{ unitDisplayText }}</text>
                  <uni-icons type="down" color="#999" :size="16" />
                </view>
              </picker>
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="supplierId" label="供应商">
              <view
                class="picker-field selector-field"
                :class="{ placeholder: !form.supplierId && !form.supplierTitle }"
                @click="openSupplierSelect"
              >
                <text>{{ supplierDisplayText }}</text>
                <uni-icons type="right" color="#999" :size="16" />
              </view>
            </uni-forms-item>
          </view>
        </view>
		
		<view class="image-section">
		  <view class="image-header">
		    <text class="section-title">产品图片</text>
		    <!-- <button class="mini-button" :disabled="uploading" @click="chooseAndUploadImage">
		      <uni-icons type="upload" color="#722ed1" :size="16" />
		      <text>{{ uploading ? '上传中' : '选择图片' }}</text>
		    </button> -->
			
			<view class="image-preview-wrap" @click="chooseAndUploadImage">
			  <image class="image-preview" :src="imagePreviewUrl" mode="widthFix" />
			</view>
		  </view>
		 
		  <!-- <view class="image-path-row">
		    <uni-easyinput v-model="form.imgUrl" :input-border="true" placeholder="上传后自动填入图片路径，也可手动修改" />
		  </view> -->
		</view>

        <view class="section-title">库存与成本</view>
        <view class="form-row three-col">
          <view class="form-col">
            <uni-forms-item name="initCost" label="初始成本">
              <uni-easyinput v-model="form.initCost" type="number" :input-border="true" :disabled="isEdit" placeholder="0.00" />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="stock" label="当前库存">
              <uni-easyinput v-model="form.stock" type="number" :input-border="true" :disabled="isEdit" placeholder="0" />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="initStock" label="初始库存">
              <uni-easyinput v-model="form.initStock" type="number" :input-border="true" disabled placeholder="自动生成" />
            </uni-forms-item>
          </view>
        </view>

        <view class="form-row">
          <view class="form-col">
            <uni-forms-item name="stockCost" label="库存总成本">
              <uni-easyinput v-model="form.stockCost" type="number" :input-border="true" disabled placeholder="0.00" />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="costPrice" label="当前成本价">
              <uni-easyinput v-model="form.costPrice" type="number" :input-border="true" disabled placeholder="0.00" />
            </uni-forms-item>
          </view>
        </view>

        <view class="section-title">销售价格</view>
        <view class="form-row three-col">
          <view class="form-col">
            <uni-forms-item name="salePrc" label="零售价">
              <uni-easyinput v-model="form.salePrc" type="number" :input-border="true" placeholder="0.00" />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="tradePrc" label="批发价">
              <uni-easyinput v-model="form.tradePrc" type="number" :input-border="true" placeholder="0.00" />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="purPrc" label="进货价">
              <uni-easyinput v-model="form.purPrc" type="number" :input-border="true" placeholder="0.00" />
            </uni-forms-item>
          </view>
        </view>

        <view class="form-row">
          <view class="form-col">
            <uni-forms-item name="maxStock" label="最大库存">
              <uni-easyinput v-model="form.maxStock" type="number" :input-border="true" placeholder="0" />
            </uni-forms-item>
          </view>
          <view class="form-col">
            <uni-forms-item name="minStock" label="最小库存">
              <uni-easyinput v-model="form.minStock" type="number" :input-border="true" placeholder="0" />
            </uni-forms-item>
          </view>
        </view>

        <uni-forms-item name="note" label="备注">
          <uni-easyinput v-model="form.note" type="textarea" :input-border="true" :auto-height="true" placeholder="请输入备注" />
        </uni-forms-item>

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
        {{ deleting ? '删除中...' : '删除货品' }}
      </button>
    </view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession } from '../../common/auth'
import {
  addGoods,
  editGoods,
  getUserInfo,
  listGoods,
  listGoodsCategories,
  listGoodsSuppliers,
  listGoodsUnits,
  removeGoods,
  uploadGoodsImage
} from '../../common/api'
import { API_BASE_URL } from '../../common/config'

const emptyForm = () => ({
  title: '',
  supplierTitle: '',
  goodsCode: '',
  categoryId: '',
  imgUrl: '',
  initCost: '',
  initStock: '',
  stock: '',
  stockCost: '',
  costPrice: '',
  unit: '',
  salePrc: '',
  tradePrc: '',
  purPrc: '',
  maxStock: '',
  minStock: '',
  supplierId: '',
  note: '',
  status: 1
})

export default {
  data() {
    return {
      user: getUser() || {},
      form: emptyForm(),
      originalForm: emptyForm(),
      goodsId: '',
      initialized: false,
      saving: false,
      deleting: false,
      uploading: false,
      categoryOptions: [],
      unitOptions: [],
      supplierOptions: [],
      statusOptions: [
        { value: 1, text: '启用' },
        { value: 0, text: '停用' }
      ],
      rules: {
        title: { rules: [{ required: true, errorMessage: '请输入货品名称' }] },
        categoryId: { rules: [{ required: true, errorMessage: '请选择货品分类' }] }
      }
    }
  },
  computed: {
    isEdit() {
      return Boolean(this.goodsId)
    },
    categoryPickerIndex() {
      const index = this.categoryOptions.findIndex((item) => String(item.value) === String(this.form.categoryId))
      return index >= 0 ? index : 0
    },
    unitPickerIndex() {
      const index = this.unitOptions.findIndex((item) => String(item.value) === String(this.form.unit))
      return index >= 0 ? index : 0
    },
    supplierPickerIndex() {
      const index = this.supplierOptions.findIndex((item) => String(item.value) === String(this.form.supplierId))
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
    unitDisplayText() {
      return this.form.unit
        ? (this.unitOptions[this.unitPickerIndex] && this.unitOptions[this.unitPickerIndex].text) || '请选择单位'
        : '请选择单位'
    },
    supplierDisplayText() {
      return this.form.supplierId
        ? (this.supplierOptions[this.supplierPickerIndex] && this.supplierOptions[this.supplierPickerIndex].text) || '请选择供应商'
        : '请选择供应商'
    },
    statusDisplayText() {
      return (this.statusOptions[this.statusPickerIndex] && this.statusOptions[this.statusPickerIndex].text) || '请选择状态'
    },
    imagePreviewUrl() {
      if (this.form.imgUrl) return this.resolveImageUrl(this.form.imgUrl)
      return '/static/ico/none.png'
    }
  },
  onLoad(options) {
    this.goodsId = options && options.id ? String(options.id) : ''
    this.loadForm()
  },
  methods: {
    resolveImageUrl(image) {
      const value = String(image || '').trim()
      if (!value) return '/static/ico/none.png'
      if (/^(https?:)?\/\//i.test(value) || value.indexOf('data:') === 0) return value
      return `${API_BASE_URL.replace(/\/$/, '')}/api/upload/static${value.startsWith('/') ? value : `/${value}`}`
    },
    async loadForm() {
      if (!isLoggedIn()) {
        this.redirectToLogin()
        return
      }
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...currentUser }
        const cachedGoods = uni.getStorageSync('easy-store-edit-goods')
        if (this.isEdit && cachedGoods && String(cachedGoods.id) === this.goodsId) {
          this.form = this.normalizeForm(cachedGoods)
        } else if (this.isEdit) {
          await this.loadGoodsById()
        }
        this.originalForm = JSON.parse(JSON.stringify(this.form))
        await Promise.all([this.loadCategories(), this.loadUnits(), this.loadSuppliers()])
        this.initialized = true
      } catch (error) {
        uni.showToast({ title: error && error.message ? error.message : '货品数据加载失败', icon: 'none' })
      }
    },
    async loadGoodsById() {
      const page = await listGoods({
        current: 1,
        pageSize: 1,
        id: this.goodsId,
        userId: this.user.id
      })
      const goods = page && Array.isArray(page.records) ? page.records[0] : null
      if (!goods || String(goods.id) !== this.goodsId) {
        throw new Error('未找到需要编辑的货品')
      }
      this.form = this.normalizeForm(goods)
    },
    normalizeForm(goods) {
      const result = { ...emptyForm(), ...goods }
      result.categoryId = this.toSelectValue(goods.categoryId)
      result.supplierId = this.toSelectValue(goods.supplierId)
      result.unit = this.toSelectValue(goods.unit)
      result.status = Number(goods.status === undefined || goods.status === null ? 1 : goods.status)
      return result
    },
    toSelectValue(value) {
      return value === undefined || value === null || value === '' ? '' : String(value)
    },
    async loadCategories() {
      const data = await listGoodsCategories()
      const options = []
      const append = (items, level = 0) => {
        ;(Array.isArray(items) ? items : []).forEach((item) => {
          if (!item || item.id === undefined || item.id === null) return
          if (Number(item.id) !== 0) {
            options.push({ value: String(item.id), text: `${level ? '  '.repeat(level) : ''}${item.title || '未命名分类'}` })
          }
          append(item.children, level + 1)
        })
      }
      append(data)
      if (!options.length) {
        ;(Array.isArray(data) ? data : [])
          .filter((item) => item && Number(item.id) !== 0 && Number(item.parentId || 0) <= 0)
          .forEach((item) => options.push({ value: String(item.id), text: item.title || '未命名分类' }))
      }
      this.categoryOptions = [{ value: '', text: '请选择分类' }, ...options]
    },
    async loadUnits() {
      const data = await listGoodsUnits()
      const options = (Array.isArray(data) ? data : [])
        .filter((item) => item && item.name)
        .map((item) => ({ value: String(item.name), text: item.name }))
      this.unitOptions = [{ value: '', text: '请选择单位' }, ...options]
    },
    async loadSuppliers() {
      const data = await listGoodsSuppliers()
      const options = (Array.isArray(data) ? data : [])
        .filter((item) => item && item.id !== undefined)
        .map((item) => ({
          value: String(item.id),
          text: item.name || String(item.id)
        }))
      this.supplierOptions = [{ value: '', text: '请选择供应商' }, ...options]
    },
    redirectToLogin() {
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    },
    onCategoryChange(event) {
      const index = Number(event.detail.value || 0)
      const item = this.categoryOptions[index]
      this.form.categoryId = item ? String(item.value) : ''
    },
    onUnitChange(event) {
      const index = Number(event.detail.value || 0)
      const item = this.unitOptions[index]
      this.form.unit = item ? String(item.value) : ''
    },
    onSupplierChange(event) {
      const index = Number(event.detail.value || 0)
      const item = this.supplierOptions[index]
      this.form.supplierId = item ? String(item.value) : ''
      this.form.supplierTitle = item ? String(item.text || '') : ''
    },
    openSupplierSelect() {
      uni.navigateTo({
        url: `/pages/partner-select/index?mode=supplier&title=${encodeURIComponent('供应商查询')}&keyword=${encodeURIComponent(this.form.supplierTitle || '')}`,
        success: (res) => {
          const channel = res && res.eventChannel
          if (!channel) return
          channel.on('select', (payload) => {
            if (!payload) return
            const id = payload.id ? String(payload.id) : String(payload.name || '')
            const name = payload.name ? String(payload.name) : ''
            this.form.supplierId = id
            this.form.supplierTitle = name
            if (id && name && !this.supplierOptions.some((item) => String(item.value) === id)) {
              this.supplierOptions = [
                ...this.supplierOptions,
                { value: id, text: name }
              ]
            }
          })
        }
      })
    },
    onStatusChange(event) {
      const index = Number(event.detail.value || 0)
      const item = this.statusOptions[index]
      this.form.status = item ? Number(item.value) : 1
    },
    async chooseAndUploadImage() {
      if (this.uploading) return
      try {
        const result = await new Promise((resolve, reject) => {
          uni.chooseImage({
            count: 1,
            sizeType: ['compressed'],
            sourceType: ['album', 'camera'],
            success: resolve,
            fail: reject
          })
        })
        const filePath = result && result.tempFilePaths && result.tempFilePaths[0]
        if (!filePath) return
        this.uploading = true
        uni.showLoading({ title: '上传中...', mask: true })
        const uploaded = await uploadGoodsImage(filePath)
        this.form.imgUrl = uploaded || ''
      } catch (error) {
        if (error && error.errMsg && /cancel/i.test(error.errMsg)) return
        uni.showToast({ title: error && error.message ? error.message : '图片上传失败', icon: 'none' })
      } finally {
        this.uploading = false
        uni.hideLoading()
      }
    },
    previewImage() {
      const url = this.imagePreviewUrl
      if (!url) return
      uni.previewImage({
        urls: [url],
        current: 0
      })
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
        if (this.isEdit) await editGoods(payload)
        else await addGoods(payload)
        uni.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 350)
      } catch (error) {
        uni.showToast({ title: error && error.message ? error.message : '保存失败', icon: 'none' })
      } finally {
        uni.hideLoading()
        this.saving = false
      }
    },
    buildPayload() {
      const payload = { ...this.form, userId: this.user.id }
      if (this.isEdit) payload.id = this.goodsId
      ;['initCost', 'initStock', 'stock', 'stockCost', 'costPrice', 'salePrc', 'tradePrc', 'purPrc', 'maxStock', 'minStock'].forEach((field) => {
        if (payload[field] === '' || payload[field] === null || payload[field] === undefined) delete payload[field]
        else payload[field] = Number(payload[field])
      })
      if (payload.categoryId !== '') payload.categoryId = String(payload.categoryId)
      if (payload.supplierId !== '') payload.supplierId = String(payload.supplierId)
      if (payload.unit !== '') payload.unit = String(payload.unit)
      payload.status = Number(payload.status)
      return payload
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
        content: '删除后该货品将从货品列表中移除，是否继续？',
        confirmText: '删除',
        confirmColor: '#e5484d',
        success: (result) => {
          if (result.confirm) this.deleteGoods()
        }
      })
    },
    async deleteGoods() {
      if (this.deleting || !this.goodsId) return
      this.deleting = true
      uni.showLoading({ title: '删除中...', mask: true })
      try {
        await removeGoods(this.goodsId)
        uni.removeStorageSync('easy-store-edit-goods')
        uni.showToast({ title: '删除成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 350)
      } catch (error) {
        uni.showToast({ title: error && error.message ? error.message : '删除失败', icon: 'none' })
      } finally {
        uni.hideLoading()
        this.deleting = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; padding: 22rpx; box-sizing: border-box; background: #f5f4fb; }
.form-card { padding: 28rpx 24rpx calc(36rpx + env(safe-area-inset-bottom)); background: #fff; border: 1rpx solid #ece9f2; border-radius: 16rpx; box-shadow: 0 8rpx 24rpx rgba(67, 47, 119, .08); }
.page-header { margin-bottom: 24rpx; padding-bottom: 22rpx; border-bottom: 1rpx solid #f0edf5; }
.page-title { display: block; color: #30283d; font-size: 36rpx; font-weight: 700; }
.page-subtitle { display: block; margin-top: 8rpx; color: #9a95a4; font-size: 22rpx; }
.form-row { display: flex; gap: 18rpx; }
.form-col { flex: 1; min-width: 0; }
.three-col { gap: 12rpx; }
.section-title { margin: 12rpx 0 8rpx; padding-left: 14rpx; color: #722ed1; font-size: 27rpx; font-weight: 600; border-left: 6rpx solid #722ed1; }
.image-section { margin: 8rpx 0 18rpx; }
.image-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
.mini-button {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  height: 54rpx;
  margin: 0;
  padding: 0 18rpx;
  color: #722ed1;
  font-size: 22rpx;
  line-height: 54rpx;
  background: #f4efff;
  border-radius: 10rpx;
}
.mini-button::after,
.form-actions button::after { border: 0; }
.image-preview-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 200rpx;
  min-height: 200rpx;
  
  overflow: hidden;
  background: #faf9fc;
  border: 1rpx dashed #ded9e8;
  border-radius: 14rpx;
}
.image-preview {
  width: 200rpx;
  
}
.image-path-row { margin-top: 6rpx; }
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
.placeholder { color: #aaa5b5; }
.form-actions { display: flex; gap: 18rpx; margin-top: 18rpx; padding-top: 24rpx; border-top: 1rpx solid #f0edf5; }
.form-actions button { flex: 1; height: 78rpx; margin: 0; font-size: 27rpx; line-height: 78rpx; border-radius: 10rpx; }
.secondary-button { color: #666; background: #f2f3f5; }
.primary-button { color: #fff; background: #722ed1; }
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
.delete-button::after { border: 0; }
.form-actions button[disabled] { opacity: .6; }
.delete-button[disabled] { opacity: .6; }
:deep(.uni-forms-item) { margin-bottom: 20rpx; }
:deep(.uni-forms-item__label) { color: #5f596b; font-size: 23rpx; }
:deep(.uni-easyinput__content) { min-height: 72rpx; border-color: #ded9e8; border-radius: 10rpx; }
:deep(.uni-easyinput__content-input) { color: #393044; font-size: 25rpx; }
@media (max-width: 420px) {
  .form-row { gap: 12rpx; }
  .three-col { display: block; }
}
</style>
