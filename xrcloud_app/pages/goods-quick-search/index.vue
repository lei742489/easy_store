<template>
  <view class="page">
    <view id="top" class="top-area">
      <view class="search-panel">
        <view class="search-row">
          <uni-icons type="search" color="#aaa5b5" :size="20" />
          <input
            v-model="keyword"
            class="search-input"
            placeholder="请输入货品名称或代码"
            confirm-type="search"
            :focus="inputFocused"
            @confirm="handleSearch"
          />
          <uni-icons
            v-if="keyword"
            type="clear"
            color="#aaa5b5"
            :size="20"
            @click="clearKeyword"
          />
        </view>
      </view>
    </view>

    <scroll-view
      class="goods-scroll"
      scroll-y
      :show-scrollbar="false"
      :scroll-top="scrollTop"
      :lower-threshold="120"
      :style="{ height: `${goodsScrollHeight}px` }"
      @scrolltolower="loadMore"
    >
      <view v-if="goodsList.length" class="goods-list">
        <view
          v-for="(goods, index) in goodsList"
          :key="goods.id || index"
          class="goods-row"
          @click="openEditForm(goods)"
        >
          <view class="goods-main">
            <view class="goods-title-row">
              <text class="goods-title">{{ goods.title || "未命名货品" }}</text>
              <text v-if="Number(goods.status) === 0" class="disabled-tag"
                >停用</text
              >
            </view>
            <text v-if="goods.goodsCode" class="goods-code"
              >货品代码：{{ goods.goodsCode }}</text
            >
            <view class="goods-meta">
              <text
                >库存：<text
                  class="stock-value"
                  :class="{ negative: Number(goods.stock || 0) < 0 }"
                  >{{ formatQuantity(goods.stock) }}</text
                ></text
              >
              <text>单位：{{ goods.unit || "-" }}</text>
            </view>
          </view>

          <view v-if="visiblePrices.length" class="price-list">
            <text
              v-for="price in visiblePrices"
              :key="price.key"
              class="price-item"
            >
              {{ price.label }}：{{ formatPrice(goods[price.field]) }}
            </text>
          </view>
        </view>
      </view>

      <view v-else-if="!loading && hasSearched" class="empty-state">
        <uni-icons type="info" color="#aaa5b5" :size="52" />
        <text>没有查询到货品</text>
      </view>

      <uni-load-more
        v-if="hasSearched && (goodsList.length || loading)"
        :status="loadStatus"
      />
      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import {
  getUser,
  isLoggedIn,
  clearSession,
  updateUser,
} from "../../common/auth";
import { getUserInfo, listGoods } from "../../common/api";

const PRICE_PERMISSIONS = [
  {
    key: "purchase",
    label: "进货价",
    field: "purPrc",
    permission: "data_view:purchase_price",
  },
  {
    key: "trade",
    label: "批发价",
    field: "tradePrc",
    permission: "data_view:trade_price",
  },
  {
    key: "sale",
    label: "零售价",
    field: "salePrc",
    permission: "data_view:sale_price",
  },
  {
    key: "cost",
    label: "成本价",
    field: "costPrice",
    permission: "data_view:cost_price",
  },
];

