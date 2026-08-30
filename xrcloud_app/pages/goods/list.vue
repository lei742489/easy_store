<template>
  <view id="goods-page" class="page">
    <view id="top" class="top-area">
      <view class="filter-panel">
        <view class="quick-search-row">
          <input
            v-model="filters.searchKey"
            class="quick-search-input"
            placeholder="货品名称 / 货品代码 / 供应商"
            confirm-type="search"
            @confirm="handleSearch"
          />
          <button class="quick-search-button" @click="handleSearch">
            <uni-icons type="search" color="#fff" :size="20" />
            <text>查询</text>
          </button>
        </view>

        <view class="filter-actions">
          <button class="more-filter-button" @click="openMoreFilters">
            <text>筛选</text>
            <uni-icons type="down" color="#722ed1" :size="16" />
            <text v-if="activeFilterCount" class="filter-count">{{ activeFilterCount }}</text>
          </button>
          <view class="filter-action-right">
            <button class="add-button" @click="openCreateForm">
              <uni-icons type="plusempty" color="#fff" :size="18" />
              <text>增加</text>
            </button>
            <button class="reset-button" @click="handleReset">
              <uni-icons type="refresh" color="#666" :size="18" />
              <text>重置</text>
            </button>
          </view>
        </view>
      </view>
    </view>

    <uni-popup ref="filterPopup" type="bottom" :safe-area="true" :is-mask-click="true">
      <view class="filter-popup">
        <view class="popup-header">
          <text class="popup-title">更多筛选</text>
          <uni-icons type="closeempty" color="#999" :size="22" @click="closeMoreFilters" />
        </view>

        <view class="popup-form-item">
          <text class="popup-label">状态</text>
          <picker :range="statusOptions" range-key="label" :value="statusIndex" @change="onStatusChange">
            <view class="popup-picker">
              <text :class="{ placeholder: statusIndex === 0 }">{{ statusOptions[statusIndex].label }}</text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </view>

        <view class="popup-form-item switch-item">
          <text class="popup-label">显示零库存</text>
          <switch
            :checked="filters.showZeroStock"
            color="#722ed1"
            @change="onShowZeroStockChange"
          />
        </view>

        <view v-if="categoryPopupOptions.length > 1" class="popup-form-item">
          <text class="popup-label">更多分类</text>
          <picker
            :range="categoryPopupOptions"
            range-key="label"
            :value="moreCategoryIndex"
            @change="onMoreCategoryChange"
          >
            <view class="popup-picker">
              <text :class="{ placeholder: !moreCategoryIndex }">
                {{ categoryPopupOptions[moreCategoryIndex].label }}
              </text>
              <uni-icons type="down" color="#999" :size="16" />
            </view>
          </picker>
        </view>

        <view class="popup-actions">
          <button class="popup-cancel-button" @click="closeMoreFilters">取消</button>
          <button class="popup-confirm-button" @click="applyMoreFilters">应用筛选</button>
        </view>
      </view>
    </uni-popup>

    <view class="goods-pane" :style="{ height: `${goodsPaneHeight}px` }">
      <scroll-view
        class="category-scroll"
        scroll-y
        :show-scrollbar="false"
        :scroll-with-animation="false"
      >
        <view
          v-for="category in categories"
          :key="category.id"
          class="category-item"
          :class="{ active: String(selectedCategoryId) === String(category.id) }"
          @click="selectCategory(category)"
        >
          <view class="category-active-line" />
          <text>{{ category.title }}</text>
        </view>
      </scroll-view>

      <scroll-view
        class="goods-scroll"
        scroll-y
        :show-scrollbar="false"
        :scroll-top="goodsScrollTop"
        :lower-threshold="120"
        @scrolltolower="loadMore"
      >
        <view v-if="goodsList.length" class="goods-list">
          <uni-goods-card
            v-for="goods in goodsList"
            :key="goods.id"
            :goods="goods"
            @click="openEditForm"
          />
        </view>

        <view v-else-if="!loading" class="empty-state">
          <uni-icons type="info" color="#aaa5b5" :size="52" />
          <text>没有查询到货品</text>
        </view>

        <uni-load-more v-if="goodsList.length || loading" :status="loadStatus" />
        <view class="list-bottom-space" />
      </scroll-view>
    </view>
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../../common/auth'
import { getUserInfo, listGoods, listGoodsCategories } from '../../common/api'
import UniGoodsCard from '../../components/uni-goods-card.vue'

