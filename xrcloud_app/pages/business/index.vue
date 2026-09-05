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
import { listHomeMenus } from '../../common/api'

const TAB_INDEX_MAP = {
  sale: 'sale',
  sales: 'sale',
  saleOrder: 'sale',
  purchase: 'purchase',
  purchases: 'purchase',
  purchaseOrder: 'purchase',
  receive: 'receive',
  receivePayment: 'receive',
  receivePaymentVoucher: 'receive',
  payment: 'payment',
  payments: 'payment',
  paymentVoucher: 'payment'
}

const ALL_TABS = [
  { key: 'sale', label: '销售单', menuCode: 'sale_order_add' },
  { key: 'purchase', label: '进货单', menuCode: 'purchase_order_add' },
  { key: 'receive', label: '收款单', menuCode: 'receive_payment_add' },
  { key: 'payment', label: '付款单', menuCode: 'payment_add' }
]

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
      requestedTabKey: 'sale',
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
      tabs: [],
      permissionLoading: false,
      permissionReady: false
    }
  },
  onLoad(options) {
    const systemInfo = uni.getSystemInfoSync()
    this.statusBarHeight = Number(systemInfo.statusBarHeight || 0)
    this.pageHeight = Math.max(1, Math.floor(Number(systemInfo.windowHeight || 0)))
    this.routeOptions = options || {}
    this.requestedTabKey = this.resolveTabKey(options)
    this.setRouteOptions(options, this.requestedTabKey)
    if (this.isAddRoute(options)) {
      this.bumpFormKey(this.requestedTabKey)
    }
    uni.setNavigationBarTitle({ title: '业务' })
  },
  onReady() {
    this.pageAlive = true
    this.scheduleContentHeight()
  },
  onShow() {
    this.pageAlive = true
    if (!isLoggedIn()) {
      uni.reLaunch({ url: '/pages/login/index' })
      return
    }
    if (!this.permissionReady) {
      this.loadAvailableTabs()
      return
    }
    this.processPendingRoutes()
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
    resolveTabKey(options) {
      if (!options) return 'sale'

      const rawIndex = options.index !== undefined
        ? options.index
        : options.tabIndex
      if (rawIndex !== undefined && rawIndex !== '') {
        const index = Number(rawIndex)
        if (Number.isInteger(index) && index >= 0 && index < ALL_TABS.length) {
          return ALL_TABS[index].key
        }
      }

      const rawTab = options.tab || options.mode || options.type
      if (rawTab !== undefined && rawTab !== null) {
        const tabText = String(rawTab).trim()
        if (/^\d+$/.test(tabText)) {
          const index = Number(tabText)
          if (index >= 0 && index < ALL_TABS.length) return ALL_TABS[index].key
        }
        if (TAB_INDEX_MAP[tabText] !== undefined) return TAB_INDEX_MAP[tabText]
      }

      return 'sale'
    },
    parseRouteAction(value) {
      try {
        return JSON.parse(value)
      } catch (error) {
        return {}
      }
    },
    setRouteOptions(options, tabKey) {
      const nextOptions = options || {}
      this.routeOptions = nextOptions
      if (tabKey === 'purchase') {
        this.purchaseRouteOptions = nextOptions
      } else if (tabKey === 'receive') {
        this.receiveRouteOptions = nextOptions
      } else if (tabKey === 'payment') {
        this.paymentRouteOptions = nextOptions
      } else {
        this.saleRouteOptions = nextOptions
      }
    },
    bumpFormKey(tabKey) {
      if (tabKey === 'sale') this.saleFormKey += 1
      if (tabKey === 'purchase') this.purchaseFormKey += 1
      if (tabKey === 'receive') this.receiveFormKey += 1
      if (tabKey === 'payment') this.paymentFormKey += 1
    },
    getTabIndex(tabKey) {
      const index = this.tabs.findIndex((tab) => tab.key === tabKey)
      return index >= 0 ? index : 0
    },
    async loadAvailableTabs() {
      if (this.permissionLoading || this.permissionReady || !this.pageAlive) return
      this.permissionLoading = true
      try {
        const groups = await listHomeMenus()
        const menuCodes = new Set()
        ;(Array.isArray(groups) ? groups : []).forEach((group) => {
          ;(Array.isArray(group && group.menus) ? group.menus : []).forEach((menu) => {
            if (menu && menu.code) menuCodes.add(String(menu.code))
          })
        })
        this.tabs = ALL_TABS.filter((tab) => menuCodes.has(tab.menuCode))
        this.permissionReady = true
        this.currentIndex = this.getTabIndex(this.requestedTabKey)
        this.scheduleContentHeight()
        this.processPendingRoutes()
      } catch (error) {
        this.tabs = []
        uni.showToast({
          title: error && error.message ? error.message : '加载业务权限失败',
          icon: 'none'
        })
      } finally {
        this.permissionLoading = false
      }
    },
    processPendingRoutes() {
      if (!this.permissionReady) return
      const editStorageKeys = [
        ['easy-store-sale-edit-key', 'sale'],
        ['easy-store-purchase-edit-key', 'purchase'],
        ['easy-store-receive-edit-key', 'receive'],
        ['easy-store-payment-edit-key', 'payment']
      ]
      editStorageKeys.forEach(([storageKey, tabKey]) => {
        const editStorageKey = uni.getStorageSync(storageKey)
        if (!editStorageKey) return
        this[`${tabKey}RouteOptions`] = {
          ...this[`${tabKey}RouteOptions`],
          editStorageKey: String(editStorageKey)
        }
        this.bumpFormKey(tabKey)
        uni.removeStorageSync(storageKey)
        this.requestedTabKey = tabKey
      })

      const routeAction = uni.getStorageSync('easy-store-business-route')
      if (routeAction) {
        uni.removeStorageSync('easy-store-business-route')
        const action = typeof routeAction === 'string'
          ? this.parseRouteAction(routeAction)
          : routeAction
        const targetTabKey = this.resolveTabKey(action)
        this.setRouteOptions(action, targetTabKey)
        this.requestedTabKey = targetTabKey
        this.bumpFormKey(targetTabKey)
      }

      const storedTab = uni.getStorageSync('easy-store-business-tab')
      if (storedTab !== '' && storedTab !== undefined && storedTab !== null) {
        this.requestedTabKey = this.resolveTabKey({ tab: storedTab })
        uni.removeStorageSync('easy-store-business-tab')
      }
      this.currentIndex = this.getTabIndex(this.requestedTabKey)
      this.scheduleContentHeight()
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
      if (index < 0 || index >= this.tabs.length) return
      if (this.currentIndex === index) return
      this.currentIndex = index
      this.requestedTabKey = this.tabs[index].key
    },
    handleSwiperChange(event) {
      const index = Number(event && event.detail ? event.detail.current : 0)
      if (index >= 0 && index < this.tabs.length) {
        this.currentIndex = index
        this.requestedTabKey = this.tabs[index].key
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