export default {
  data() {
    return {
      user: getUser() || {},
      keyword: "",
      loading: false,
      initializing: false,
      initialized: false,
      redirecting: false,
      hasSearched: false,
      inputFocused: false,
      goodsScrollHeight: 1,
      measureTimer: null,
      requestId: 0,
      current: 1,
      pageSize: 20,
      total: 0,
      goodsList: [],
      scrollTop: 0,
    };
  },
  computed: {
    isRoot() {
      return Number(this.user.isRoot) === 1;
    },
    visiblePrices() {
      const permissionCodes = Array.isArray(this.user.permissionCodes)
        ? this.user.permissionCodes
        : [];
      return PRICE_PERMISSIONS.filter(
        (price) => {
          if (price.key === 'cost' && !this.isRoot) return false
          return this.isRoot || permissionCodes.includes(price.permission)
        }
      );
    },
    hasMore() {
      return this.goodsList.length < this.total;
    },
    loadStatus() {
      if (this.loading) return "loading";
      return this.hasMore ? "more" : "noMore";
    },
  },
  onReady() {
    this.scheduleMeasure();
    this.$nextTick(() => {
      this.inputFocused = true;
    });
  },
  onShow() {
    if (!isLoggedIn()) {
      this.redirectToLogin();
      return;
    }
    if (!this.initialized) {
      this.initialize();
    } else {
      this.scheduleMeasure();
    }
  },
  onHide() {
    this.clearMeasureTimer();
  },
  onUnload() {
    this.redirecting = true;
    this.clearMeasureTimer();
  },
  onPullDownRefresh() {
    this.queryGoods(true).finally(() => uni.stopPullDownRefresh());
  },
  methods: {
    clearMeasureTimer() {
      if (this.measureTimer) {
        clearTimeout(this.measureTimer);
        this.measureTimer = null;
      }
    },
    scheduleMeasure() {
      this.clearMeasureTimer();
      this.measureTimer = setTimeout(() => {
        this.measureTimer = null;
        this.measureGoodsScroll();
      }, 0);
    },
    measureGoodsScroll() {
      if (this.redirecting) return;
      const systemInfo = uni.getSystemInfoSync();
      try {
        uni
          .createSelectorQuery()
          .select("#top")
          .boundingClientRect()
          .exec((rects) => {
            const topRect = rects && rects[0];
            if (!topRect || !topRect.height) return;
            const topBottom = Number(
              topRect.bottom || topRect.top + topRect.height
            );
            const marginTop = uni.upx2px ? uni.upx2px(20) : 10;
            this.goodsScrollHeight = Math.max(
              240,
              Math.floor(systemInfo.windowHeight - topBottom - marginTop)
            );
          });
      } catch (error) {
        this.goodsScrollHeight = Math.max(
          240,
          Math.floor(systemInfo.windowHeight * 0.7)
        );
      }
    },
    redirectToLogin() {
      if (this.redirecting) return;
      this.redirecting = true;
      clearSession();
      uni.reLaunch({ url: "/pages/login/index" });
    },
    async initialize() {
      if (this.initializing || this.redirecting) return;
      this.initializing = true;
      try {
        const currentUser = await getUserInfo();
        this.user = { ...this.user, ...currentUser };
        updateUser(this.user);
        this.initialized = true;
        this.scheduleMeasure();
      } catch (error) {
        const message = error && error.message ? error.message : "货品加载失败";
        if (/登录|token|过期/.test(message)) this.redirectToLogin();
        else uni.showToast({ title: message, icon: "none" });
      } finally {
        this.initializing = false;
      }
    },
    buildQuery(page) {
      const keyword = String(this.keyword || "").trim();
      return {
        current: page,
        pageNo: page,
        pageSize: this.pageSize,
        title: keyword,
        userId: this.user.id,
      };
    },
    async queryGoods(reset = false, pageNumber) {
      if (this.loading || this.redirecting) return;
      const keyword = String(this.keyword || "").trim();
      if (!keyword) {
        this.clearResults();
        return;
      }
      const targetPage = reset ? 1 : pageNumber || this.current;
      const requestId = ++this.requestId;
      this.loading = true;
      if (reset) {
        this.hasSearched = true;
        this.resetScrollPosition();
      }
      try {
        const page = await listGoods(this.buildQuery(targetPage));
        if (requestId !== this.requestId || this.redirecting) return;
        const records = page && Array.isArray(page.records) ? page.records : [];
        this.goodsList = reset ? records : this.goodsList.concat(records);
        this.current = Number(page && page.current ? page.current : targetPage);
        this.total = Number(
          page && page.total ? page.total : this.goodsList.length
        );
      } catch (error) {
        if (requestId !== this.requestId || this.redirecting) return;
        const message = error && error.message ? error.message : "货品加载失败";
        if (/登录|token|过期/.test(message)) this.redirectToLogin();
        else uni.showToast({ title: message, icon: "none" });
      } finally {
        if (requestId === this.requestId) this.loading = false;
      }
    },
    handleSearch() {
      if (String(this.keyword || "").trim()) {
        this.queryGoods(true);
      } else {
        this.clearResults();
      }
    },
    clearKeyword() {
      if (!this.keyword) return;
      this.keyword = "";
      this.clearResults();
    },
    loadMore() {
      if (this.loading || !this.hasSearched || !this.hasMore) return;
      this.queryGoods(false, this.current + 1);
    },
    openEditForm(goods) {
      if (!goods || goods.id === undefined || goods.id === null) return;
      uni.setStorageSync("easy-store-edit-goods", goods);
      uni.navigateTo({
        url: `/pages/goods/form?id=${encodeURIComponent(goods.id)}`,
      });
    },
    clearResults() {
      this.requestId += 1;
      this.loading = false;
      this.goodsList = [];
      this.current = 1;
      this.total = 0;
      this.hasSearched = false;
      this.resetScrollPosition();
    },
    resetScrollPosition() {
      this.scrollTop = 1;
      this.$nextTick(() => {
        this.scrollTop = 0;
      });
    },
    formatPrice(value) {
      const number = Number(value);
      return Number.isFinite(number) ? `￥${number.toFixed(2)}` : "-";
    },
    formatQuantity(value) {
      const number = Number(value || 0);
      if (!Number.isFinite(number)) return "0";
      return Number.isInteger(number) ? String(number) : number.toFixed(2);
    },
  },
};
</script>