const createFilters = () => ({
  searchKey: '',
  status: undefined,
  showZeroStock: true
})

const flattenCategoryChildren = (items, targetId) => {
  const stack = Array.isArray(items) ? [...items] : []
  while (stack.length) {
    const node = stack.shift()
    if (!node) continue
    if (String(node.id) === String(targetId)) {
      return Array.isArray(node.children) ? node.children : []
    }
    if (Array.isArray(node.children) && node.children.length) {
      stack.push(...node.children)
    }
  }
  return []
}

export default {
  components: { UniGoodsCard },
  data() {
    return {
      user: getUser() || {},
      loading: false,
      initializing: false,
      initialized: false,
      redirecting: false,
      goodsPaneHeight: 1,
      measureTimer: null,
      current: 1,
      pageSize: 20,
      total: 0,
      goodsList: [],
      goodsScrollTop: 0,
      categories: [{ id: 0, title: '全部分类', children: [] }],
      selectedCategoryId: 0,
      filters: createFilters(),
      statusIndex: 0,
      moreCategoryIndex: 0,
      categoryPopupOptions: [],
      statusOptions: [
        { label: '全部状态', value: undefined },
        { label: '启用', value: 1 },
        { label: '停用', value: 0 }
      ]
    }
  },
  computed: {
    isRoot() {
      return Number(this.user.isRoot) === 1
    },
    hasMore() {
      return this.goodsList.length < this.total
    },
    loadStatus() {
      if (this.loading) return 'loading'
      return this.hasMore ? 'more' : 'noMore'
    },
    activeFilterCount() {
      let count = 0
      if (this.filters.status !== undefined) count += 1
      if (!this.filters.showZeroStock) count += 1
      if (this.selectedCategoryId || this.moreCategoryIndex > 0) count += 1
      return count
    }
  },
  onReady() {
    this.scheduleMeasure()
  },
  onShow() {
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    if (!this.initialized) this.initialize()
    else {
      this.scheduleMeasure()
      this.queryGoods(true)
    }
  },
  onHide() {
    this.clearMeasureTimer()
  },
  methods: {
    clearMeasureTimer() {
      if (this.measureTimer) {
        clearTimeout(this.measureTimer)
        this.measureTimer = null
      }
    },
    scheduleMeasure() {
      this.clearMeasureTimer()
      this.measureTimer = setTimeout(() => {
        this.measureTimer = null
        this.measureGoodsPane()
      }, 0)
    },
    measureGoodsPane() {
      if (this.redirecting) return
      const systemInfo = uni.getSystemInfoSync()
      try {
        uni.createSelectorQuery()
          .select('#top')
          .boundingClientRect()
          .exec((rects) => {
            const topRect = rects && rects[0]
            if (!topRect || !topRect.height) return
            const topBottom = Number(topRect.bottom || topRect.top + topRect.height)
            const viewportBottom = Number(systemInfo.windowHeight)
            const marginTop = uni.upx2px ? uni.upx2px(20) : 10
            this.goodsPaneHeight = Math.max(240, Math.floor(viewportBottom - topBottom - marginTop))
          })
      } catch (error) {
        this.goodsPaneHeight = Math.max(240, Math.floor(systemInfo.windowHeight * 0.7))
      }
    },
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    },
    async initialize() {
      if (this.initializing || this.redirecting) return
      this.initializing = true
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...currentUser }
        updateUser(this.user)
        await this.loadCategories()
        this.initialized = true
        await this.queryGoods(true, true)
        this.scheduleMeasure()
      } catch (error) {
        this.redirectToLogin()
      } finally {
        this.initializing = false
      }
    },
    async loadCategories() {
      const result = await listGoodsCategories()
      const roots = Array.isArray(result) ? result : []
      const root = roots.find((item) => Number(item.id) === 0) || roots[0] || { id: 0, title: '全部分类' }
      const firstLevel = Array.isArray(root.children) && root.children.length
        ? root.children
        : roots.filter((item) => item && Number(item.parentId || 0) <= 0 && Number(item.id) !== 0)
      const unique = []
      const seen = new Set()
      firstLevel.forEach((item) => {
        if (!item || item.id === undefined || seen.has(String(item.id))) return
        seen.add(String(item.id))
        unique.push({
          id: item.id,
          title: item.title || '未命名分类',
          children: Array.isArray(item.children) ? item.children : []
        })
      })
      this.categories = [{ id: 0, title: '全部分类', children: roots }, ...unique]
      this.refreshMoreCategoryOptions()
    },
    refreshMoreCategoryOptions() {
      const children = this.selectedCategoryId
        ? flattenCategoryChildren(this.categories, this.selectedCategoryId)
        : []
      const options = [{ value: '', label: '全部分类' }]
      ;(Array.isArray(children) ? children : []).forEach((item) => {
        if (!item || item.id === undefined || item.id === null) return
        options.push({
          value: String(item.id),
          label: item.title || '未命名分类'
        })
      })
      this.categoryPopupOptions = options
      if (this.moreCategoryIndex >= options.length) this.moreCategoryIndex = 0
    },
    buildQuery(page) {
      const params = {
        current: page,
        pageSize: this.pageSize,
        column: 'createTime',
        order: 'desc',
        title: String(this.filters.searchKey || '').trim(),
        zeroStock: Boolean(this.filters.showZeroStock),
        userId: this.user.id
      }
      if (this.moreCategoryIndex > 0 && this.categoryPopupOptions[this.moreCategoryIndex]) {
        params.categoryId = this.categoryPopupOptions[this.moreCategoryIndex].value
      } else if (this.selectedCategoryId) params.categoryId = this.selectedCategoryId
      if (this.filters.status !== undefined) params.status = this.filters.status
      return params
    },
    async queryGoods(reset = false, showLoading = false) {
      if (this.loading || this.redirecting) return
      const targetPage = reset ? 1 : this.current
      if (reset) this.resetGoodsScrollPosition()
      this.loading = true
      if (showLoading) uni.showLoading({ title: '查询中...', mask: true })
      try {
        const page = await listGoods(this.buildQuery(targetPage))
        const records = page && Array.isArray(page.records) ? page.records : []
        this.goodsList = reset ? records : this.goodsList.concat(records)
        this.current = Number(page && page.current ? page.current : targetPage)
        this.total = Number(page && page.total ? page.total : this.goodsList.length)
      } catch (error) {
        const message = error && error.message ? error.message : '货品加载失败'
        if (/登录|token|过期/.test(message)) this.redirectToLogin()
        else uni.showToast({ title: message, icon: 'none' })
      } finally {
        this.loading = false
        if (showLoading) uni.hideLoading()
      }
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.current += 1
      this.queryGoods(false)
    },
    handleSearch() {
      this.closeMoreFilters()
      this.queryGoods(true, true)
    },
    handleReset() {
      this.filters = createFilters()
      this.statusIndex = 0
      this.selectedCategoryId = 0
      this.moreCategoryIndex = 0
      this.refreshMoreCategoryOptions()
      this.closeMoreFilters()
      this.queryGoods(true, true)
    },
    resetGoodsScrollPosition() {
      this.goodsScrollTop = 1
      this.$nextTick(() => {
        this.goodsScrollTop = 0
      })
    },
    selectCategory(category) {
      this.selectedCategoryId = category.id
      this.moreCategoryIndex = 0
      this.refreshMoreCategoryOptions()
      this.queryGoods(true, true)
    },
    openCreateForm() {
      uni.navigateTo({ url: '/pages/goods/form' })
    },
    openEditForm(goods) {
      if (!goods || goods.id === undefined || goods.id === null) return
      uni.setStorageSync('easy-store-edit-goods', goods)
      uni.navigateTo({ url: `/pages/goods/form?id=${encodeURIComponent(goods.id)}` })
    },
    openMoreFilters() {
      this.refreshMoreCategoryOptions()
      this.$refs.filterPopup && this.$refs.filterPopup.open()
    },
    closeMoreFilters() {
      this.$refs.filterPopup && this.$refs.filterPopup.close()
    },
    applyMoreFilters() {
      this.closeMoreFilters()
      this.queryGoods(true, true)
    },
    onStatusChange(event) {
      this.statusIndex = Number(event.detail.value || 0)
      this.filters.status = this.statusOptions[this.statusIndex].value
    },
    onShowZeroStockChange(event) {
      this.filters.showZeroStock = Boolean(event.detail.value)
    },
    onMoreCategoryChange(event) {
      this.moreCategoryIndex = Number(event.detail.value || 0)
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 20rpx 20rpx 0;
  box-sizing: border-box;
  color: #33303f;
  background: #f5f4fb;
  overflow: hidden;
}
.top-area { flex: 0 0 auto; }
.filter-panel {
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
  padding: 20rpx 22rpx 16rpx;
}
.quick-search-row { display: flex; align-items: center; gap: 14rpx; }
.quick-search-input,
.popup-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 58rpx;
  padding: 0 20rpx;
  box-sizing: border-box;
  color: #393044;
  font-size: 25rpx;
  background: #fff;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}
