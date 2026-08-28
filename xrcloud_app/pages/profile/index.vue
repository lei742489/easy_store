<template>
  <view class="placeholder-page">
    <image class="profile-avatar" src="/static/ico/def_head.png" mode="aspectFill" />
    <text class="placeholder-title">我的</text>
    <text class="placeholder-desc">个人资料和系统设置将在这里展开</text>
  </view>
</template>

<script>
import { isLoggedIn } from '../../common/auth'

export default {
  onShow() {
    if (!isLoggedIn()) uni.reLaunch({ url: '/pages/login/index' })
  }
}
</script>

<style lang="scss" scoped>
.placeholder-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100vh;
  padding-top: 220rpx;
  box-sizing: border-box;
  color: #625f6e;
  background: #f5f4fb;
}

.profile-avatar {
  width: 128rpx;
  height: 128rpx;
  border: 8rpx solid #fff;
  border-radius: 50%;
  box-shadow: 0 8rpx 24rpx rgba(53, 36, 96, 0.12);
}

.placeholder-title {
  margin-top: 24rpx;
  color: #33303f;
  font-size: 36rpx;
  font-weight: 600;
}

.placeholder-desc {
  margin-top: 14rpx;
  font-size: 24rpx;
}
</style>
