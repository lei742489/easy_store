<template>
  <view class="page">
    <scroll-view class="page-scroll" scroll-y :show-scrollbar="false">
      <view class="form-card">
        <view class="card-heading">
          <view class="avatar-wrap" @click="chooseAvatar">
            <image class="avatar" :src="avatarUrl" mode="aspectFill" />
            <view class="avatar-camera">
              <uni-icons
                :type="avatarUploading ? 'spinner-cycle' : 'camera'"
                color="#fff"
                :size="17"
              />
            </view>
          </view>
          <view>
            <text class="heading-title">个人中心</text>
            <text class="heading-subtitle">维护您的账号资料</text>
          </view>
        </view>

        <view class="form-section">
          <view class="field-row readonly-row">
            <text class="field-label">登录账号</text>
            <text class="readonly-value">{{ form.userName || '-' }}</text>
          </view>
          <view v-if="isRoot" class="field-row">
            <text class="field-label">店铺名称</text>
            <input v-model="form.companyName" class="field-input" maxlength="100" placeholder="请输入店铺名称" />
          </view>
          <view class="field-row">
            <text class="field-label">姓名</text>
            <input v-model="form.realName" class="field-input" maxlength="50" placeholder="请输入姓名" />
          </view>
          <view class="field-row">
            <text class="field-label">邮箱</text>
            <input v-model="form.email" class="field-input" maxlength="100" placeholder="请输入邮箱" />
          </view>
          <view class="field-row">
            <text class="field-label">电话</text>
            <input v-model="form.mobile" class="field-input" maxlength="30" type="number" placeholder="请输入联系电话" />
          </view>
          <view v-if="isRoot" class="field-row">
            <text class="field-label">店铺地址</text>
            <input v-model="form.address" class="field-input" maxlength="200" placeholder="请输入店铺地址" />
          </view>
          <view class="field-row readonly-row">
            <text class="field-label">注册时间</text>
            <text class="readonly-value">{{ formatDate(form.createTime) }}</text>
          </view>
        </view>

        <button class="save-button" :disabled="saving" @click="saveProfile">
          {{ saving ? '保存中...' : '保存资料' }}
        </button>
      </view>
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getUserInfo, updateUserProfile, uploadGoodsImage } from '../../common/api'
import { clearSession, getUser, isLoggedIn, updateUser } from '../../common/auth'
import { getStaticFileUrl } from '../../common/file-url'

export default {
  data() {
    return {
      user: getUser() || {},
      form: {},
      saving: false,
      avatarUploading: false,
      redirecting: false
    }
  },
  computed: {
    isRoot() {
      return Number(this.form.isRoot || this.user.isRoot) === 1
    },
    avatarUrl() {
      return getStaticFileUrl(this.form.avatar) || '/static/ico/def_head.png'
    }
  },
  onLoad() {
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    this.loadUser()
  },
  methods: {
    async loadUser() {
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...(currentUser || {}) }
        this.form = { ...this.user }
        updateUser(this.user)
      } catch (error) {
        this.handleError(error)
      }
    },
    async saveProfile() {
      if (this.saving) return
      if (this.isRoot && !String(this.form.companyName || '').trim()) {
        uni.showToast({ title: '请输入店铺名称', icon: 'none' })
        return
      }
      this.saving = true
      uni.showLoading({ title: '保存中...', mask: true })
      try {
        await updateUserProfile({
          id: this.form.id,
          userId: this.form.id,
          realName: String(this.form.realName || '').trim(),
          email: String(this.form.email || '').trim(),
          mobile: String(this.form.mobile || '').trim(),
          ...(this.isRoot
            ? {
                companyName: String(this.form.companyName || '').trim(),
                address: String(this.form.address || '').trim()
              }
            : {})
        })
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...(currentUser || {}) }
        this.form = { ...this.user }
        updateUser(this.user)
        uni.showToast({ title: '保存成功', icon: 'success' })
      } catch (error) {
        this.handleError(error)
      } finally {
        uni.hideLoading()
        this.saving = false
      }
    },
    chooseAvatar() {
      if (this.avatarUploading) return
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: async (result) => {
          const filePath = result && result.tempFilePaths && result.tempFilePaths[0]
          if (!filePath) return
          this.avatarUploading = true
          uni.showLoading({ title: '上传中...', mask: true })
          try {
            const avatar = await uploadGoodsImage(filePath)
            if (!avatar) throw new Error('头像上传失败')
            await updateUserProfile({
              id: this.form.id,
              userId: this.form.id,
              avatar
            })
            this.form.avatar = avatar
            this.user = { ...this.user, avatar }
            updateUser(this.user)
            uni.showToast({ title: '头像更新成功', icon: 'success' })
          } catch (error) {
            this.handleError(error)
          } finally {
            uni.hideLoading()
            this.avatarUploading = false
          }
        }
      })
    },
    formatDate(value) {
      return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
    },
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    },
    handleError(error) {
      const message = error && error.message ? error.message : '操作失败'
      if (/登录|token|过期/i.test(message)) {
        this.redirectToLogin()
        return
      }
      uni.showToast({ title: message, icon: 'none' })
    }
  }
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #f5f4fb; }
.page-scroll { height: 100vh; box-sizing: border-box; }
.form-card { margin: 20rpx 22rpx 0; padding: 26rpx 24rpx calc(32rpx + env(safe-area-inset-bottom)); background: #fff; border: 1rpx solid #ece9f2; border-radius: 18rpx; box-shadow: 0 7rpx 22rpx rgba(67,47,119,.07); }
.card-heading { display: flex; align-items: center; padding-bottom: 26rpx; border-bottom: 1rpx solid #f0edf5; }
.avatar-wrap { position: relative; flex: 0 0 92rpx; width: 92rpx; height: 92rpx; margin-right: 18rpx; }
.avatar { width: 92rpx; height: 92rpx; border: 4rpx solid #f0eafe; border-radius: 50%; background: #fff; }
.avatar-camera { position: absolute; right: -4rpx; bottom: -2rpx; display: flex; align-items: center; justify-content: center; width: 34rpx; height: 34rpx; background: #722ed1; border: 3rpx solid #fff; border-radius: 50%; }
.heading-title, .heading-subtitle { display: block; }
.heading-title { color: #30283d; font-size: 33rpx; font-weight: 700; }
.heading-subtitle { margin-top: 8rpx; color: #aaa5b5; font-size: 22rpx; }
.form-section { margin-top: 18rpx; }
.field-row { display: flex; align-items: center; min-height: 88rpx; border-bottom: 1rpx solid #f0edf5; }
.field-row:last-child { border-bottom: 0; }
.field-label { flex: 0 0 150rpx; color: #625d6d; font-size: 25rpx; }
.field-input, .readonly-value { flex: 1; min-width: 0; color: #393044; font-size: 25rpx; text-align: right; }
.field-input { height: 72rpx; }
.field-input::placeholder { color: #b7b2bd; }
.readonly-value { overflow: hidden; color: #aaa5b5; text-overflow: ellipsis; white-space: nowrap; }
.save-button { width: 100%; height: 82rpx; margin: 28rpx 0 0; color: #fff; font-size: 27rpx; line-height: 82rpx; background: #722ed1; border-radius: 12rpx; box-shadow: 0 10rpx 20rpx rgba(114,46,209,.18); }
.save-button::after { border: 0; }
.save-button[disabled] { opacity: .6; }
.bottom-space { height: 34rpx; }
</style>
