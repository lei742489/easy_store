<template>
  <view class="page">
    <view id="goods-select-top" class="top-area">
      <view class="filter-panel">
        <view class="quick-search-row">
          <input
            v-model="keyword"
            class="quick-search-input"
            placeholder="货品名称 / 货品代码 / 拼音"
            confirm-type="search"
            @confirm="handleSearch"
          />
          <button class="quick-search-button" @click="handleSearch">
            <uni-icons type="search" color="#fff" :size="20" />
            <text>查询</text>
          </button>
        </view>
      </view>
    </view>

    <view class="goods-pane" :style="{ height: `${goodsPaneHeight}px` }">
      <!--
      <scroll-view
        class="category-scroll"
        scroll-y
        :show-scrollbar="false"
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
      -->

      <scroll-view
        class="goods-scroll"
        scroll-y
        :show-scrollbar="false"
        :scroll-top="goodsScrollTop"
        :lower-threshold="120"
        @scrolltolower="loadMore"
      >
        <view v-if="goodsList.length" class="goods-list">
          <view
            v-for="(goods, index) in goodsList"
            :key="goods.id || index"
            class="goods-card-wrap"
          >
            <view class="goods-card-click-area" @click="selectAndReturn(goods)">
              <uni-goods-card :goods="goods" />
            </view>
            <view
              class="add-goods-button"
              :class="{ selected: selectedQuantity(goods.id) > 0 }"
              @click.stop="addToCart(goods)"
            >
              <text v-if="selectedQuantity(goods.id) > 0" class="selected-count">
                {{ formatQuantity(selectedQuantity(goods.id)) }}
              </text>
              <uni-icons type="plusempty" color="#fff" :size="22" />
            </view>
          </view>
        </view>

        <view v-else-if="!loading" class="empty-state">
          <uni-icons type="info" color="#aaa5b5" :size="52" />
          <text>没有查询到货品</text>
        </view>

        <uni-load-more v-if="goodsList.length || loading" :status="loadStatus" />
        <view class="list-bottom-space" />
      </scroll-view>
    </view>

    <view id="cart-bar" class="cart-bar">
      <view class="cart-summary">
        <view class="cart-total">
          <text class="cart-label">合计</text>
          <text class="cart-amount">￥{{ formatMoney(selectedTotal) }}</text>
        </view>
        <view class="cart-count">
          <text>{{ selectedKinds }}种商品</text>
          <text>{{ formatQuantity(selectedUnits) }}件</text>
        </view>
      </view>
      <button
        class="next-button"
        :disabled="!cartItems.length"
        @click="confirmSelection"
      >
        下一步
      </button>
    </view>
  </view>
</template>

<script>
import { getUserInfo, listGoods, listGoodsCategories } from '../../common/api'
import { clearSession, getUser, isLoggedIn, updateUser } from '../../common/auth'
import UniGoodsCard from '../../components/uni-goods-card.vue'

const toNumber = (value, fallback = 0) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