<style lang="scss" scoped>
.page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 20rpx 22rpx 0;
  box-sizing: border-box;
  color: #33303f;
  background: #f5f4fb;
  overflow: hidden;
}
.top-area {
  flex: 0 0 auto;
}
.search-panel {
  padding: 18rpx 20rpx;
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, 0.06);
}
.search-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  height: 58rpx;
  padding: 0 16rpx;
  box-sizing: border-box;
  background: #faf9fc;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}
.search-input {
  flex: 1;
  min-width: 0;
  height: 58rpx;
  color: #393044;
  font-size: 25rpx;
}
.clear-action {
  flex: 0 0 auto;
  color: #722ed1;
  font-size: 23rpx;
}
.goods-scroll {
  flex: 0 0 auto;
  min-height: 240px;
  margin-top: 20rpx;
  padding: 0 2rpx;
  box-sizing: border-box;
}
.goods-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.goods-row {
  padding: 22rpx;
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 14rpx;
  box-shadow: 0 5rpx 16rpx rgba(67, 47, 119, 0.05);
}
.goods-main {
  min-width: 0;
}
.goods-title-row {
  display: flex;
  align-items: center;
  min-width: 0;
}
.goods-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #1f1f1f;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.disabled-tag {
  flex: 0 0 auto;
  margin-left: 12rpx;
  padding: 4rpx 10rpx;
  color: #e5484d;
  font-size: 18rpx;
  background: #fff0f0;
  border-radius: 8rpx;
}
.goods-code,
.goods-meta {
  margin-top: 10rpx;
  color: #8b8594;
  font-size: 21rpx;
}
.goods-meta {
  display: flex;
  gap: 28rpx;
}
.stock-value {
  color: #33303f;
  font-weight: 600;
}
.stock-value.negative {
  color: #e5484d;
}
.price-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10rpx 20rpx;
  margin-top: 18rpx;
  padding-top: 16rpx;
  color: #722ed1;
  font-size: 21rpx;
  border-top: 1rpx solid #eeeaf4;
}
.price-item {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
.bottom-space {
  height: 28rpx;
}
</style>
