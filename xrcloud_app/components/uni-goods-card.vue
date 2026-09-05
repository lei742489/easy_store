<template>
  <view class="goods-card" @click="$emit('click', goods)">
    <image
      class="goods-image"
      :src="imageUrl"
      mode="aspectFill"
      @error="handleImageError"
    />
    <view class="goods-content">
      <view class="goods-title-row">
        <text class="goods-title">{{ goods.title || '未命名货品' }}</text>
        <text v-if="Number(goods.status) === 0" class="disabled-tag">停用</text>
      </view>
      <!--
      <text v-if="goods.goodsCode" class="goods-code">货品代码：{{ goods.goodsCode }}</text>
      -->

      <view class="goods-meta">
        <text>分类：{{ goods.categoryId_dictText || goods.categoryName || '未分类' }}</text>
        <text>单位：{{ goods.unit || '-' }}</text>
      </view>
      <view class="goods-stock-row">
        <text class="stock-label">库存</text>
        <text class="stock-value" :class="{ negative: Number(goods.stock || 0) < 0 }">
          {{ formatQuantity(goods.stock) }}
        </text>
        <text v-if="showCostPrice" class="goods-cost">成本：{{ formatPrice(goods.costPrice) }}</text>
      </view>
    </view>
    <view v-if="visiblePrices.length" class="price-row">
      <text v-for="price in visiblePrices" :key="price.key">
        {{ price.label }}：{{ formatPrice(goods[price.field]) }}
      </text>
    </view>
  </view>
</template>

<script>
import { API_BASE_URL } from '../common/config'
import { getUser } from '../common/auth'

export default {
  name: 'UniGoodsCard',
  props: {
    goods: {
      type: Object,
      default: () => ({})
    },
    priceMode: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      user: getUser() || {},
      imageFailed: false
    }
  },
  computed: {
    isRoot() {
      return Number(this.user.isRoot) === 1
    },
    showCostPrice() {
      return this.isRoot
    },
    visiblePrices() {
      const salePrice = { key: 'sale', label: '零售', field: 'salePrc' }
      const tradePrice = { key: 'trade', label: '批发', field: 'tradePrc' }
      const purchasePrice = { key: 'purchase', label: '进货', field: 'purPrc' }
      if (this.priceMode === 'businessOrder') {
        return this.isRoot
          ? [salePrice, tradePrice, purchasePrice]
          : [salePrice, tradePrice]
      }
      return [salePrice, purchasePrice]
    },
    imageUrl() {
      if (this.imageFailed) return '/static/ico/none.png'
      const image = String(this.goods && this.goods.imgUrl || '').trim()
      if (!image) return '/static/ico/none.png'
      if (/^(https?:)?\/\//i.test(image) || image.indexOf('data:') === 0) return image
      return `${API_BASE_URL.replace(/\/$/, '')}/api/upload/static${image.startsWith('/') ? image : `/${image}`}`
    }
  },
  watch: {
    'goods.id'() {
      this.imageFailed = false
    },
    'goods.imgUrl'() {
      this.imageFailed = false
    }
  },
  methods: {
    handleImageError() {
      this.imageFailed = true
    },
    formatPrice(value) {
      const number = Number(value)
      return Number.isFinite(number) ? `￥${number.toFixed(2)}` : '-'
    },
    formatQuantity(value) {
      const number = Number(value || 0)
      if (!Number.isFinite(number)) return '0'
      return Number.isInteger(number) ? String(number) : number.toFixed(2)
    }
  }
}
</script>

<style lang="scss" scoped>
.goods-card {
  display: flex;
  flex-wrap: wrap;
  min-height: 210rpx;
  margin: 0;
  padding: 18rpx;
  box-sizing: border-box;
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 12rpx;
  box-shadow: 0 3rpx 12rpx rgba(67, 47, 119, .05);
}
.goods-image { flex: 0 0 168rpx; width: 168rpx; height: 168rpx; margin-right: 18rpx; background: #f5f3f8; border-radius: 10rpx; }
.goods-content { flex: 1; min-width: 0; }
.goods-title-row { display: flex; align-items: center; min-width: 0; }
.goods-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #30283d;
  font-size: 28rpx;
  font-weight: 600;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  text-overflow: ellipsis;
}
.disabled-tag { flex: 0 0 auto; margin-left: 10rpx; padding: 4rpx 10rpx; color: #e5484d; font-size: 18rpx; background: #fff0f0; border-radius: 8rpx; }
.goods-code, .goods-meta { margin-top: 10rpx; color: #8b8594; font-size: 20rpx; }
.goods-meta { display: flex; gap: 18rpx; }
.goods-meta text { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.goods-stock-row { display: flex; align-items: baseline; margin-top: 15rpx; }
.stock-label { color: #8b8594; font-size: 20rpx; }
.stock-value { margin-left: 12rpx; color: #33303f; font-size: 28rpx; font-weight: 600; }
.stock-value.negative { color: #e5484d; }
.goods-cost { margin-left: auto; color: #722ed1; font-size: 22rpx; }
.price-row { display: grid; flex: 0 0 100%; grid-template-columns: repeat(auto-fit, minmax(180rpx, 1fr)); gap: 8rpx 16rpx; width: 100%; margin-top: 14rpx; padding-top: 12rpx; box-sizing: border-box; color: #8b8594; font-size: 22rpx; border-top: 1rpx solid #f0edf5; }
.price-row text { min-width: 0; overflow: hidden; text-align: left; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 420px) {
  .goods-image { flex-basis: 150rpx; width: 150rpx; height: 150rpx; }
  .goods-card { padding: 14rpx; }
  .goods-meta { display: block; }
  .goods-meta text { display: block; margin-top: 6rpx; }
}
</style>
