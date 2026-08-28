<template>
  <view class="page">
    <view id="top" class="hero">
      <uni-status-bar />
      <view class="hero-top">
        <view class="brand">
          <image class="brand-logo" src="/static/logo-big.png" mode="aspectFit" />
          <text class="brand-name">新锐云</text>
        </view>
        <uni-icons type="search" :size="24" color="#fff" />
      </view>
      <view class="welcome-row">
        <text class="welcome">欢迎回来！{{ displayName }}</text>
        <text v-if="isRoot" class="root-label">老板模式</text>
		<text v-else class="root-label">员工模式</text>
      </view>
    </view>

    <scroll-view class="content" scroll-y :show-scrollbar="false" :style="{ height: `${contentHeight}px` }">
      <view class="section announcement">
        <view class="section-title-row"><text class="section-title">通知公告</text></view>
        <view class="announcement-row"><view class="announcement-dot" /><text>欢迎使用 新锐云 进销存管理系统</text></view>
      </view>

      <view v-if="loading" class="loading-state">
        <uni-icons type="spinner-cycle" color="#722ed1" :size="30" />
        <text>正在加载菜单...</text>
      </view>
      <view v-else-if="menuGroups.length" class="menu-sections">
        <view v-for="group in menuGroups" :key="group.code" class="section">
          <view class="section-title-row"><text class="section-title">{{ group.title }}</text></view>
          <view class="menu-grid">
            <view
              v-for="item in group.menus"
              :key="item.id || item.code"
              class="menu-card"
              @click="handleMenu(item)"
            >
              <view class="feature-icon-wrap">
                <image v-if="isImageIcon(item)" :src="getImageIcon(item)" mode="aspectFit" />
                <uni-icons
                  v-else
                  :type="getUniIcon(item)"
                  color="#722ed1"
                  :size="42"
                />
                <text v-if="getPendingCount(item)" class="badge">
                  {{ getPendingCount(item) > 99 ? '99+' : getPendingCount(item) }}
                </text>
              </view>
              <text>{{ item.name }}</text>
            </view>
          </view>
        </view>
      </view>
      <view v-else class="empty-state">
        <uni-icons type="info" color="#aaa5b5" :size="52" />
        <text>暂无可用功能</text>
      </view>
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import { getUserInfo, listHomeMenus, pendingApproveCounts } from '../../common/api'

const iconMap = {
  sale_order_add: 'sm1', receive_payment_add: 'sm2', purchase_order_add: 'sm3', payment_add: 'sm4',
  goods: 'sm5', customer: 'sm6', supplier: 'sm7', customer_quote: 'sm8', account_settle: 'sm9',
  sale_order_list: 'dm1', receive_payment_list: 'sfc2', sale_stats: 'sfc3', customer_statement: 'sfc4',
  debt_stats: 'dm3', debt_detail: 'dm5', purchase_order_list: 'jm1', payment_list: 'jm2', purchase_stats: 'jm3',
  payable_order: 'jm4', payable_stats: 'jm5', payable_detail: 'dm5', stock_stats: 'cc1', stock_warning: 'cc2',
  stock_check: 'cc3', fund_stats: 'tj1', profit_stats: 'tj2', cashier_stats: 'tj3'
}

const uniIconMap = {
  'icon-user-group': 'staff-filled',
  'icon-safe': 'auth-filled',
  'icon-storage': 'settings-filled',
  'icon-book': 'compose',
  'icon-history': 'list'
}

const appPageMap = {
  sale_order_list: '/pages/sale-order/list',
  goods: '/pages/goods/list'
}

