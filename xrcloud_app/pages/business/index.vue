<template>
  <view class="business-page" :style="{ height: `${pageHeight}px` }">
    <uni-status-bar />

    <view
      id="business-header"
      class="business-header"
      :style="{ paddingTop: `${statusBarHeight + 14}px` }"
    >
      <view class="header-title">业务录入</view>
      <view class="tabs">
        <view
          v-for="(tab, index) in tabs"
          :key="tab.key"
          class="tab-item"
          :class="{ active: currentIndex === index }"
          @click="switchTab(index)"
        >
          <text>{{ tab.label }}</text>
        </view>
      </view>
    </view>

    <swiper
      class="business-swiper"
      :style="{ height: `${contentHeight}px` }"
      :current="currentIndex"
      :duration="220"
      :indicator-dots="false"
      :disable-touch="true"
      @change="handleSwiperChange"
    >
      <swiper-item v-for="tab in tabs" :key="tab.key" class="swiper-item">
        <sale-form
          v-if="tab.key === 'sale'"
          :key="saleFormKey"
          :route-options="saleRouteOptions"
        />
        <purchase-form
          v-if="tab.key === 'purchase'"
          :key="purchaseFormKey"
          :route-options="purchaseRouteOptions"
        />
        <voucher-form
          v-if="tab.key === 'receive'"
          :key="receiveFormKey"
          mode="receive"
          :route-options="receiveRouteOptions"
        />
        <voucher-form
          v-if="tab.key === 'payment'"
          :key="paymentFormKey"
          mode="payment"
          :route-options="paymentRouteOptions"
        />
      </swiper-item>
    </swiper>
  </view>
</template>

<script>
import SaleForm from './components/sale-form.vue'
import PurchaseForm from './components/purchase-form.vue'
import VoucherForm from './components/voucher-form.vue'
import { isLoggedIn } from '../../common/auth'

const TAB_INDEX_MAP = {
  sale: 0,
  sales: 0,
  saleOrder: 0,
  purchase: 1,
  purchases: 1,
  purchaseOrder: 1,
  receive: 2,
  receivePayment: 2,
  receivePaymentVoucher: 2,
  payment: 3,
  payments: 3,
  paymentVoucher: 3
}

