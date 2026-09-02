<template>
  <view class="login-page">
    <scroll-view class="login-scroll" scroll-y :show-scrollbar="false">
      <view class="login-card">
        <image class="logo" src="/static/icon.png" mode="widthFix" />
        <text class="brand-title">{{ appName }}</text>
        <text class="page-title">登录账号</text>

        <view class="form">
          <view class="form-item">
            <view class="field-icon"><uni-icons type="person" color="#722ed1" :size="24" /></view>
            <input
              v-model="form.username"
              class="field-input"
              type="text"
              maxlength="30"
              placeholder="请输入用户名"
              placeholder-class="field-placeholder"
              @confirm="handleLogin"
            />
          </view>

          <view class="form-item">
            <view class="field-icon"><uni-icons type="locked" color="#722ed1" :size="25" /></view>
            <input
              v-model="form.password"
              class="field-input"
              :password="!showPassword"
              maxlength="50"
              placeholder="请输入密码"
              placeholder-class="field-placeholder"
              @confirm="handleLogin"
            />
            <view class="password-toggle" @click="showPassword = !showPassword">
              <uni-icons :type="showPassword ? 'eye' : 'eye-slash'" color="#999" :size="25" />
            </view>
          </view>

          <button class="login-button" :disabled="loading" @click="handleLogin">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </view>
      </view>
    </scroll-view>

    <text class="app-version">版本: v{{ appVersion }}</text>
  </view>
</template>

<script>
import { APP_NAME } from '../../common/config'
import { login, getUserInfo } from '../../common/api'
import {
  getToken,
  saveSession,
  updateUser,
  clearSession,
  getLastLoginUsername,
  saveLastLoginUsername
} from '../../common/auth'

export default {
  data() {
    return {
      showPassword: false,
      loading: false,
      appVersion: '1.0.0',
      appName: APP_NAME,
      form: { username: getLastLoginUsername(), password: '' }
    }
  },
  onLoad() {
    const appBaseInfo = uni.getAppBaseInfo ? uni.getAppBaseInfo() : {}
    this.appVersion = appBaseInfo.appVersion || this.appVersion
    if (getToken()) uni.reLaunch({ url: '/pages/index/index' })
  },
  methods: {
    async handleLogin() {
      if (this.loading) return
      const username = String(this.form.username || '').trim()
      const password = String(this.form.password || '')
      if (!username || !password) {
        uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
        return
      }

      this.loading = true
      uni.showLoading({ title: '登录中...', mask: true })
      try {
        const user = await login({ username, password })
        saveLastLoginUsername(username)
        saveSession(user)
        const currentUser = await getUserInfo()
        updateUser({ ...user, ...currentUser, token: user.token })
        uni.hideLoading()
        uni.reLaunch({ url: '/pages/index/index' })
      } catch (error) {
        clearSession()
        uni.hideLoading()
        uni.showToast({
          title: error && error.message ? error.message : '登录失败，请稍后重试',
          icon: 'none',
          duration: 3000
        })
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  color: #30283d;
  background: linear-gradient(180deg, #eee8fb 0%, #f8f7fc 44%, #fff 100%);
}
.login-scroll { height: 100vh; box-sizing: border-box; }
.login-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 150rpx 44rpx 120rpx;
  box-sizing: border-box;
}
.logo {
  width: 150rpx;
  padding: 18rpx;
  box-sizing: border-box;
  background: #fff;
  border: 1rpx solid rgba(114, 46, 209, .1);
  border-radius: 34rpx;
  box-shadow: 0 16rpx 32rpx rgba(89, 54, 151, .16);
}
.brand-title { margin-top: 20rpx; color: #722ed1; font-size: 34rpx; font-weight: 700; }
.page-title { margin-top: 54rpx; color: #30283d; font-size: 42rpx; font-weight: 700; }
.form { width: 100%; margin-top: 42rpx; }
.form-item {
  display: flex;
  align-items: center;
  height: 104rpx;
  margin-bottom: 26rpx;
  padding: 0 28rpx;
  box-sizing: border-box;
  background: rgba(255, 255, 255, .95);
  border: 1rpx solid #ded9e8;
  border-radius: 20rpx;
}
.field-icon { display: flex; align-items: center; justify-content: center; width: 38rpx; margin-right: 18rpx; }
.field-input { flex: 1; min-width: 0; height: 100%; color: #393044; font-size: 29rpx; }
.field-placeholder { color: #aaa5b5; }
.password-toggle { display: flex; align-items: center; justify-content: center; width: 44rpx; height: 44rpx; margin-left: 12rpx; }
.login-button {
  width: 100%;
  height: 96rpx;
  margin-top: 54rpx;
  color: #fff;
  font-size: 31rpx;
  font-weight: 600;
  line-height: 96rpx;
  background: #722ed1;
  border: 0;
  border-radius: 20rpx;
  box-shadow: 0 14rpx 26rpx rgba(114, 46, 209, .22);
}
.login-button::after { border: 0; }
.login-button[disabled] { opacity: .65; }
.app-version { position: fixed; right: 0; bottom: 42rpx; left: 0; z-index: 2; color: #aaa5b5; font-size: 23rpx; line-height: 1; text-align: center; }
</style>
