<template>
  <view class="page">
	
    <view class="hero" id="top">
		<uni-status-bar></uni-status-bar>
      <view class="hero-top">
        <view class="brand">
          <image class="brand-logo" src="/static/logo-big.png" mode="aspectFit" />
          <text class="brand-name">新锐云·进销存管理系统</text>
        </view>
        <view class="hero-actions">
		  <uni-icons type="search" :size="24" color="#fff"></uni-icons>
          <!-- <image class="avatar" src="/static/ico/def_head.png" mode="aspectFill" /> -->
        </view>
      </view>
      <view class="welcome-row">
        <text class="welcome">欢迎回来！贰筱姐</text>
        <!-- <text class="updated-at">2026-08-25 10:21:58</text> -->
      </view>
    </view>

    <scroll-view
      class="content"
      scroll-y
	  :show-scrollbar="false"
      :style="{ height: `${contentHeight}px` }"
    >
      <view class="section quick-section">
        <view class="section-title-row">
          <text class="section-title">常用功能</text>
        </view>
        <view class="quick-grid">
          <view
            v-for="item in quickActions"
            :key="item.label"
            class="menu-card quick-item"
            @click="handleAction(item)"
          >
            <view class="icon-tile" :style="{ backgroundColor: item.bg }">
              <image :src="item.icon" mode="aspectFit" />
            </view>
            <text>{{ item.label }}</text>
          </view>
        </view>
        <view class="more-button" @click="showMore">
          <text>更多功能</text>
          <text class="more-arrow">›</text>
        </view>
      </view>

      <view class="section">
        <view class="section-title-row">
          <text class="section-title">销售相关</text>
        </view>
        <view class="feature-grid">
          <view
            v-for="item in salesActions"
            :key="item.label"
            class="menu-card feature-item"
            @click="handleAction(item)"
          >
            <view class="feature-icon-wrap">
              <image :src="item.icon" mode="aspectFit" />
              <text v-if="item.badge" class="badge">{{ item.badge }}</text>
            </view>
            <text>{{ item.label }}</text>
          </view>
        </view>
      </view>

      <view class="section">
        <view class="section-title-row">
          <text class="section-title">进货 / 库存</text>
        </view>
        <view class="feature-grid">
          <view
            v-for="item in stockActions"
            :key="item.label"
            class="menu-card feature-item"
            @click="handleAction(item)"
          >
            <view class="feature-icon-wrap">
              <image :src="item.icon" mode="aspectFit" />
              <text v-if="item.badge" class="badge">{{ item.badge }}</text>
            </view>
            <text>{{ item.label }}</text>
          </view>
        </view>
      </view>

      <view class="section">
        <view class="section-title-row">
          <text class="section-title">统计报告</text>
        </view>
        <view class="report-grid">
          <view
            v-for="item in reportActions"
            :key="item.label"
            class="menu-card report-item"
            @click="handleAction(item)"
          >
            <image :src="item.icon" mode="aspectFit" />
            <text>{{ item.label }}</text>
          </view>
        </view>
      </view>

      <view class="section announcement">
        <view class="section-title-row">
          <text class="section-title">通知公告</text>
          <text class="collapse-icon">⌃</text>
        </view>
        <view class="announcement-row">
          <view class="announcement-dot"></view>
          <text>欢迎使用新锐云进销存管理系统</text>
          <text class="announcement-date">08-25</text>
        </view>
      </view>

      <view class="bottom-space"></view>
    </scroll-view>

    <bottom-nav current="home" />
  </view>
</template>

<script>
import BottomNav from '../../components/bottom-nav.vue'

const icon = (name) => `/static/ico/${name}.png`