export default {
  data() {
    return {
      contentHeight: 1,
      loading: false,
      redirecting: false,
      pageAlive: false,
      measureTimer: null,
      loadRequestId: 0,
      menuGroups: [],
      pendingCounts: {},
      user: getUser() || {}
    }
  },
  computed: {
    displayName() { return this.user.realName || this.user.userName || '用户' },
    isRoot() { return Number(this.user.isRoot) === 1 }
  },
  onReady() {
    this.pageAlive = true
    this.scheduleContentHeight()
  },
  onShow() {
    this.pageAlive = true
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    this.loadHome()
  },
  onHide() {
    this.pageAlive = false
    this.clearMeasureTimer()
  },
  onUnload() {
    this.pageAlive = false
    this.redirecting = true
    this.clearMeasureTimer()
  },
  methods: {
    clearMeasureTimer() {
      if (this.measureTimer) {
        clearTimeout(this.measureTimer)
        this.measureTimer = null
      }
    },
    scheduleContentHeight() {
      this.clearMeasureTimer()
      this.measureTimer = setTimeout(() => {
        this.measureTimer = null
        this.updateContentHeight()
      }, 0)
    },
    updateContentHeight() {
      // 只在首次渲染完成后测量，避免 onShow 时查询已销毁的页面节点。
      if (this.redirecting || !this.pageAlive) return
      const systemInfo = uni.getSystemInfoSync()
      try {
        uni.createSelectorQuery()
          .select('#top')
          .boundingClientRect((rect) => {
            if (this.redirecting || !this.pageAlive || !rect || !rect.height) return
            this.contentHeight = Math.max(
              1,
              Math.floor(systemInfo.windowHeight - rect.height)
            )
          })
          .exec()
      } catch (error) {
        // 页面切换期间节点可能已经销毁，保留默认高度即可。
      }
    },
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      this.pageAlive = false
      this.clearMeasureTimer()
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    },
    async loadHome() {
      if (this.loading || this.redirecting) return
      if (!isLoggedIn()) { this.redirectToLogin(); return }
      const requestId = ++this.loadRequestId
      this.loading = true
      try {
        const currentUser = await getUserInfo()
        if (!this.pageAlive || requestId !== this.loadRequestId) return
        this.user = { ...this.user, ...currentUser }
        updateUser(this.user)
        const requests = [listHomeMenus()]
        if (Number(this.user.isRoot) === 1) requests.push(pendingApproveCounts())
        const results = await Promise.all(requests)
        if (!this.pageAlive || requestId !== this.loadRequestId) return
        this.menuGroups = Array.isArray(results[0]) ? results[0] : []
        this.pendingCounts = results[1] || {}
      } catch (error) {
        if (this.redirecting || !this.pageAlive || requestId !== this.loadRequestId) return
        clearSession()
        this.redirecting = true
        uni.showToast({ title: '登录已失效，请重新登录', icon: 'none' })
        uni.reLaunch({ url: '/pages/login/index' })
      } finally {
        this.loading = false
      }
    },
    isImageIcon(item) {
      return Boolean(item && item.icon && /\.png$/i.test(item.icon))
    },
    getImageIcon(item) {
      if (this.isImageIcon(item)) return `/static/ico/${item.icon}`
      return `/static/ico/${iconMap[item && item.code] || 'sm5'}.png`
    },
    getUniIcon(item) {
      return uniIconMap[item && item.icon] || 'list'
    },
    getPendingCount(item) { return this.isRoot && item ? Number(this.pendingCounts[item.code] || 0) : 0 },
    handleMenu(item) {
      if (!item) return
      const appPage = appPageMap[item.code]
      if (appPage) {
        uni.navigateTo({ url: appPage })
        return
      }
      if (item.url) uni.showToast({ title: `${item.name}将在后续版本接入`, icon: 'none' })
    }
  }
}
</script>

<style lang="scss" scoped>
.page { min-height: 100%; color: #303044; background: #f5f4fb; }
.hero { padding: 30rpx 30rpx 28rpx; box-sizing: border-box; color: #fff; background: linear-gradient(135deg, #4a238d 0%, #722ed1 65%, #8e5de8 100%); border-radius: 0 0 34rpx 34rpx; }
.hero-top, .welcome-row, .section-title-row, .announcement-row { display: flex; align-items: center; justify-content: space-between; }
.brand, .hero-top { display: flex; align-items: center; }
.brand-logo { width: 72rpx; height: 72rpx; margin-right: 14rpx; padding: 7rpx; box-sizing: border-box; background: #fff; border-radius: 15rpx; }
.brand-name { font-size: 32rpx; font-weight: 600; }
.welcome-row { margin-top: 30rpx; }
.welcome { font-size: 30rpx; font-weight: 600; }
.root-label { padding: 6rpx 14rpx; font-size: 19rpx; border: 1rpx solid rgba(255, 255, 255, .65); border-radius: 20rpx; }
.content { padding: 22rpx 22rpx 0; box-sizing: border-box; }
.section { margin-bottom: 20rpx; padding: 24rpx 22rpx 20rpx; background: #fff; border-radius: 18rpx; box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .05); }
.section-title { color: #454252; font-size: 28rpx; font-weight: 600; }
.menu-grid { display: flex; flex-wrap: wrap; gap: 30rpx; margin-top: 20rpx; justify-content: flex-start; }
.menu-card { position: relative; display: flex; flex: 0 0 142rpx; flex-direction: column; align-items: center; justify-content: center; height: 142rpx; box-sizing: border-box; color: #5a5865; font-size: 23rpx; text-align: center; background: #f7f7fa; border: 1rpx solid #f0eef5; border-radius: 14rpx; box-shadow: 0 5rpx 12rpx rgba(67, 47, 119, .08); }
.menu-card:active { background: #f0edf8; box-shadow: 0 2rpx 6rpx rgba(67, 47, 119, .12); }
.menu-card > text { display: block; width: 100%; padding: 0 6rpx; box-sizing: border-box; overflow: hidden; line-height: 30rpx; text-overflow: ellipsis; white-space: nowrap; }
.feature-icon-wrap { position: relative; display: flex; align-items: center; justify-content: center; width: 70rpx; height: 70rpx; margin-bottom: 12rpx; background: #fff; border-radius: 14rpx; }
.feature-icon-wrap image { width: 62rpx; height: 62rpx; }
.badge { position: absolute; top: -10rpx; right: -14rpx; min-width: 28rpx; height: 28rpx; padding: 0 6rpx; color: #fff; font-size: 17rpx; line-height: 28rpx; text-align: center; background: #e54d42; border: 2rpx solid #fff; border-radius: 18rpx; }
.announcement-row { justify-content: flex-start; margin-top: 22rpx; color: #777482; font-size: 22rpx; }
.announcement-dot { width: 10rpx; height: 10rpx; margin-right: 12rpx; background: #722ed1; border-radius: 50%; }
.loading-state, .empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 420rpx; color: #9895a3; font-size: 24rpx; gap: 18rpx; }
.bottom-space { height: 20rpx; }
</style>
