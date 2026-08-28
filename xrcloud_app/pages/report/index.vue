<template>
  <view class="placeholder-page">
    <view class="placeholder-icon">▥</view>
    <text class="placeholder-title">报表</text>
    <text class="placeholder-desc">资金、利润、库存统计将在这里展开</text>
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
  padding-top: 260rpx;
  box-sizing: border-box;
  color: #625f6e;
  background: #f5f4fb;
}

.placeholder-icon {
  color: #722ed1;
  font-size: 100rpx;
}

.placeholder-title {
  margin-top: 20rpx;
  color: #33303f;
  font-size: 36rpx;
  font-weight: 600;
}

.placeholder-desc {
  margin-top: 14rpx;
  font-size: 24rpx;
}
</style>
