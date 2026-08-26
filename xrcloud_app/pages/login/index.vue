<template>
  <view class="login-page">
    <view class="login-top">
     
    </view>

    <scroll-view class="login-scroll" scroll-y>
      <view class="login-card">
        <image class="logo" src="/static/logo-big.png" mode="widthFix" />
        <!-- <text class="brand-title">新锐云</text> -->
        <text class="page-title">登录账号</text>
        

        <view class="form">
          <view class="form-item">
            <view class="field-icon">
				<uni-icons type="person" color="#999" :size="24"></uni-icons>
			</view>
            <input
              v-model="form.username"
              class="field-input"
              type="text"
              maxlength="30"
              placeholder="请输入账号"
              placeholder-class="field-placeholder"
            />
          </view>

          <view class="form-item">
			  
            <view class="field-icon">
				<uni-icons type="locked" color="#999" :size="25"></uni-icons>
			</view>
            <input
              v-model="form.password"
              class="field-input"
              :password="!showPassword"
              maxlength="20"
              placeholder="请输入密码"
              placeholder-class="field-placeholder"
            />
            <view class="password-toggle" @click="showPassword = !showPassword">
				<uni-icons :type="showPassword?'eye' :'eye-slash' " color="#999" :size="25"></uni-icons>
				
            </view>
          </view>

          <button class="login-button" @click="handleLogin">登录</button>
        </view>

      </view>
    </scroll-view>

    <text class="app-version">版本: v{{ appVersion }}</text>
  </view>
</template>

<script>
export default {
  data() {
    return {
      showPassword: false,
      appVersion: '1.0.0',
      form: {
        username: '',
        password: ''
      }
    }
  },
  onLoad() {
    const appBaseInfo = uni.getAppBaseInfo ? uni.getAppBaseInfo() : {}
    this.appVersion = appBaseInfo.appVersion || this.appVersion
  },
  methods: {
    handleLogin() {
      uni.showToast({
        title: '登录功能待接入',
        icon: 'none'
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  color: #30283d;
  background: linear-gradient(180deg, #eee8fb 0%, #f8f7fc 44%, #ffffff 100%);
}

.login-top {
  height: 104rpx;
  padding: 34rpx 34rpx 0;
  box-sizing: border-box;
}

.back-button {
  display: flex;
  align-items: center;
  width: fit-content;
  color: #6e6490;
  font-size: 25rpx;
}

.back-arrow {
  margin-right: 8rpx;
  font-size: 48rpx;
  line-height: 24rpx;
}

.login-scroll {
  height: calc(100vh - 104rpx);
  box-sizing: border-box;
}

.login-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 58rpx 44rpx 90rpx;
  box-sizing: border-box;
}

.logo {
  width: 200rpx;
  padding: 18rpx;
  box-sizing: border-box;
  background: #fff;
  border: 1rpx solid rgba(114, 46, 209, 0.1);
  border-radius: 34rpx;
  box-shadow: 0 16rpx 32rpx rgba(89, 54, 151, 0.16);
}

.brand-title {
  margin-top: 20rpx;
  color: #722ed1;
  font-size: 34rpx;
  font-weight: 700;
}

.page-title {
  margin-top: 54rpx;
  color: #30283d;
  font-size: 42rpx;
  font-weight: 700;
}

.page-subtitle {
  margin-top: 14rpx;
  color: #9891aa;
  font-size: 23rpx;
}

.form {
  width: 100%;
  margin-top: 42rpx;
}

.form-item {
  display: flex;
  align-items: center;
  height: 104rpx;
  margin-bottom: 26rpx;
  padding: 0 28rpx;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.95);
  border: 1rpx solid #ded9e8;
  border-radius: 20rpx;
}

.field-icon {
  width: 38rpx;
  margin-right: 18rpx;
  color: #722ed1;
  font-size: 34rpx;
  text-align: center;
}

.field-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  color: #393044;
  font-size: 29rpx;
}

.field-placeholder {
  color: #aaa5b5;
}

.password-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44rpx;
  height: 44rpx;
  margin-left: 12rpx;
  color: #a29bad;
  font-size: 22rpx;

}

.login-button {
  width: 100%;
  height: 96rpx;
  margin-top: 54rpx;
  color: #fff;
  font-size: 31rpx;
  font-weight: 600;
  line-height: 104rpx;
  letter-spacing: 0;
  background: #722ed1;
  border: 0;
  border-radius: 20rpx;
  box-shadow: 0 14rpx 26rpx rgba(114, 46, 209, 0.22);
}

.app-version {
  position: fixed;
  right: 0;
  bottom: 42rpx;
  left: 0;
  z-index: 2;
  color: #aaa5b5;
  font-size: 23rpx;
  line-height: 1;
  text-align: center;
}

</style>
