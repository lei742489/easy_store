<template>
  <view class="page">
    <scroll-view class="page-scroll" scroll-y :show-scrollbar="false">
      <view class="profile-header">
        <uni-status-bar />
		<view style="height: 50rpx;"></view>
        <view class="user-summary">
          <image
            class="avatar"
            :src="avatarUrl"
            mode="aspectFill"
            @click.stop="openPage('/pages/profile-center/index')"
          />
          <view class="user-copy">
            <text class="welcome">欢迎回来</text>
            <text class="user-name">{{ displayName }}</text>
            <text class="profile-role">{{ roleLabel }}</text>
            <text class="user-role">{{ isRoot ? '管理员' : '普通员工' }}</text>
          </view>
          <uni-icons
            type="right"
            color="rgba(255,255,255,.8)"
            :size="22"
            @click.stop="openPage('/pages/profile-center/index')"
          />
        </view>
      </view>

      <view class="sales-panel">
        <view class="panel-heading">
          <text class="panel-title">销售额</text>
          <text class="panel-caption">按销售单统计</text>
        </view>
        <view v-if="statisticsLoading" class="statistics-loading">
          <uni-icons type="spinner-cycle" color="#722ed1" :size="24" />
        </view>
        <view v-else class="statistics-grid">
          <view v-for="item in salesItems" :key="item.key" class="statistics-item">
            <text class="statistics-label">{{ item.label }}</text>
            <text class="statistics-value">¥{{ formatAmount(item.value) }}</text>
          </view>
        </view>
      </view>

      <view class="menu-panel">
        <view class="menu-item" @click="openPage('/pages/profile-center/index')">
          <view class="menu-icon profile-icon"><uni-icons type="person" color="#722ed1" :size="23" /></view>
          <text class="menu-name">个人中心</text>
          <uni-icons type="right" color="#b2adba" :size="19" />
        </view>
        <view class="menu-item" @click="openPage('/pages/security-settings/index')">
          <view class="menu-icon security-icon"><uni-icons type="locked" color="#1677ff" :size="23" /></view>
          <text class="menu-name">安全设置</text>
          <uni-icons type="right" color="#b2adba" :size="19" />
        </view>
        <template v-if="isRoot">
          <view class="menu-item" @click="openPage('/pages/app-user/index')">
            <view class="menu-icon user-icon"><uni-icons type="staff-filled" color="#00a870" :size="23" /></view>
            <text class="menu-name">员工管理</text>
            <uni-icons type="right" color="#b2adba" :size="19" />
          </view>
          <view class="menu-item" @click="openPage('/pages/app-unit/index')">
            <view class="menu-icon unit-icon"><uni-icons type="list" color="#e88a00" :size="23" /></view>
            <text class="menu-name">单位管理</text>
            <uni-icons type="right" color="#b2adba" :size="19" />
          </view>
          <view class="menu-item" @click="openPage('/pages/operation-log/index')">
            <view class="menu-icon log-icon"><uni-icons type="compose" color="#e5484d" :size="23" /></view>
            <text class="menu-name">操作日志</text>
            <uni-icons type="right" color="#b2adba" :size="19" />
          </view>
        </template>
        <view class="menu-divider" />
        <view class="menu-item logout-item" @click="confirmLogout">
          <view class="menu-icon logout-icon"><uni-icons type="close" color="#e5484d" :size="23" /></view>
          <text class="menu-name">安全退出</text>
          <uni-icons type="right" color="#d9a6a8" :size="19" />
        </view>
      </view>

      <text class="version">版本 v{{ appVersion }}</text>
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import { getHomeStatistics, getUserInfo, logout } from '../../common/api'
import { clearSession, getUser, isLoggedIn, updateUser } from '../../common/auth'
import { getClientVersion, getClientVersionAsync } from '../../common/config'
import { getStaticFileUrl } from '../../common/file-url'

