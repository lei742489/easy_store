<template>
  <view class="page">
    <view class="security-card">
      <view class="card-heading">
        <view class="heading-icon"><uni-icons type="locked" color="#1677ff" :size="28" /></view>
        <view>
          <text class="heading-title">安全设置</text>
          <text class="heading-subtitle">修改登录密码，保护账号安全</text>
        </view>
      </view>
      <view class="form-section">
        <view class="field-row">
          <text class="field-label">新密码</text>
          <input v-model="form.password" class="field-input" :password="!showPassword" maxlength="50" placeholder="请输入新密码" />
        </view>
        <view class="field-row">
          <text class="field-label">确认密码</text>
          <input v-model="form.confirmPassword" class="field-input" :password="!showPassword" maxlength="50" placeholder="请再次输入新密码" />
        </view>
        <view class="password-switch" @click="showPassword = !showPassword">
          <uni-icons :type="showPassword ? 'eye' : 'eye-slash'" color="#722ed1" :size="20" />
          <text>{{ showPassword ? '隐藏密码' : '显示密码' }}</text>
        </view>
      </view>
      <button class="save-button" :disabled="saving" @click="savePassword">
        {{ saving ? '保存中...' : '保存密码' }}
      </button>
      <text class="password-tip">密码长度至少 6 位，修改后请使用新密码登录。</text>
    </view>
  </view>
</template>

<script>
import { getUserInfo, updateUserPassword } from '../../common/api'
import { clearSession, getUser, isLoggedIn, updateUser } from '../../common/auth'

export default {
  data() {
    return {
      user: getUser() || {},
      form: { password: '', confirmPassword: '' },
      showPassword: false,
      saving: false,
      redirecting: false
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
        updateUser(this.user)
      } catch (error) {
        this.handleError(error)
      }
    },
    async savePassword() {
      if (this.saving) return
      const password = String(this.form.password || '')
      const confirmPassword = String(this.form.confirmPassword || '')
      if (password.length < 6) {
        uni.showToast({ title: '密码长度至少 6 位', icon: 'none' })
        return
      }
      if (password !== confirmPassword) {
        uni.showToast({ title: '两次输入的密码不一致', icon: 'none' })
        return
      }
      this.saving = true
      uni.showLoading({ title: '保存中...', mask: true })
      try {
        await updateUserPassword({ userId: this.user.id, pwd: password })
        this.form = { password: '', confirmPassword: '' }
        uni.showToast({ title: '密码修改成功', icon: 'success' })
      } catch (error) {
        this.handleError(error)
      } finally {
        uni.hideLoading()
        this.saving = false
      }
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
.page { min-height: 100vh; padding: 20rpx 22rpx; box-sizing: border-box; background: #f5f4fb; }
.security-card { padding: 28rpx 24rpx 34rpx; background: #fff; border: 1rpx solid #ece9f2; border-radius: 18rpx; box-shadow: 0 7rpx 22rpx rgba(67,47,119,.07); }
.card-heading { display: flex; align-items: center; padding-bottom: 26rpx; border-bottom: 1rpx solid #f0edf5; }
.heading-icon { display: flex; align-items: center; justify-content: center; width: 76rpx; height: 76rpx; margin-right: 18rpx; background: #eaf3ff; border-radius: 18rpx; }
.heading-title, .heading-subtitle { display: block; }
.heading-title { color: #30283d; font-size: 33rpx; font-weight: 700; }
.heading-subtitle { margin-top: 8rpx; color: #aaa5b5; font-size: 22rpx; }
.form-section { margin-top: 18rpx; }
.field-row { display: flex; align-items: center; min-height: 92rpx; border-bottom: 1rpx solid #f0edf5; }
.field-label { flex: 0 0 150rpx; color: #625d6d; font-size: 25rpx; }
.field-input { flex: 1; min-width: 0; height: 72rpx; color: #393044; font-size: 25rpx; text-align: right; }
.field-input::placeholder { color: #b7b2bd; }
.password-switch { display: flex; align-items: center; justify-content: flex-end; gap: 7rpx; margin-top: 18rpx; color: #722ed1; font-size: 22rpx; }
.save-button { width: 100%; height: 82rpx; margin: 30rpx 0 0; color: #fff; font-size: 27rpx; line-height: 82rpx; background: #722ed1; border-radius: 12rpx; box-shadow: 0 10rpx 20rpx rgba(114,46,209,.18); }
.save-button::after { border: 0; }
.save-button[disabled] { opacity: .6; }
.password-tip { display: block; margin-top: 22rpx; color: #aaa5b5; font-size: 21rpx; line-height: 1.5; text-align: center; }
</style>