.quick-search-input { flex: 1; min-width: 0; }
.quick-search-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  flex: 0 0 124rpx;
  height: 58rpx;
  margin: 0;
  padding: 0;
  color: #fff;
  font-size: 25rpx;
  line-height: 58rpx;
  background: #722ed1;
  border-radius: 10rpx;
}
.quick-search-button::after,
.reset-button::after,
.more-filter-button::after,
.add-button::after,
.popup-actions button::after { border: 0; }
.filter-actions { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; margin-top: 16rpx; }
.filter-action-right { display: flex; align-items: center; gap: 14rpx; }
.reset-button,
.more-filter-button,
.add-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 58rpx;
  margin: 0;
  padding: 0 18rpx;
  font-size: 23rpx;
  line-height: 58rpx;
  border-radius: 8rpx;
}
.reset-button { color: #666; background: #f2f3f5; }
.more-filter-button { position: relative; color: #722ed1; background: #f4efff; }
.add-button { color: #fff; background: #722ed1; }
.filter-count { display: inline-flex; align-items: center; justify-content: center; min-width: 28rpx; height: 28rpx; padding: 0 5rpx; color: #fff; font-size: 17rpx; line-height: 28rpx; background: #722ed1; border-radius: 18rpx; }
.filter-popup { padding: 28rpx 28rpx calc(22rpx + env(safe-area-inset-bottom)); background: #fff; border-radius: 28rpx 28rpx 0 0; }
.popup-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 26rpx; }
.popup-title { color: #30283d; font-size: 32rpx; font-weight: 600; }
.popup-form-item { margin-bottom: 22rpx; }
.popup-label { display: block; margin-bottom: 10rpx; color: #6b6676; font-size: 22rpx; }
.popup-picker { width: 100%; }
.placeholder { color: #aaa5b5; }
.switch-item { display: flex; align-items: center; justify-content: space-between; }
.switch-item .popup-label { margin-bottom: 0; }
.popup-actions { display: flex; gap: 18rpx; margin-top: 30rpx; }
.popup-actions button { flex: 1; height: 78rpx; margin: 0; font-size: 26rpx; line-height: 78rpx; border-radius: 10rpx; }
.popup-cancel-button { color: #666; background: #f2f3f5; }
.popup-confirm-button { color: #fff; background: #722ed1; }
.goods-pane {
  display: flex;
  flex: 0 0 auto;
  min-height: 240px;
  margin-top: 20rpx;
  overflow: hidden;
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
}
.category-scroll { flex: 0 0 188rpx; width: 188rpx; background: #faf9fc; border-right: 1rpx solid #eeeaf4; }
.category-item { position: relative; display: flex; align-items: center; min-height: 82rpx; padding: 0 18rpx; box-sizing: border-box; color: #6b6676; font-size: 23rpx; border-bottom: 1rpx solid #f0edf5; }
.category-item text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.category-item.active { color: #722ed1; font-weight: 600; background: #f1eaff; }
.category-active-line { position: absolute; left: 0; top: 0; bottom: 0; width: 6rpx; background: transparent; }
.category-item.active .category-active-line { background: #722ed1; }
.goods-scroll { flex: 1; min-width: 0; padding: 16rpx; box-sizing: border-box; }
.goods-list { display: flex; flex-direction: column; gap: 16rpx; }
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 420rpx; gap: 18rpx; color: #aaa5b5; font-size: 24rpx; }
.list-bottom-space { height: 28rpx; }
@media (max-width: 420px) {
  .category-scroll { flex-basis: 160rpx; width: 160rpx; }
}
</style>