export default {
  components: {
    SaleForm,
    PurchaseForm,
    VoucherForm
  },
  data() {
    return {
      statusBarHeight: 0,
      pageHeight: 1,
      contentHeight: 1,
      currentIndex: 0,
      routeOptions: {},
      saleRouteOptions: {},
      purchaseRouteOptions: {},
      receiveRouteOptions: {},
      paymentRouteOptions: {},
      saleFormKey: 0,
      purchaseFormKey: 0,
      receiveFormKey: 0,
      paymentFormKey: 0,
      pageAlive: false,
      measureTimer: null,
      tabs: [
        { key: 'sale', label: '销售单' },
        { key: 'purchase', label: '进货单' },
        { key: 'receive', label: '收款单' },
        { key: 'payment', label: '付款单' }
      ]
    }
  },
  onLoad(options) {
    const systemInfo = uni.getSystemInfoSync()
    this.statusBarHeight = Number(systemInfo.statusBarHeight || 0)
    this.pageHeight = Math.max(1, Math.floor(Number(systemInfo.windowHeight || 0)))
    this.routeOptions = options || {}
    this.currentIndex = this.resolveTabIndex(options)
    this.setRouteOptions(options, this.currentIndex)
    if (this.isAddRoute(options)) {
      if (this.currentIndex === 0) this.saleFormKey += 1
      if (this.currentIndex === 1) this.purchaseFormKey += 1
      if (this.currentIndex === 2) this.receiveFormKey += 1
      if (this.currentIndex === 3) this.paymentFormKey += 1
    }
    uni.setNavigationBarTitle({ title: '业务' })
  },
  onReady() {
    this.pageAlive = true
    this.scheduleContentHeight()
  },
  onShow() {
    this.pageAlive = true
    const editStorageKey = uni.getStorageSync('easy-store-sale-edit-key')
    if (editStorageKey) {
      this.saleRouteOptions = {
        ...this.saleRouteOptions,
        editStorageKey: String(editStorageKey)
      }
      this.saleFormKey += 1
      uni.removeStorageSync('easy-store-sale-edit-key')
      this.currentIndex = 0
    }
    const purchaseEditStorageKey = uni.getStorageSync('easy-store-purchase-edit-key')
    if (purchaseEditStorageKey) {
      this.purchaseRouteOptions = {
        ...this.purchaseRouteOptions,
        editStorageKey: String(purchaseEditStorageKey)
      }
      this.purchaseFormKey += 1
      uni.removeStorageSync('easy-store-purchase-edit-key')
      this.currentIndex = 1
    }
    const receiveEditStorageKey = uni.getStorageSync('easy-store-receive-edit-key')
    if (receiveEditStorageKey) {
      this.receiveRouteOptions = {
        ...this.receiveRouteOptions,
        editStorageKey: String(receiveEditStorageKey)
      }
      this.receiveFormKey += 1
      uni.removeStorageSync('easy-store-receive-edit-key')
      this.currentIndex = 2
    }
    const paymentEditStorageKey = uni.getStorageSync('easy-store-payment-edit-key')
    if (paymentEditStorageKey) {
      this.paymentRouteOptions = {
        ...this.paymentRouteOptions,
        editStorageKey: String(paymentEditStorageKey)
      }
      this.paymentFormKey += 1
      uni.removeStorageSync('easy-store-payment-edit-key')
      this.currentIndex = 3
    }
    const routeAction = uni.getStorageSync('easy-store-business-route')
    if (routeAction) {
      uni.removeStorageSync('easy-store-business-route')
      const action = typeof routeAction === 'string'
        ? this.parseRouteAction(routeAction)
        : routeAction
      const targetIndex = this.resolveTabIndex(action)
      this.setRouteOptions(action, targetIndex)
      this.currentIndex = targetIndex
      if (targetIndex === 0) this.saleFormKey += 1
      if (targetIndex === 1) this.purchaseFormKey += 1
      if (targetIndex === 2) this.receiveFormKey += 1
      if (targetIndex === 3) this.paymentFormKey += 1
    }
    const storedTab = uni.getStorageSync('easy-store-business-tab')
    if (storedTab !== '' && storedTab !== undefined && storedTab !== null) {
      this.currentIndex = this.resolveTabIndex({ tab: storedTab })
      uni.removeStorageSync('easy-store-business-tab')
    }
    if (!isLoggedIn()) {
      uni.reLaunch({ url: '/pages/login/index' })
      return
    }
    this.scheduleContentHeight()
  },
  onHide() {
    this.pageAlive = false
    this.clearMeasureTimer()
  },
  onUnload() {
    this.pageAlive = false
    this.clearMeasureTimer()
  },
  methods: {
    resolveTabIndex(options) {
      if (!options) return 0

      const rawIndex = options.index !== undefined
        ? options.index
        : options.tabIndex
      if (rawIndex !== undefined && rawIndex !== '') {
        const index = Number(rawIndex)
        if (Number.isInteger(index) && index >= 0 && index < this.tabs.length) {
          return index
        }
      }

      const rawTab = options.tab || options.mode || options.type
      if (rawTab !== undefined && rawTab !== null) {
        const tabText = String(rawTab).trim()
        if (/^\d+$/.test(tabText)) {
          const index = Number(tabText)
          if (index >= 0 && index < this.tabs.length) return index
        }
        if (TAB_INDEX_MAP[tabText] !== undefined) return TAB_INDEX_MAP[tabText]
      }

      return 0
    },
    parseRouteAction(value) {
      try {
        return JSON.parse(value)
      } catch (error) {
        return {}
      }
    },
    setRouteOptions(options, tabIndex) {
      const nextOptions = options || {}
      this.routeOptions = nextOptions
      if (tabIndex === 1) {
        this.purchaseRouteOptions = nextOptions
      } else if (tabIndex === 2) {
        this.receiveRouteOptions = nextOptions
      } else if (tabIndex === 3) {
        this.paymentRouteOptions = nextOptions
      } else {
        this.saleRouteOptions = nextOptions
      }
    },
    isAddRoute(options) {
      return Boolean(
        options &&
        (String(options.mode || '').toLowerCase() === 'add' ||
          String(options.reset || '') === '1' ||
          String(options.reset || '').toLowerCase() === 'true')
      )
    },
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
      if (!this.pageAlive) return
      const systemInfo = uni.getSystemInfoSync()
      this.pageHeight = Math.max(1, Math.floor(Number(systemInfo.windowHeight || this.pageHeight)))
      try {
        uni.createSelectorQuery()
          .select('#business-header')
          .boundingClientRect((rect) => {
            if (!this.pageAlive || !rect || !rect.height) return
            this.contentHeight = Math.max(
              1,
              Math.floor(systemInfo.windowHeight - rect.height)
            )
          })
          .exec()
      } catch (error) {
        // 页面切换期间节点可能已销毁，保留上一次有效高度。
      }
    },
    switchTab(index) {
      if (this.currentIndex === index) return
      this.currentIndex = index
    },
    handleSwiperChange(event) {
      const index = Number(event && event.detail ? event.detail.current : 0)
      if (index >= 0 && index < this.tabs.length) {
        this.currentIndex = index
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.business-page {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  color: #33303f;
  background: #f5f4fb;
}

.business-header {
  flex: 0 0 auto;
  padding-right: 22rpx;
  padding-bottom: 18rpx;
  padding-left: 22rpx;
  box-sizing: border-box;
  background: #722ed1;
}

.header-title {
  height: 54rpx;
  color: #fff;
  font-size: 36rpx;
  font-weight: 600;
  line-height: 54rpx;
}

.tabs {
  display: flex;
  align-items: center;
  height: 72rpx;
  margin-top: 12rpx;
  padding: 6rpx;
  box-sizing: border-box;
  background: rgba(255, 255, 255, .14);
  border-radius: 12rpx;
}

.tab-item {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  height: 60rpx;
  color: rgba(255, 255, 255, .72);
  font-size: 24rpx;
  border-radius: 9rpx;
  transition: color .2s, background-color .2s;
}

.tab-item.active {
  color: #722ed1;
  font-weight: 600;
  background: #fff;
}

.business-swiper {
  flex: 0 0 auto;
  min-height: 1px;
  width: 100%;
}

.swiper-item {
  height: 100%;
  overflow: hidden;
}
</style>
