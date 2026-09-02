<template>
  <view class="page">
    <view class="filter-panel">
      <view class="quick-search-row">
        <input
          v-model="keyword"
          class="quick-search-input"
          placeholder="请输入账户名称"
          confirm-type="search"
          @confirm="handleSearch"
        />
        <button class="quick-search-button" @click="handleSearch">
          <uni-icons type="search" color="#fff" :size="20" />
          <text>查询</text>
        </button>
      </view>
      <view class="filter-actions">
        <button class="add-button" @click="openCreateForm">
          <uni-icons type="plusempty" color="#fff" :size="17" />
          <text>新增</text>
        </button>
        <button class="reset-button" @click="handleReset">
          <uni-icons type="refresh" color="#666" :size="18" />
          <text>重置</text>
        </button>
      </view>
    </view>

    <view class="list-summary">
      <text>结算账户</text>
      <text>共 {{ total }} 条</text>
    </view>

    <view v-if="records.length" class="account-list">
      <view
        v-for="item in records"
        :key="item.id"
        class="account-card"
        @click="openEditForm(item)"
      >
        <view class="card-header">
          <view class="account-main">
            <view class="account-icon">
              <uni-icons type="wallet" color="#722ed1" :size="24" />
            </view>
            <view class="account-title-wrap">
              <text class="account-name">{{ item.name || '-' }}</text>
              <text class="account-type">{{ getTypeName(item) }}</text>
            </view>
          </view>
          <view class="balance-box">
            <text class="balance-label">当前余额</text>
            <text class="balance-value" :class="{ negative: toNumber(item.curPrc) < 0 }">
              ¥{{ formatAmount(item.curPrc) }}
            </text>
          </view>
        </view>

        <view class="detail-grid">
          <view class="detail-item">
            <text class="detail-label">银行名称</text>
            <text class="detail-value">{{ item.bankName || '-' }}</text>
          </view>
          <view class="detail-item">
            <text class="detail-label">银行卡号</text>
            <text class="detail-value">{{ item.bankCard || '-' }}</text>
          </view>
          <view class="detail-item">
            <text class="detail-label">初始余额</text>
            <text class="detail-value">¥{{ formatAmount(item.initPrc) }}</text>
          </view>
        </view>

        <view v-if="item.note" class="note-row">
          <text class="note-label">备注</text>
          <text class="note-text">{{ item.note }}</text>
        </view>

        <view class="card-footer">
          <text>点击编辑账户</text>
          <uni-icons type="right" color="#aaa5b5" :size="17" />
        </view>
      </view>
    </view>

    <view v-else-if="!loading" class="empty-state">
      <uni-icons type="info" color="#aaa5b5" :size="52" />
      <text>暂无结算账户</text>
    </view>

    <uni-load-more v-if="records.length || loading" :status="loadStatus" />
    <view class="bottom-space" />
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import {
  getUserInfo,
  listAccountSettlePage
} from '../../common/api'

export default {
  data() {
    return {
      user: getUser() || {},
      keyword: '',
      records: [],
      current: 1,
      pageSize: 20,
      total: 0,
      loading: false,
      initialized: false,
      initializing: false,
      redirecting: false
    }
  },
  computed: {
    hasMore() {
      return this.records.length < this.total
    },
    loadStatus() {
      if (this.loading) return 'loading'
      return this.hasMore ? 'more' : 'noMore'
    }
  },
  onShow() {
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    if (!this.initialized) this.initialize()
    else this.queryAccounts(true)
  },
  onReachBottom() {
    this.loadMore()
  },
  onPullDownRefresh() {
    this.queryAccounts(true).finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    async initialize() {
      if (this.initializing || this.redirecting) return
      this.initializing = true
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...currentUser }
        updateUser(this.user)
        this.initialized = true
        await this.queryAccounts(true)
      } catch (error) {
        this.handleError(error, '结算账户加载失败')
      } finally {
        this.initializing = false
      }
    },
    buildQuery(current) {
      const params = {
        current,
        pageSize: this.pageSize,
        column: 'id',
        order: 'desc',
        userId: this.user.id
      }
      const value = String(this.keyword || '').trim()
      if (value) params.name = value
      return params
    },
    async queryAccounts(reset = false, pageNumber) {
      if (this.loading || this.redirecting) return
      const targetPage = reset ? 1 : (pageNumber || this.current)
      this.loading = true
      try {
        const page = await listAccountSettlePage(this.buildQuery(targetPage))
        const list = page && Array.isArray(page.records) ? page.records : []
        this.records = reset ? list : this.records.concat(list)
        this.current = Number(page && page.current ? page.current : targetPage)
        this.total = Number(page && page.total ? page.total : this.records.length)
      } catch (error) {
        this.handleError(error, '结算账户加载失败')
      } finally {
        this.loading = false
      }
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.queryAccounts(false, this.current + 1)
    },
    handleSearch() {
      this.queryAccounts(true)
    },
    handleReset() {
      this.keyword = ''
      this.queryAccounts(true)
    },
    openCreateForm() {
      this.navigateToForm()
    },
    openEditForm(item) {
      if (!item || item.id === undefined || item.id === null) return
      this.navigateToForm(String(item.id), item)
    },
    navigateToForm(id, item) {
      if (item) uni.setStorageSync('easy-store-edit-account-settle', item)
      const query = id ? `?id=${encodeURIComponent(id)}` : ''
      uni.navigateTo({
        url: `/pages/account-settle/form${query}`,
        success: (res) => {
          const channel = res && res.eventChannel
          if (channel) channel.on('saved', () => this.queryAccounts(true))
        }
      })
    },
    getTypeName(item) {
      return item && (item.typeId_dictText || item.typeName) || '未分类'
    },
    toNumber(value) {
      const number = Number(value || 0)
      return Number.isFinite(number) ? number : 0
    },
    formatAmount(value) {
      return this.toNumber(value).toFixed(2)
    },
    handleError(error, fallback) {
      const message = error && error.message ? error.message : fallback
      if (/登录|token|过期/i.test(message)) {
        this.redirectToLogin()
        return
      }
      uni.showToast({ title: message, icon: 'none' })
    },
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 22rpx;
  box-sizing: border-box;
  color: #33303f;
  background: #f5f4fb;
}