export default {
  components: { UniGoodsCard },
  data() {
    return {
      user: getUser() || {},
      keyword: '',
      storageKey: '',
      selectType: 'sale',
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
      cartItems: [],
      requestId: 0,
      channel: null,
      returning: false,
      hasReturned: false
    }
  },
  computed: {
    hasMore() {
      return this.goodsList.length < this.total
    },
    loadStatus() {
      if (this.loading) return 'loading'
      return this.hasMore ? 'more' : 'noMore'
    },
    selectedKinds() {
      return this.cartItems.length
    },
    selectedUnits() {
      return this.cartItems.reduce(
        (total, item) => total + Math.max(0, toNumber(item.quantity, 0)),
        0
      )
    },
    selectedTotal() {
      return this.cartItems.reduce(
        (total, item) =>
          total + Math.max(0, toNumber(item.quantity, 0)) * toNumber(item.unitPrice),
        0
      )
    }
  },
  onLoad(options) {
    this.storageKey = options && options.storageKey
      ? decodeURIComponent(String(options.storageKey))
      : ''
    this.selectType = options && options.type === 'purchase'
      ? 'purchase'
      : options && options.type === 'stockCheck'
        ? 'stockCheck'
        : 'sale'
    this.channel = this.getOpenerEventChannel ? this.getOpenerEventChannel() : null
    this.restoreCart()
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
    else this.scheduleMeasure()
  },
  onHide() {
    this.clearMeasureTimer()
  },
  onUnload() {
    this.clearMeasureTimer()
  },
  onPullDownRefresh() {
    this.queryGoods(true).finally(() => uni.stopPullDownRefresh())
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
          .select('#goods-select-top')
          .boundingClientRect()
          .select('#cart-bar')
          .boundingClientRect()
          .exec((rects) => {
            const topRect = rects && rects[0]
            const cartRect = rects && rects[1]
            if (!topRect || !cartRect) return
            const topBottom = Number(topRect.bottom || topRect.top + topRect.height)
            const cartHeight = Number(cartRect.height || 0)
            const marginTop = uni.upx2px ? uni.upx2px(20) : 10
            const marginBottom = uni.upx2px ? uni.upx2px(16) : 8
            this.goodsPaneHeight = Math.max(
              240,
              Math.floor(systemInfo.windowHeight - topBottom - cartHeight - marginTop - marginBottom)
            )
          })
      } catch (error) {
        this.goodsPaneHeight = Math.max(240, Math.floor(systemInfo.windowHeight * 0.65))
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
        //await this.loadCategories()
        this.initialized = true
        await this.queryGoods(true, true)
        this.scheduleMeasure()
      } catch (error) {
        const message = error && error.message ? error.message : '货品加载失败'
        if (String(message).toLowerCase().includes('token')) this.redirectToLogin()
        else uni.showToast({ title: message, icon: 'none' })
      } finally {
        this.initializing = false
      }
    },
    restoreCart() {
      if (!this.storageKey) return
      const stored = uni.getStorageSync(this.storageKey)
      if (Array.isArray(stored)) {
        this.cartItems = stored
          .filter((item) => item && item.goodsId !== undefined && item.goodsId !== null)
          .map((item, index) => ({
            ...item,
            goodsId: String(item.goodsId),
            goodsName: item.goodsName || item.goodsId_dictText || item.title || '',
            quantity: Math.max(
              0,
              toNumber(item.displayQuantity !== undefined ? item.displayQuantity : item.quantity, 1)
            ),
            displayQuantity: Math.max(
              0,
              toNumber(item.displayQuantity !== undefined ? item.displayQuantity : item.quantity, 1)
            ),
            unitPrice: toNumber(item.unitPrice)
          }))
      }
      uni.removeStorageSync(this.storageKey)
    },
    async loadCategories() {
      const result = await listGoodsCategories()
      const roots = Array.isArray(result) ? result : []
      const root = roots.find((item) => Number(item.id) === 0) || roots[0] || {
        id: 0,
        title: '全部分类'
      }
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
    },
    buildQuery(page) {
      const params = {
        current: page,
        pageNo: page,
        pageSize: this.pageSize,
        column: 'createTime',
        order: 'desc',
        title: String(this.keyword || '').trim(),
        zeroStock: this.selectType === 'stockCheck',
        userId: this.user.id
      }
      if (this.selectedCategoryId) params.categoryId = this.selectedCategoryId
      return params
    },
    async queryGoods(reset = false, showLoading = false) {
      if (this.loading || this.redirecting) return
      const targetPage = reset ? 1 : this.current + 1
      const requestId = ++this.requestId
      this.loading = true
      if (reset) this.resetGoodsScrollPosition()
      if (showLoading) uni.showLoading({ title: '查询中...', mask: true })
      try {
        const page = await listGoods(this.buildQuery(targetPage))
        if (requestId !== this.requestId || this.redirecting) return
        const records = page && Array.isArray(page.records) ? page.records : []
        this.goodsList = reset ? records : this.goodsList.concat(records)
        this.current = Number(page && page.current ? page.current : targetPage)
        this.total = Number(page && page.total ? page.total : this.goodsList.length)
      } catch (error) {
        if (requestId !== this.requestId || this.redirecting) return
        const message = error && error.message ? error.message : '货品加载失败'
        if (String(message).toLowerCase().includes('token')) this.redirectToLogin()
        else uni.showToast({ title: message, icon: 'none' })
      } finally {
        if (requestId === this.requestId) this.loading = false
        if (showLoading) uni.hideLoading()
      }
    },
    handleSearch() {
      this.queryGoods(true, true)
    },
    loadMore() {
      if (this.loading || !this.hasMore) return
      this.queryGoods(false)
    },
    selectCategory(category) {
      this.selectedCategoryId = category.id
      this.queryGoods(true, true)
    },
    resetGoodsScrollPosition() {
      this.goodsScrollTop = 1
      this.$nextTick(() => {
        this.goodsScrollTop = 0
      })
    },
    selectedQuantity(goodsId) {
      const item = this.cartItems.find((current) => String(current.goodsId) === String(goodsId))
      return item ? toNumber(item.quantity) : 0
    },
    normalizeGoods(goods) {
      const unitPrice = this.selectType === 'purchase'
        ? toNumber(goods.purPrc ?? goods.tradePrc ?? goods.salePrc)
        : this.selectType === 'stockCheck'
          ? toNumber(goods.costPrice)
          : toNumber(goods.salePrc ?? goods.tradePrc ?? goods.purPrc)
      return {
        goodsId: String(goods.id),
        goodsCode: goods.goodsCode,
        goodsName: goods.title || goods.goodsName || '',
        categoryId: goods.categoryId,
        categoryId_dictText: goods.categoryId_dictText || goods.categoryName || '',
        unit: goods.unit || '',
        stock: goods.stock,
        unitPrice,
        quantity: 1,
        bookQuantity: this.selectType === 'stockCheck' ? toNumber(goods.stock) : undefined,
        actualQuantity: this.selectType === 'stockCheck' ? toNumber(goods.stock) : undefined,
        totalAmount: unitPrice
      }
    },
    addToCart(goods) {
      if (!goods || goods.id === undefined || goods.id === null) return
      const item = this.cartItems.find((current) => String(current.goodsId) === String(goods.id))
      if (item) {
        if (this.selectType === 'stockCheck') return
        item.quantity = toNumber(item.quantity) + 1
        item.displayQuantity = item.quantity
        item.totalAmount = item.quantity * toNumber(item.unitPrice)
        return
      }
      this.cartItems.push(this.normalizeGoods(goods))
    },
    selectAndReturn(goods) {
      if (this.returning || this.hasReturned) return
      this.returning = true
      this.addToCart(goods)
      this.confirmSelection()
    },
    confirmSelection() {
      if (this.hasReturned) return
      if (!this.cartItems.length) {
        uni.showToast({ title: '请先选择货品', icon: 'none' })
        return
      }
      this.returning = true
      this.hasReturned = true
      if (this.channel && this.channel.emit) {
        this.channel.emit('selected', this.cartItems)
      }
      uni.navigateBack()
    },
    formatQuantity(value) {
      const number = toNumber(value)
      return Number.isInteger(number) ? String(number) : number.toFixed(2)
    },
    formatMoney(value) {
      return toNumber(value).toFixed(2)
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
  padding: 20rpx 22rpx 16rpx;
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
}
.quick-search-row { display: flex; align-items: center; gap: 14rpx; }
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
.quick-search-button::after { border: 0; }
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
.category-scroll {
  flex: 0 0 188rpx;
  width: 188rpx;
  background: #faf9fc;
  border-right: 1rpx solid #eeeaf4;
}
.category-item {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 82rpx;
  padding: 0 18rpx;
  box-sizing: border-box;
  color: #6b6676;
  font-size: 23rpx;
  border-bottom: 1rpx solid #f0edf5;
}
.category-item text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.category-item.active { color: #722ed1; font-weight: 600; background: #f1eaff; }
.category-active-line {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6rpx;
  background: transparent;
}
.category-item.active .category-active-line { background: #722ed1; }
.goods-scroll { flex: 1; min-width: 0; padding: 16rpx; box-sizing: border-box; }
.goods-list { display: flex; flex-direction: column; gap: 16rpx; }
.goods-card-wrap { position: relative; }
.add-goods-button {
  position: absolute;
  right: 18rpx;
  bottom: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 50rpx;
  height: 50rpx;
  color: #fff;
  background: #722ed1;
  border-radius: 50%;
  box-shadow: 0 5rpx 12rpx rgba(114, 46, 209, .28);
}
.add-goods-button.selected { background: #00a870; }
.selected-count {
  position: absolute;
  right: -8rpx;
  top: -10rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 5rpx;
  box-sizing: border-box;
  color: #fff;
  font-size: 17rpx;
  background: #e5484d;
  border: 2rpx solid #fff;
  border-radius: 20rpx;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 420rpx;
  gap: 18rpx;
  color: #aaa5b5;
  font-size: 24rpx;
}
.list-bottom-space { height: 28rpx; }
.cart-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  gap: 18rpx;
  margin-top: 16rpx;
  padding: 16rpx 18rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx 16rpx 0 0;
  box-shadow: 0 -4rpx 16rpx rgba(67, 47, 119, .08);
}
.cart-summary { flex: 1; min-width: 0; }
.cart-total { display: flex; align-items: baseline; gap: 10rpx; }
.cart-label { color: #5f596b; font-size: 22rpx; }
.cart-amount { color: #e5484d; font-size: 30rpx; font-weight: 600; }
.cart-count { display: flex; gap: 18rpx; margin-top: 8rpx; color: #918b99; font-size: 20rpx; }
.next-button {
  flex: 0 0 180rpx;
  height: 72rpx;
  margin: 0;
  padding: 0;
  color: #fff;
  font-size: 25rpx;
  line-height: 72rpx;
  background: #722ed1;
  border-radius: 10rpx;
}
.next-button::after { border: 0; }
.next-button[disabled] { color: #aaa5b5; background: #e8e5ed; }
@media (max-width: 420px) {
  .category-scroll { flex-basis: 160rpx; width: 160rpx; }
  .add-goods-button { right: 14rpx; top: 70rpx; }
}
</style>
