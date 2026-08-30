<template>
  <view class="page">
    <view class="search-bar">
      <view class="search-input-wrap">
        <uni-icons type="search" color="#722ed1" :size="18" />
        <input
          v-model="keyword"
          class="search-input"
          type="text"
          confirm-type="search"
          :placeholder="placeholder"
          @input="handleInput"
          @confirm="handleConfirm"
        />
        <uni-icons
          v-if="keyword"
          type="clear"
          color="#b6b0c2"
          :size="18"
          @click="clearKeyword"
        />
      </view>
      <!-- <button class="add-button" @click="handleAdd">新增</button> -->
    </view>

    <view class="result-list">
      <view
        v-for="item in visibleItems"
        :key="String(item.id || item.name)"
        class="result-item"
        @click="selectItem(item)"
      >
        <view class="result-main">
          <text class="result-name">
            <text v-if="item.parts.prefix">{{ item.parts.prefix }}</text>
            <text v-if="item.parts.match" class="match-text">{{ item.parts.match }}</text>
            <text v-if="item.parts.suffix">{{ item.parts.suffix }}</text>
          </text>
          <text v-if="item.subtitle" class="result-subtitle">{{ item.subtitle }}</text>
        </view>
        <text class="debt-text">
          欠款 ¥{{ formatAmount(getDebtAmount(item)) }}
        </text>
      </view>
    </view>

    <view v-if="!loading && !visibleItems.length" class="empty-state">
      <uni-icons type="info" color="#b6b0c2" :size="40" />
      <text>暂无数据</text>
    </view>
  </view>
</template>

<script>
import { listCustomers, listGoodsSuppliers } from '../../common/api'

const normalizeText = (value) => String(value || '').trim()