export default {
  data() {
    return {
      user: getUser() || {},
      statistics: {},
      statisticsLoading: false,
      appVersion: getClientVersion(),
      redirecting: false
    }
  },
  computed: {
    displayName() {
      return this.user.realName || this.user.userName || '用户'
    },
    isRoot() {
      return Number(this.user.isRoot) === 1
    },
    roleLabel() {
      return this.isRoot
        ? '管理员'
        : this.user.roleId_dictText || this.user.roleName || '普通员工'
    },
    avatarUrl() {
      return getStaticFileUrl(this.user.avatar) || '/static/ico/def_head.png'
    },
    salesItems() {
      return [
        { key: 'day', label: '本日', value: this.statistics.salesAmount || 0 },
        { key: 'month', label: '本月', value: this.statistics.salesAmountMonth || 0 },
        { key: 'year', label: '本年', value: this.statistics.salesAmountYear || 0 }
      ]
    }
  },
  onShow() {
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    this.refreshAppVersion()
    this.loadPage()
  },
  methods: {
    async refreshAppVersion() {
      this.appVersion = await getClientVersionAsync()
    },
    async loadPage() {
      if (this.statisticsLoading || this.redirecting) return
      try {
        const currentUser = await getUserInfo()
        console.log(currentUser)
        this.user = { ...this.user, ...(currentUser || {}) }
        updateUser(this.user)
        this.statisticsLoading = true
        this.statistics = await getHomeStatistics({ userId: this.user.id }) || {}
      } catch (error) {
        this.handleError(error)
      } finally {
        this.statisticsLoading = false
      }
    },
    formatAmount(value) {
      const number = Number(value || 0)
      return Number.isFinite(number)
        ? number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
        : '0.00'
    },
    openPage(url) {
      uni.navigateTo({ url })
    },
    confirmLogout() {
      uni.showModal({
        title: '安全退出',
        content: '确定退出当前账号吗？',
        confirmText: '退出',
        confirmColor: '#e5484d',
        success: async (result) => {
          if (!result.confirm || this.redirecting) return
          this.redirecting = true
          try {
            await logout({ userId: this.user.id })
          } catch (error) {
            // 即使服务端退出失败，也要清理本地会话。
          } finally {
            clearSession()
            uni.reLaunch({ url: '/pages/login/index' })
          }
        }
      })
    },
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    },
    handleError(error) {
      const message = error && error.message ? error.message : '页面加载失败'
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
.page { min-height: 100vh; color: #30283d; background: #f5f4fb; }
.page-scroll { height: 100vh; box-sizing: border-box; }
.profile-header { padding: 24rpx 30rpx 32rpx; box-sizing: border-box; color: #fff; background: linear-gradient(135deg, #4a238d 0%, #722ed1 62%, #8e5de8 100%); border-radius: 0 0 34rpx 34rpx; }
.header-title { margin-top: 20rpx; font-size: 38rpx; font-weight: 700; }
.user-summary { display: flex; align-items: center; margin-top: 34rpx; }
.avatar { flex: 0 0 112rpx; width: 112rpx; height: 112rpx; border: 6rpx solid rgba(255,255,255,.72); border-radius: 50%; background: #fff; }
.user-copy { display: flex; flex: 1; flex-direction: column; min-width: 0; margin-left: 22rpx; }
.welcome { color: rgba(255,255,255,.76); font-size: 23rpx; }
.user-name { margin-top: 5rpx; overflow: hidden; font-size: 36rpx; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.user-role { margin-top: 8rpx; color: rgba(255,255,255,.82); font-size: 22rpx; }
.sales-panel, .menu-panel { margin: 20rpx 22rpx 0; padding: 22rpx; box-sizing: border-box; background: #fff; border: 1rpx solid #ece9f2; border-radius: 18rpx; box-shadow: 0 7rpx 22rpx rgba(67,47,119,.07); }
.panel-heading { display: flex; align-items: baseline; justify-content: space-between; }
.panel-title { color: #30283d; font-size: 29rpx; font-weight: 700; }
.panel-caption { color: #aaa5b5; font-size: 20rpx; }
.statistics-grid { display: flex; margin-top: 24rpx; }
.statistics-item { flex: 1; min-width: 0; padding: 0 14rpx; border-right: 1rpx solid #f0edf5; }
.statistics-item:first-child { padding-left: 0; }
.statistics-item:last-child { padding-right: 0; border-right: 0; }
.statistics-label { display: block; color: #918b99; font-size: 22rpx; }
.statistics-value { display: block; margin-top: 10rpx; overflow: hidden; color: #722ed1; font-size: 27rpx; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.statistics-loading { display: flex; align-items: center; justify-content: center; height: 86rpx; }
.menu-panel { padding: 0 22rpx; }
.menu-item { display: flex; align-items: center; min-height: 100rpx; border-bottom: 1rpx solid #f0edf5; }
.menu-item:last-child { border-bottom: 0; }
.menu-icon { display: flex; align-items: center; justify-content: center; flex: 0 0 58rpx; width: 58rpx; height: 58rpx; margin-right: 18rpx; border-radius: 16rpx; }
.profile-icon { background: #f4efff; }
.security-icon { background: #eaf3ff; }
.user-icon { background: #e8f8f2; }
.unit-icon { background: #fff5df; }
.log-icon, .logout-icon { background: #fff0f0; }
.menu-name { flex: 1; color: #454252; font-size: 27rpx; }
.menu-divider { height: 16rpx; margin: 0 -22rpx; background: #f5f4fb; border-top: 1rpx solid #f0edf5; border-bottom: 1rpx solid #f0edf5; }
.logout-item .menu-name { color: #e5484d; }
.version { display: block; margin-top: 28rpx; color: #aaa5b5; font-size: 22rpx; text-align: center; }
.bottom-space { height: 34rpx; }
.user-role { display: none; }
.profile-role { margin-top: 8rpx; color: rgba(255,255,255,.82); font-size: 22rpx; }
</style>
