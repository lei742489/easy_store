<template>
  <view class="bottom-nav">
    <view
      v-for="item in items"
      :key="item.key"
      class="nav-item"
      :class="{ active: current === item.key }"
      @click="switchTab(item)"
    >
      <image class="nav-icon" :src="item.icon" mode="aspectFit" />
      <text>{{ item.label }}</text>
    </view>
  </view>
</template>

<script>
export default {
  props: {
    current: {
      type: String,
      default: 'home'
    }
  },
  data() {
    return {
      items: [
        { key: 'home', label: '首页', icon: '/static/logo-big.png', path: '/pages/index/index' },
        { key: 'business', label: '业务', icon: '/static/ico/sfc1.png', path: '/pages/business/index' },
        { key: 'report', label: '报表', icon: '/static/ico/tj2.png', path: '/pages/report/index' },
        { key: 'profile', label: '我的', icon: '/static/ico/def_head.png', path: '/pages/profile/index' }
      ]
    }
  },
  methods: {
    switchTab(item) {
      if (item.key === this.current) return
      uni.redirectTo({ url: item.path })
    }
  }
}
</script>

<style lang="scss" scoped>
.bottom-nav {
  position: fixed;
  right: 22rpx;
  bottom: calc(18rpx + env(safe-area-inset-bottom));
  left: 22rpx;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: 112rpx;
  padding: 8rpx 12rpx;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.97);
  border-radius: 28rpx;
  box-shadow: 0 8rpx 32rpx rgba(41, 29, 78, 0.15);
}

.nav-item {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #9a97a6;
  font-size: 20rpx;
}

.nav-icon {
  width: 44rpx;
  height: 44rpx;
  margin-bottom: 6rpx;
  opacity: 0.45;
}

.nav-item.active {
  color: #722ed1;
}

.nav-item.active .nav-icon {
  opacity: 1;
}
</style>