export default {
  data() {
    return {
      mode: 'supplier',
      title: '供应商查询',
      keyword: '',
      loading: false,
      items: [],
      channel: null
    }
  },
  computed: {
    placeholder() {
      return '名称 / 联系人 / 手机 / 拼音'
    },
    visibleItems() {
      const keyword = normalizeText(this.keyword)
      const items = keyword ? this.items.filter((item) => this.matchItem(item, keyword)) : this.items
      return items.map((item) => {
        const name = this.getItemName(item)
        return {
          ...item,
          name,
          subtitle: this.getItemSubtitle(item),
          parts: this.splitKeyword(name, keyword)
        }
      })
    }
  },
  onLoad(options) {
    this.mode = options && options.mode === 'customer' ? 'customer' : 'supplier'
    this.title = options && options.title
      ? String(options.title)
      : this.mode === 'customer'
        ? '客户查询'
        : '供应商查询'
    this.keyword = normalizeText(options && (options.keyword || options.selectedName))
    uni.setNavigationBarTitle({ title: this.title })
    this.channel = this.getOpenerEventChannel ? this.getOpenerEventChannel() : null
    this.loadItems()
  },
  methods: {
    async loadItems() {
      if (this.loading) return
      this.loading = true
      uni.showLoading({ title: '加载中...', mask: true })
      try {
        const data = this.mode === 'customer' ? await listCustomers() : await listGoodsSuppliers()
        const list = Array.isArray(data)
          ? data
          : Array.isArray(data && data.records)
            ? data.records
            : Array.isArray(data && data.data)
              ? data.data
              : []
        this.items = list
          .filter((item) => item && item.id !== undefined && item.id !== null)
          .map((item) => ({
            ...item,
            name: this.getItemName(item),
            subtitle: this.getItemSubtitle(item)
          }))
      } catch (error) {
        uni.showToast({
          title: error && error.message ? error.message : '加载失败',
          icon: 'none'
        })
      } finally {
        this.loading = false
        uni.hideLoading()
      }
    },
    handleInput(event) {
      this.keyword = normalizeText(event && event.detail ? event.detail.value : '')
    },
    handleConfirm() {
      const first = this.visibleItems[0]
      if (first) {
        this.selectItem(first)
        return
      }
      this.handleAdd()
    },
    clearKeyword() {
      this.keyword = ''
    },
    getItemName(item) {
      return normalizeText(item && (item.name || item.title || item.realName || item.userName))
    },
    getItemSubtitle(item) {
      const parts = []
      if (item && item.contactName) parts.push(item.contactName)
      if (item && item.mobile) parts.push(item.mobile)
      if (item && item.phone) parts.push(item.phone)
      
      return parts.filter(Boolean).join(' / ')
    },
    getDebtAmount(item) {
      const candidates = [
        item && item.payable
      ]
      const value = candidates.find((v) => v !== undefined && v !== null && v !== '')
      const amount = Number(value || 0)
      return Number.isFinite(amount) ? amount : 0
    },
    formatAmount(value) {
      const amount = Number(value || 0)
      return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
    },
    matchItem(item, keyword) {
      const fields = [
        item && item.name,
        item && item.contactName,
        item && item.mobile,
        item && item.phone,
        item && item.pyCode
      ]
      return fields.some((field) => normalizeText(field).toLowerCase().includes(keyword.toLowerCase()))
    },
    splitKeyword(text, keyword) {
      const source = normalizeText(text)
      const search = normalizeText(keyword)
      if (!search) {
        return { prefix: '', match: source, suffix: '' }
      }
      const lowerSource = source.toLowerCase()
      const lowerSearch = search.toLowerCase()
      const index = lowerSource.indexOf(lowerSearch)
      if (index < 0) {
        return { prefix: '', match: source, suffix: '' }
      }
      return {
        prefix: source.slice(0, index),
        match: source.slice(index, index + search.length),
        suffix: source.slice(index + search.length)
      }
    },
    selectItem(item) {
      const payload = {
        id: normalizeText(item && item.id),
        name: this.getItemName(item),
        raw: item,
        mode: this.mode
      }
      if (this.channel && this.channel.emit) {
        this.channel.emit('select', payload)
      }
      uni.navigateBack()
    },
    handleAdd() {
      const keyword = normalizeText(this.keyword)
      uni.navigateTo({
        url: `/pages/partner-form/index?mode=${this.mode}&name=${encodeURIComponent(keyword)}`,
        success: (res) => {
          const channel = res && res.eventChannel
          if (!channel) return
          channel.on('saved', () => {
            this.loadItems()
          })
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 18rpx 22rpx calc(24rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background: #f5f4fb;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 18rpx;
}

.search-input-wrap {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  height: 72rpx;
  padding: 0 18rpx;
  box-sizing: border-box;
  background: #fff;
  border: 1rpx solid #ded9e8;
  border-radius: 999rpx;
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  margin: 0 12rpx;
  color: #342d3f;
  font-size: 26rpx;
}

.add-button {
  flex: 0 0 auto;
  height: 72rpx;
  margin: 0;
  padding: 0 28rpx;
  color: #fff;
  font-size: 28rpx;
  line-height: 72rpx;
  background: #722ed1;
  border: 0;
  border-radius: 999rpx;
}

.add-button::after { border: 0; }

.result-list {
  overflow: hidden;
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
}

.result-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  min-height: 96rpx;
  padding: 18rpx 18rpx 18rpx 20rpx;
  box-sizing: border-box;
  border-bottom: 1rpx solid #f0edf5;
}

.result-item:last-child { border-bottom: 0; }

.result-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.result-name {
  color: #1f1f1f;
  font-size: 30rpx;
  line-height: 1.35;
}

.match-text {
  color: #1f1f1f;
  font-weight: 600;
}

.result-subtitle {
  margin-top: 8rpx;
  color: #9a95a4;
  font-size: 22rpx;
  line-height: 1.3;
}

.debt-text {
  flex: 0 0 auto;
  color: #722ed1;
  font-size: 24rpx;
  line-height: 1.4;
  white-space: nowrap;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 340rpx;
  gap: 16rpx;
  color: #aaa5b5;
  font-size: 24rpx;
}
</style>