export default {
  components: { BottomNav },
  data() {
    return {
      contentHeight: 1,
      quickActions: [
        { label: '销售单', icon: icon('sm1'), bg: '#e6f7f6' },
        { label: '收款单', icon: icon('sm2'), bg: '#eaf8ed' },
        { label: '进货单', icon: icon('sm3'), bg: '#eaf4ff' },
        { label: '付款单', icon: icon('sm4'), bg: '#f2f8df' }
      ],
      salesActions: [
        { label: '销售单查询', icon: icon('sfc1'), badge: '10' },
        { label: '收款单查询', icon: icon('sfc2') },
        { label: '销售统计', icon: icon('sfc3') },
        { label: '应收对账单', icon: icon('sfc4') },
        { label: '欠款统计', icon: icon('sm9') }
      ],
      stockActions: [
        { label: '进货单查询', icon: icon('jm1'), badge: '5' },
        { label: '付款单查询', icon: icon('jm2') },
        { label: '进货统计', icon: icon('jm3') },
        { label: '应付对账单', icon: icon('jm4') },
        { label: '库存统计', icon: icon('jm5') },
        { label: '库存明细', icon: icon('dm1') },
        { label: '库存预警', icon: icon('dm2') },
        { label: '库存盘点', icon: icon('dm3') },
        { label: '库存商品', icon: icon('dm4') }
      ],
      reportActions: [
        { label: '资金统计', icon: icon('tj1') },
        { label: '利润统计', icon: icon('tj2') },
        { label: '营业员统计', icon: icon('tj3') }
      ]
    }
  },
  onLoad() {
    this.updateContentHeight()
  },
  onReady() {
    this.updateContentHeight()
    if (uni.onWindowResize) {
      uni.onWindowResize(this.updateContentHeight)
    }
  },
  onShow() {
    this.updateContentHeight()
  },
  onUnload() {
    if (uni.offWindowResize) {
      uni.offWindowResize(this.updateContentHeight)
    }
  },
  methods: {
    updateContentHeight() {
      const systemInfo = uni.getSystemInfoSync()
      uni.createSelectorQuery()
        .in(this)
        .select('#top')
        .boundingClientRect((rect) => {
          const topHeight = rect && rect.height ? rect.height : 0
          this.contentHeight = Math.max(
            1,
            Math.floor(systemInfo.windowHeight - topHeight)
          )
        })
        .exec()
    },
    handleAction(item) {
      uni.showToast({
        title: `${item.label}暂未接入`,
        icon: 'none'
      })
    },
    showMore() {
      uni.showToast({
        title: '更多功能暂未接入',
        icon: 'none'
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100%;
  background: #f5f4fb;
  color: #303044;
}

.hero {
  padding: 54rpx 30rpx 28rpx;
  box-sizing: border-box;
  color: #fff;
  background: linear-gradient(135deg, #4a238d 0%, #722ed1 65%, #8e5de8 100%);
  border-radius: 0 0 34rpx 34rpx;
}

.hero-top,
.welcome-row,
.section-title-row,
.announcement-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand,
.hero-actions {
  display: flex;
  align-items: center;
}

.brand-logo {
  width: 82rpx;
  height: 82rpx;
  margin-right: 12rpx;
  padding: 7rpx;
  box-sizing: border-box;
  background: #fff;
  border-radius: 15rpx;
}

.brand-name {
  font-size: 30rpx;
  font-weight: 600;
}

.hero-actions {
  gap: 24rpx;
}

.action-icon {
  font-size: 48rpx;
  line-height: 1;
  transform: rotate(-20deg);
}

.avatar {
  width: 55rpx;
  height: 55rpx;
  border: 3rpx solid rgba(255, 255, 255, 0.8);
  border-radius: 50%;
}

.welcome-row {
  margin-top: 34rpx;
}

.welcome {
  font-size: 30rpx;
  font-weight: 600;
}

.updated-at {
  font-size: 20rpx;
  opacity: 0.88;
}

.content {
  padding: 22rpx 22rpx 0;
  box-sizing: border-box;
}

.section {
  margin-bottom: 20rpx;
  padding: 24rpx 22rpx 20rpx;
  background: #fff;
  border-radius: 18rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, 0.05);
}

.quick-section {
  padding-bottom: 18rpx;
}

.section-title {
  color: #454252;
  font-size: 28rpx;
  font-weight: 600;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14rpx;
  margin-top: 18rpx;
}

.menu-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  height: 142rpx;
  box-sizing: border-box;
  color: #5a5865;
  font-size: 23rpx;
  text-align: center;
  background: #f7f7fa;
  border: 1rpx solid #f0eef5;
  border-radius: 14rpx;
}

.menu-card > text {
  display: block;
  width: 100%;
  padding: 0 6rpx;
  box-sizing: border-box;
  overflow: hidden;
  line-height: 30rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-item,
.feature-item,
.report-item {
}

.icon-tile {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 76rpx;
  height: 76rpx;
  margin-bottom: 12rpx;
  border-radius: 12rpx;
}

.icon-tile image {
  width: 58rpx;
  height: 58rpx;
}

.more-button {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 52rpx;
  margin-top: 18rpx;
  color: #6241a4;
  font-size: 22rpx;
  background: #f0edf8;
  border-radius: 28rpx;
}

.more-arrow {
  margin-left: 8rpx;
  font-size: 30rpx;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14rpx;
  margin-top: 22rpx;
}

.feature-icon-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 70rpx;
  height: 70rpx;
  margin-bottom: 12rpx;
  background: #fff;
  border-radius: 14rpx;
}

.feature-icon-wrap image,
.report-item image {
  width: 62rpx;
  height: 62rpx;
}

.badge {
  position: absolute;
  top: -10rpx;
  right: -12rpx;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 6rpx;
  color: #fff;
  font-size: 17rpx;
  line-height: 28rpx;
  text-align: center;
  background: #e54d42;
  border: 2rpx solid #fff;
  border-radius: 18rpx;
}

.report-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14rpx;
  margin-top: 24rpx;
}

.report-item text {
  margin-top: 12rpx;
}

.collapse-icon {
  color: #9895a3;
  font-size: 28rpx;
}

.announcement {
  padding-bottom: 22rpx;
}

.announcement-row {
  justify-content: flex-start;
  margin-top: 22rpx;
  color: #777482;
  font-size: 22rpx;
}

.announcement-dot {
  width: 10rpx;
  height: 10rpx;
  margin-right: 12rpx;
  background: #722ed1;
  border-radius: 50%;
}

.announcement-date {
  margin-left: auto;
  color: #aaa7b2;
  font-size: 19rpx;
}

.bottom-space {
  height: 150rpx;
}
</style>
