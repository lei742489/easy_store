<template>
  <view class="page">
    <view class="page-header">
      <uni-status-bar />
	  <view style="height: 50rpx;"></view>
      <view class="header-content">
        <text class="page-title">鎶ヨ〃</text>
        <text class="page-caption">閿€鍞粡钀ユ暟鎹?/text>
      </view>
    </view>

    <scroll-view class="page-scroll" scroll-y :show-scrollbar="false">
      <view class="report-card">
        <view class="card-header">
          <view class="card-title-wrap">
            <view class="title-mark" />
            <text class="card-title">鏈€杩?7 澶╅攢鍞粺璁?/text>
          </view>
          <text class="card-caption">{{ salesDateRange }}</text>
        </view>

        <view v-if="salesLoading" class="chart-loading">
          <uni-icons type="spinner-cycle" color="#722ed1" :size="28" />
          <text>姝ｅ湪鍔犺浇...</text>
        </view>
        <view v-else-if="salesRecords.length" class="line-chart">
          <qiun-data-charts
            type="line"
            canvas-id="recent-sales-chart"
            :chart-data="salesChartData"
            :opts="salesChartOpts"
            :animation="true"
            :tooltip-show="true"
            :ontap="false"
          />
        </view>
        <view v-else class="chart-empty">
          <uni-icons type="info" color="#b6b0c2" :size="38" />
          <text>鏆傛棤閿€鍞暟鎹?/text>
        </view>

        <view class="summary-row">
          <view class="summary-item">
            <text class="summary-label">閿€鍞噾棰?/text>
            <text class="summary-value sales-value">锟{ formatAmount(totalSalesAmount) }}</text>
          </view>
          <view v-if="isRoot" class="summary-item">
            <text class="summary-label">姣涘埄</text>
            <text class="summary-value profit-value">锟{ formatAmount(totalProfitAmount) }}</text>
          </view>
        </view>
      </view>

      <view v-if="isRoot" class="report-card asset-card">
        <view class="card-header">
          <view class="card-title-wrap">
            <view class="title-mark" />
            <text class="card-title">璧勪骇缁熻</text>
          </view>
        </view>

        <view v-if="assetLoading" class="asset-loading">
          <uni-icons type="spinner-cycle" color="#722ed1" :size="28" />
          <text>姝ｅ湪鍔犺浇...</text>
        </view>
        <template v-else>
          <view class="asset-total">
            <text class="asset-total-label">鎬昏祫浜?/text>
            <text class="asset-total-value">锟{ formatAmount(totalAssets) }}</text>
          </view>

          <view v-if="assetChartHasData" class="ring-chart">
            <qiun-data-charts
              type="ring"
              canvas-id="asset-statistics-chart"
              :chart-data="assetChartData"
              :opts="assetChartOpts"
              :animation="true"
              :tooltip-show="true"
            />
          </view>
          <view v-else class="asset-chart-empty">
            <uni-icons type="info" color="#b6b0c2" :size="32" />
            <text>鏆傛棤璧勪骇鏁版嵁</text>
          </view>

          <view class="asset-grid">
            <view class="asset-item account-item">
              <text class="asset-label">璐︽埛浣欓</text>
              <text class="asset-value">锟{ formatAmount(assetData.accountBalance) }}</text>
            </view>
            <view class="asset-item inventory-item">
              <text class="asset-label">搴撳瓨鎬婚</text>
              <text class="asset-value">锟{ formatAmount(assetData.inventoryAmount) }}</text>
            </view>
            <view class="asset-item receivable-item">
              <text class="asset-label">搴旀敹娆犳</text>
              <text class="asset-value">锟{ formatAmount(assetData.receivableAmount) }}</text>
            </view>
            <view class="asset-item payable-item">
              <text class="asset-label">搴斾粯娆犳</text>
              <text class="asset-value">锟{ formatSignedAmount(assetData.payableAmount) }}</text>
            </view>
          </view>
        </template>
      </view>

      <view v-if="isRoot" class="report-card top-card">
        <view class="card-header">
          <view class="card-title-wrap">
            <view class="title-mark" />
            <text class="card-title">鏈湀鍛樺伐閿€鍞 TOP5</text>
          </view>
          <text class="card-caption">{{ currentMonth }}</text>
        </view>

        <view v-if="topLoading" class="chart-loading">
          <uni-icons type="spinner-cycle" color="#722ed1" :size="28" />
          <text>姝ｅ湪鍔犺浇...</text>
        </view>
        <view v-else-if="topRecords.length" class="pie-chart">
          <qiun-data-charts
            type="pie"
            canvas-id="employee-sales-top-chart"
            :chart-data="topChartData"
            :opts="topChartOpts"
            :animation="true"
            :tooltip-show="true"
          />
        </view>
        <view v-else class="chart-empty">
          <uni-icons type="info" color="#b6b0c2" :size="38" />
          <text>鏆傛棤鍛樺伐閿€鍞暟鎹?/text>
        </view>
      </view>

      <view class="bottom-space" />
    </scroll-view>
  </view>