.filter-panel,
.account-card {
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
}

.filter-panel { padding: 20rpx 22rpx 16rpx; }
.quick-search-row,
.filter-actions,
.card-header,
.account-main,
.card-footer {
  display: flex;
  align-items: center;
}

.quick-search-row { gap: 14rpx; }
.quick-search-input {
  flex: 1;
  min-width: 0;
  height: 58rpx;
  padding: 0 20rpx;
  box-sizing: border-box;
  color: #393044;
  font-size: 25rpx;
  background: #fff;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}

.quick-search-button,
.add-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  flex: 0 0 124rpx;
  height: 58rpx;
  margin: 0;
  padding: 0;
  color: #fff;
  font-size: 24rpx;
  line-height: 58rpx;
  background: #722ed1;
  border-radius: 10rpx;
}

.filter-actions {
  justify-content: flex-end;
  gap: 14rpx;
  margin-top: 16rpx;
}

.reset-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  flex: 0 0 124rpx;
  height: 58rpx;
  margin: 0;
  padding: 0;
  color: #666;
  font-size: 24rpx;
  line-height: 58rpx;
  background: #f2f3f5;
  border-radius: 10rpx;
}

.quick-search-button::after,
.add-button::after,
.reset-button::after { border: 0; }

.list-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 6rpx 16rpx;
  color: #777482;
  font-size: 23rpx;
}

.list-summary text:first-child {
  color: #454252;
  font-size: 29rpx;
  font-weight: 600;
}

.account-card {
  margin-bottom: 18rpx;
  padding: 22rpx;
}

.card-header { justify-content: space-between; gap: 16rpx; }
.account-main { flex: 1; min-width: 0; gap: 14rpx; }
.account-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 62rpx;
  width: 62rpx;
  height: 62rpx;
  background: #f4efff;
  border-radius: 14rpx;
}

.account-title-wrap {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.account-name {
  overflow: hidden;
  color: #30283d;
  font-size: 29rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-type {
  margin-top: 6rpx;
  color: #8d8797;
  font-size: 21rpx;
}

.balance-box { flex: 0 0 auto; text-align: right; }
.balance-label,
.detail-label {
  display: block;
  color: #9a95a4;
  font-size: 20rpx;
}

.balance-value {
  display: block;
  margin-top: 5rpx;
  color: #722ed1;
  font-size: 29rpx;
  font-weight: 600;
}

.balance-value.negative { color: #e5484d; }
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14rpx;
  margin-top: 20rpx;
  padding: 18rpx;
  background: #faf9fc;
  border-radius: 12rpx;
}

.detail-item:last-child { grid-column: 1 / -1; }
.detail-value {
  display: block;
  margin-top: 6rpx;
  overflow: hidden;
  color: #4d4858;
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-row {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
  color: #686171;
  font-size: 22rpx;
}

.note-label { flex: 0 0 62rpx; color: #918b99; }
.note-text { flex: 1; word-break: break-all; }
.card-footer {
  justify-content: space-between;
  margin-top: 18rpx;
  padding-top: 15rpx;
  color: #9a95a4;
  font-size: 20rpx;
  border-top: 1rpx solid #eeeaf4;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 500rpx;
  gap: 18rpx;
  color: #aaa5b5;
  font-size: 24rpx;
}

.bottom-space { height: 30rpx; }
</style>