</template>

<script>
import request from '../../common/request'
import {
  getHomeAssetStatistics,
  getUserInfo,
  listCashierStatistics
} from '../../common/api'
import { getUser, isLoggedIn, updateUser } from '../../common/auth'

const pad = (value) => String(value).padStart(2, '0')

const formatDate = (date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`

const formatMonth = (date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}`

const getRecentSevenDayRange = () => {
  const endDate = new Date()
  const startDate = new Date(endDate)
  startDate.setDate(startDate.getDate() - 6)

  return {
    startDate: formatDate(startDate),
    endDate: formatDate(endDate)
  }
}

const getCurrentMonthRange = () => {
  const today = new Date()
  return {
    startDate: `${formatMonth(today)}-01`,
    endDate: formatDate(today)
  }
}

const createAssetData = () => ({
  totalAssets: 0,
  accountBalance: 0,
  inventoryAmount: 0,
  receivableAmount: 0,
  payableAmount: 0
})

export default {
  data() {
    return {
      user: getUser() || {},
      salesRecords: [],
      assetData: createAssetData(),
      topRecords: [],
      salesLoading: false,
      assetLoading: false,
      topLoading: false,
      loading: false,
      redirecting: false
    }
  },
  computed: {
    isRoot() {
      return Number(this.user.isRoot) === 1
    },
    recentDateRange() {
      return getRecentSevenDayRange()
    },
    salesDateRange() {
      return `${this.recentDateRange.startDate} 鑷?${this.recentDateRange.endDate}`
    },
    currentMonth() {
      return formatMonth(new Date())
    },
    salesChartData() {
      const records = Array.isArray(this.salesRecords) ? this.salesRecords : []
      const series = [
        {
          name: '閿€鍞噾棰?,
          data: records.map((item) => Number(item.salesAmount || 0))
        }
      ]
      if (this.isRoot) {
        series.push({
          name: '姣涘埄',
          data: records.map((item) => Number(item.profitAmount || 0))
        })
      }
      return {
        categories: records.map((item) => String(item.date || '').slice(5)),
        series
      }
    },
    assetChartData() {
      return {
        series: [
          {
            name: '璧勪骇鏋勬垚',
            data: [
              {
                name: '璐︽埛浣欓',
                value: Math.abs(Number(this.assetData.accountBalance || 0))
              },
              {
                name: '搴撳瓨鎬婚',
                value: Math.abs(Number(this.assetData.inventoryAmount || 0))
              },
              {
                name: '搴旀敹娆犳',
                value: Math.abs(Number(this.assetData.receivableAmount || 0))
              },
              {
                name: '搴斾粯娆犳',
                value: Math.abs(Number(this.assetData.payableAmount || 0))
              }
            ]
          }
        ]
      }
    },
    assetChartHasData() {
      const data = this.assetChartData.series[0].data
      return data.some((item) => Number(item.value) > 0)
    },
    topChartData() {
      const records = this.topRecords
        .slice()
        .sort(
          (left, right) =>
            Number(right.salesAmount || 0) - Number(left.salesAmount || 0)
        )
        .slice(0, 5)

      return {
        series: [
          {
            name: '閿€鍞噾棰?,
            data: records.map((item) => ({
              name: item.cashierName || '鏈懡鍚嶅憳宸?,
              value: Number(item.salesAmount || 0)
            }))
          }
        ]
      }
    },
    totalSalesAmount() {
      return this.salesRecords.reduce(
        (total, item) => total + Number(item.salesAmount || 0),
        0
      )
    },
    totalProfitAmount() {
      return this.salesRecords.reduce(
        (total, item) => total + Number(item.profitAmount || 0),
        0
      )
    },
    totalAssets() {
      return Number(this.assetData.totalAssets || 0)
    },
    salesChartOpts() {
      return {
        color: ['#4d6bff', '#32bf8a'],
        padding: [18, 12, 8, 12],
        dataLabel: false,
        legend: {
          show: true,
          position: 'top',
          lineHeight: 24,
          fontSize: 12
        },
        xAxis: {
          disableGrid: false,
          itemCount: 7,
          fontSize: 10,
          axisLineColor: '#e5e8ef',
          boundaryGap: 'center'
        },
        yAxis: {
          gridType: 'dash',
          dashLength: 3,
          splitNumber: 4,
          data: [
            {
              min: 0,
              unit: '鍏?,
              tofix: 0
            }
          ]
        },
        extra: {
          line: {
            type: 'curve',
            width: 3
          },
          tooltip: {
            showCategory: true,
            showBox: true,
            legendShow: true
          }
        }
      }
    },
    assetChartOpts() {
      return {
        color: ['#4d6bff', '#32bf8a', '#21c1ed', '#ffb65a'],
        padding: [14, 12, 14, 12],
        dataLabel: false,
        legend: {
          show: true,
          position: 'bottom',
          lineHeight: 22,
          fontSize: 11
        },

        extra: {
          ring: {
            ringWidth: 28,
            activeOpacity: 0.6,
            activeRadius: 6,
            border: true,
            borderWidth: 2,
            borderColor: '#ffffff'
          },
          tooltip: {
            showCategory: true,
            showBox: true,
            legendShow: true
          }
        }
      }
    },
    topChartOpts() {
      return {
        color: ['#4d6bff', '#32bf8a', '#21c1ed', '#ffb65a', '#e5484d'],
        padding: [12, 12, 12, 12],
        dataLabel: true,
        legend: {
          show: true,
          position: 'bottom',
          lineHeight: 24,
          fontSize: 11
        },
        extra: {
          pie: {
            activeOpacity: 0.7,
            activeRadius: 8,
            offsetAngle: 0,
            labelWidth: 12,
            border: true,
            borderWidth: 2,
            borderColor: '#ffffff'
          },
          tooltip: {
            showCategory: true,
            showBox: true,
            legendShow: true
          }
        }
      }
    }
  },
  onShow() {
    if (!isLoggedIn()) {
      this.redirectToLogin()
      return
    }
    this.loadPage()
  },
  methods: {
    async loadPage() {
      if (this.loading || this.redirecting) return
      this.loading = true
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...(currentUser || {}) }
        updateUser(this.user)
      } catch (error) {
        if (this.isAuthError(error)) {
          this.redirectToLogin()
          return
        }
      }

      await Promise.all([
        this.loadRecentSalesStatistics(),
        this.isRoot ? this.loadAssetStatistics() : Promise.resolve(),
        this.isRoot ? this.loadTopStatistics() : Promise.resolve()
      ])
      this.loading = false
    },
    async loadRecentSalesStatistics() {
      this.salesLoading = true
      try {
        const data = await request({
          url: '/api/user/appSaleStatistics/monthly',
          data: {
            userId: this.user.id,
            startDate: this.recentDateRange.startDate,
            endDate: this.recentDateRange.endDate
          }
        })
        this.salesRecords = Array.isArray(data) ? data : []
      } catch (error) {
        this.salesRecords = []
        this.handleError(error)
      } finally {
        this.salesLoading = false
      }
    },
    async loadAssetStatistics() {
      this.assetLoading = true
      try {
        const data = await getHomeAssetStatistics({ userId: this.user.id })
        this.assetData = {
          ...createAssetData(),
          ...(data || {})
        }
      } catch (error) {
        this.assetData = createAssetData()
        this.handleError(error)
      } finally {
        this.assetLoading = false
      }
    },
    async loadTopStatistics() {
      this.topLoading = true
      try {
        const range = getCurrentMonthRange()
        const data = await listCashierStatistics({
          userId: this.user.id,
          startDate: range.startDate,
          endDate: range.endDate,
          current: 1,
          pageSize: 100
        })
        this.topRecords = data && Array.isArray(data.records) ? data.records : []
      } catch (error) {
        this.topRecords = []
        this.handleError(error)
      } finally {
        this.topLoading = false
      }
    },
    formatAmount(value) {
      const number = Number(value || 0)

      return Number.isFinite(number)
        ? number.toFixed(2)
        : '0.00'
    },
    formatSignedAmount(value) {
      const number = Number(value || 0)
      if (!Number.isFinite(number)) return '0.00'
      return `${number < 0 ? '-' : ''}${this.formatAmount(Math.abs(number))}`
    },
    isAuthError(error) {
      const message = error && error.message ? error.message : ''
      return /鐧诲綍|token|杩囨湡/i.test(message)
    },
    handleError(error) {
      if (this.isAuthError(error)) {
        this.redirectToLogin()
        return
      }
      const message = error && error.message ? error.message : '鎶ヨ〃鍔犺浇澶辫触'
      uni.showToast({ title: message, icon: 'none' })
    },
    redirectToLogin() {
      if (this.redirecting) return
      this.redirecting = true
      uni.reLaunch({ url: '/pages/login/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  color: #30283d;
  background: #f5f4fb;
}

.page-header {
  color: #fff;
  background: linear-gradient(135deg, #722ed1 0%, #5b35bd 100%);
}

.header-content {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 20rpx 30rpx 28rpx;
}

.page-title {
  font-size: 40rpx;
  font-weight: 700;
}

.page-caption {
  color: rgba(255, 255, 255, 0.72);
  font-size: 24rpx;
}

.page-scroll {
  height: calc(100vh - 148rpx);
  box-sizing: border-box;
}

.report-card {
  margin: 22rpx 22rpx 0;
  padding: 24rpx 22rpx 20rpx;
  box-sizing: border-box;
  background: #fff;
  border: 1rpx solid #ece9f2;
  border-radius: 18rpx;
  box-shadow: 0 8rpx 24rpx rgba(67, 47, 119, 0.07);
}

.top-card,
.asset-card {
  margin-top: 22rpx;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44rpx;
}

.card-title-wrap {
  display: flex;
  align-items: center;
  min-width: 0;
}

.title-mark {
  width: 6rpx;
  height: 30rpx;
  margin-right: 14rpx;
  background: #722ed1;
  border-radius: 4rpx;
}

.card-title {
  overflow: hidden;
  color: #30283d;
  font-size: 30rpx;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-caption {
  flex-shrink: 0;
  margin-left: 16rpx;
  color: #9893a5;
  font-size: 20rpx;
}

.line-chart {
  width: 100%;
  height: 390rpx;
  margin-top: 8rpx;
}

.pie-chart {
  width: 100%;
  height: 490rpx;
  margin-top: 8rpx;
}

.ring-chart {
  width: 100%;
  height: 410rpx;
  margin-top: 8rpx;
}

.chart-loading,
.chart-empty,
.asset-loading,
.asset-chart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 390rpx;
  color: #aaa5b5;
  font-size: 24rpx;
  gap: 16rpx;
}

.asset-loading {
  height: 474rpx;
}

.asset-chart-empty {
  height: 260rpx;
}

.summary-row {
  display: flex;
  align-items: center;
  gap: 30rpx;
  margin-top: 4rpx;
  padding: 22rpx 4rpx 0;
  border-top: 1rpx solid #f0edf5;
}

.summary-item {
  display: flex;
  align-items: baseline;
  min-width: 0;
}

.summary-label,
.asset-label {
  color: #777184;
  font-size: 24rpx;
}

.summary-value {
  margin-left: 8rpx;
  font-size: 29rpx;
  font-weight: 700;
}

.sales-value {
  color: #4d6bff;
}

.profit-value {
  color: #00a870;
}

.asset-total {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin: 18rpx 0 0;
}

.asset-total-label {
  color: #777184;
  font-size: 23rpx;
}

.asset-total-value {
  margin-top: 6rpx;
  color: #5b35bd;
  font-size: 42rpx;
  font-weight: 700;
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
  margin-top: 14rpx;
}

.asset-item {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 18rpx;
  border-radius: 10rpx;
}

.asset-value {
  overflow: hidden;
  margin-top: 8rpx;
  font-size: 27rpx;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-item {
  background: #eef2ff;

  .asset-value {
    color: #4d6bff;
  }
}

.inventory-item {
  background: #eaf8f2;

  .asset-value {
    color: #00a870;
  }
}

.receivable-item {
  background: #eaf9fd;

  .asset-value {
    color: #1596b5;
  }
}

.payable-item {
  background: #fff4e8;

  .asset-value {
    color: #d9710e;
  }
}

.bottom-space {
  height: 48rpx;
}

@media (prefers-color-scheme: dark) {
  .page {
    color: #f5f2fb;
    background: #17151d;
  }

  .report-card {
    background: #24212d;
    border-color: #393442;
    box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.2);
  }

  .card-title {
    color: #f5f2fb;
  }

  .card-caption,
  .summary-label,
  .asset-label,
  .asset-total-label {
    color: #aaa3b8;
  }

  .summary-row {
    border-color: #393442;
  }

  .asset-total-value {
    color: #b8a1ff;
  }

  .account-item {
    background: rgba(77, 107, 255, 0.16);
  }

  .inventory-item {
    background: rgba(50, 191, 138, 0.16);
  }

  .receivable-item {
    background: rgba(33, 193, 237, 0.16);
  }

  .payable-item {
    background: rgba(255, 182, 90, 0.16);
  }
}
</style>
