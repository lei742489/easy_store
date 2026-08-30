<template>
	<view class="page">
		<view class="filter-panel">


			<view class="quick-search-row">
				<input v-model="keyword" class="quick-search-input" :placeholder="searchPlaceholder"
					confirm-type="search" @confirm="handleSearch" />
					
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
				    <uni-icons type="plusempty" color="#fff" :size="16" />
				    <text>增加</text>
				  </button>
				  <button class="reset-button" @click="handleReset">
				    <uni-icons type="refresh" color="#666" :size="16" />
				    <text>重置</text>
				  </button>
				</view>
			</view>
		</view>

		<view v-if="visibleRecords.length" class="partner-list">
			<view v-for="item in visibleRecords" :key="String(item.id)" class="partner-card" @click="openEditForm(item)">
				<view class="partner-head">
					<view class="partner-main">
						<text class="partner-name">{{ item.name || '-' }}</text>
						<text class="partner-subtitle">{{ getPartnerSubtitle(item) }}</text>
					</view>
					<view class="debt-box">
						<text class="debt-label">欠款：</text>
						<text class="debt-value">¥{{ formatAmount(item.payable) }}</text>
					</view>
				</view>

				<view v-if="getPartnerTags(item).length" class="tag-row">
					<text v-for="tag in getPartnerTags(item)" :key="tag" class="tag-item">{{ tag }}</text>
				</view>

				<view class="footer-row">
					<text class="status-tag" :class="statusClass(item.status)" v-if="item.note">
						{{ item.note}}
					</text>
					<text v-else> </text>
					<text class="create-time">{{ formatDate(item.createTime) }}</text>
				</view>
			</view>
		</view>

		<view v-else-if="!loading" class="empty-state">
			<uni-icons type="info" color="#aaa5b5" :size="52" />
			<text>{{ emptyText }}</text>
		</view>

		<uni-load-more v-if="visibleRecords.length || loading" :status="loadStatus" />
		<view class="bottom-space" />

		<uni-popup ref="filterPopup" type="bottom" :safe-area="true" :is-mask-click="true">
			<view class="filter-popup">
				<view class="popup-header">
					<text class="popup-title">筛选</text>
					<uni-icons type="closeempty" color="#999" :size="22" @click="closeMoreFilters" />
				</view>

				<view class="popup-form-item">
					<text class="popup-label">状态</text>
					<picker :range="statusOptions" range-key="label" :value="statusIndex" @change="onStatusChange">
						<view class="popup-picker">
							<text
								:class="{ placeholder: statusIndex === 0 }">{{ statusOptions[statusIndex].label }}</text>
							<uni-icons type="down" color="#999" :size="16" />
						</view>
					</picker>
				</view>

				<view class="popup-actions">
					<button class="popup-cancel-button" @click="closeMoreFilters">取消</button>
					<button class="popup-confirm-button" @click="applyMoreFilters">确定</button>
				</view>
			</view>
		</uni-popup>
	</view>
</template>

<script>
	import {
		listCustomerPage,
		listSupplierPage
	} from '../../common/api'

	const normalizeText = (value) => String(value || '').trim()

	const createModeState = () => ({
		records: [],
		page: 0,
		total: 0,
		loaded: false,
		loading: false,
		keyword: '',
		statusIndex: 0,
		status: undefined
	})

	export default {
		data() {
			return {
				mode: 'customer',
				listScrollTop: 0,
				pageSize: 50,
				modeState: {
					customer: createModeState(),
					supplier: createModeState()
				},
				statusOptions: [{
						label: '全部状态',
						value: undefined
					},
					{
						label: '启用',
						value: 1
					},
					{
						label: '停用',
						value: 0
					}
				]
			}
		},
		computed: {
			currentState() {
				return this.modeState[this.mode]
			},
			keyword: {
				get() {
					return this.currentState.keyword
				},
				set(value) {
					this.currentState.keyword = normalizeText(value)
				}
			},
			statusIndex: {
				get() {
					return this.currentState.statusIndex
				},
				set(value) {
					const index = Number(value || 0)
					this.currentState.statusIndex = index
					this.currentState.status = this.statusOptions[index] ?
						this.statusOptions[index].value :
						undefined
				}
			},
			pageTitle() {
				return this.mode === 'customer' ? '客户管理' : '供应商管理'
			},
			summaryText() {
				return this.mode === 'customer' ?
					'客户名称 / 联系人 / 手机 / 拼音' :
					'供应商名称 / 联系人 / 手机 / 拼音'
			},
			searchPlaceholder() {
				return this.summaryText
			},
			
			visibleRecords() {
				return this.currentState.records
			},
			totalCount() {
				return Number(this.currentState.total || this.currentState.records.length)
			},
			hasMore() {
				return this.currentState.records.length < this.totalCount
			},
			loadStatus() {
				if (this.currentState.loading) return 'loading'
				return this.hasMore ? 'more' : 'noMore'
			},
			activeFilterCount() {
				let count = 0
				if (normalizeText(this.currentState.keyword)) count += 1
				if (this.currentState.status !== undefined) count += 1
				return count
			},
			emptyText() {
				return this.mode === 'customer' ? '暂无客户数据' : '暂无供应商数据'
			}
		},
		onLoad(options) {
			this.mode = options && options.mode === 'supplier' ? 'supplier' : 'customer'
			this.setPageTitle()
			this.loadFirstPage()
		},
		onShow() {
			this.setPageTitle()
		},
		onPullDownRefresh() {
			this.reloadCurrentMode().finally(() => uni.stopPullDownRefresh())
		},
		onReachBottom() {
			this.loadMore()
		},
		methods: {
			setPageTitle() {
				uni.setNavigationBarTitle({
					title: this.pageTitle
				})
			},
			getModeApi(mode) {
				return mode === 'customer' ? listCustomerPage : listSupplierPage
			},
			buildQuery(current) {
				const state = this.currentState
				const params = {
					current,
					pageSize: this.pageSize,
					name:this.keyword,
					column: 'createTime',
					order: 'desc'
				}
				if (state.status !== undefined) {
					params.status = state.status
				}
				if (normalizeText(state.keyword)) {
					params.key = normalizeText(state.keyword)
				}
				return params
			},
			normalizePartner(item, mode) {
				const source = item || {}
				return {
					...source,
					id: source.id,
					name: normalizeText(source.name),
					pyCode: normalizeText(source.pyCode),
					contactName: normalizeText(source.contactName),
					mobile: normalizeText(source.mobile),
					phone: normalizeText(source.phone),
					note: normalizeText(source.note),
					payable: source.payable,
					status: source.status,
					createTime: source.createTime,
					mode
				}
			},
			async loadFirstPage() {
				const state = this.currentState
				if (state.loading) return
				state.loading = true
				uni.showLoading({
					title: '加载中...',
					mask: true
				})
				try {
					state.records = []
					state.page = 0
					state.total = 0
					const page = await this.getModeApi(this.mode)(this.buildQuery(1))
					const list = page && Array.isArray(page.records) ? page.records : []
					state.records = list
						.filter((item) => item && item.id !== undefined && item.id !== null)
						.map((item) => this.normalizePartner(item, this.mode))
					state.page = Number(page && page.current ? page.current : 1)
					state.total = Number(page && page.total ? page.total : state.records.length)
					state.loaded = true
					this.resetScrollPosition()
				} catch (error) {
					uni.showToast({
						title: error && error.message ? error.message : '加载失败',
						icon: 'none'
					})
				} finally {
					state.loading = false
					uni.hideLoading()
				}
			},
			async reloadCurrentMode() {
				this.currentState.loaded = false
				await this.loadFirstPage()
			},
			async loadMore() {
				const state = this.currentState
				if (state.loading || !this.hasMore) return
				state.loading = true
				try {
					const nextPage = (state.page || 1) + 1
					const page = await this.getModeApi(this.mode)(this.buildQuery(nextPage))
					const list = page && Array.isArray(page.records) ? page.records : []
					const records = list
						.filter((item) => item && item.id !== undefined && item.id !== null)
						.map((item) => this.normalizePartner(item, this.mode))
					state.records = state.records.concat(records)
					state.page = Number(page && page.current ? page.current : nextPage)
					state.total = Number(page && page.total ? page.total : state.total)
					state.loaded = true
				} catch (error) {
					uni.showToast({
						title: error && error.message ? error.message : '加载更多失败',
						icon: 'none'
					})
				} finally {
					state.loading = false
				}
			},
			matchKeyword(item, keyword) {
				const fields = [
					item && item.name,
					item && item.pyCode,
					item && item.contactName,
					item && item.mobile,
					item && item.phone,
					item && item.note,
					item && item.categoryId_dictText,
					item && item.levelId_dictText
				]
				return fields.some((field) => normalizeText(field).toLowerCase().includes(keyword))
			},
			switchMode(mode) {
				if (this.mode === mode) return
				this.mode = mode
				this.setPageTitle()
				if (!this.currentState.loaded) {
					this.loadFirstPage()
				} else {
					this.resetScrollPosition()
				}
			},
			getStorageKey() {
				return this.mode === 'customer'
					? 'easy-store-edit-customer'
					: 'easy-store-edit-supplier'
			},
			openCreateForm() {
				this.navigateToForm()
			},
			openEditForm(item) {
				if (!item || item.id === undefined || item.id === null) return
				this.navigateToForm(String(item.id), item)
			},
			navigateToForm(id, item) {
				if (item) {
					uni.setStorageSync(this.getStorageKey(), item)
				}
				const params = [`mode=${this.mode}`]
				if (id) params.push(`id=${encodeURIComponent(id)}`)
				uni.navigateTo({
					url: `/pages/partner-form/index?${params.join('&')}`,
					success: (res) => {
						const channel = res && res.eventChannel
						if (!channel) return
						channel.on('saved', () => {
							this.reloadCurrentMode()
						})
					}
				})
			},
			handleSearch() {
				this.loadFirstPage()
			},
			handleReset() {
				this.currentState.keyword = ''
				this.currentState.statusIndex = 0
				this.currentState.status = undefined
				this.closeMoreFilters()
				this.reloadCurrentMode()
			},
			openMoreFilters() {
				this.$refs.filterPopup && this.$refs.filterPopup.open()
			},
			closeMoreFilters() {
				this.$refs.filterPopup && this.$refs.filterPopup.close()
			},
			applyMoreFilters() {
				this.closeMoreFilters()
				this.reloadCurrentMode()
			},
			onStatusChange(event) {
				this.statusIndex = Number(event.detail.value || 0)
			},
			resetScrollPosition() {
				this.listScrollTop = 1
				this.$nextTick(() => {
					this.listScrollTop = 0
				})
			},
			getPartnerSubtitle(item) {
				const parts = []
				if (item.contactName) parts.push(item.contactName)
				if (item.mobile) parts.push(item.mobile)
				if (item.phone) parts.push(item.phone)
				return parts.filter(Boolean).join(' / ')
			},
			getPartnerTags(item) {
				const tags = []
				if (this.mode === 'customer') {
					if (item.categoryId_dictText) tags.push(`${item.categoryId_dictText}`)
					if (item.discount !== undefined && item.discount !== null && item.discount !== '') {
						tags.push(`折扣 ${item.discount}`)
					}
				} 

				return tags.slice(0, 3)
			},
			statusText(status) {
				return Number(status) === 1 ? '启用' : '停用'
			},
			statusClass(status) {
				return Number(status) === 1 ? 'enabled' : 'disabled'
			},
			formatAmount(value) {
				const amount = Number(value || 0)
				return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
			},
			formatDate(value) {
				if (!value) return '-'
				const text = String(value)
				return text.indexOf(' ') > -1 ? text.split(' ')[0] : text.slice(0, 10)
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page {
		display: flex;
		flex-direction: column;
		min-height: 0;
		padding: 22rpx 22rpx 0;
		box-sizing: border-box;
		color: #33303f;
		background: #f5f4fb;
		overflow: hidden;
	}

	.filter-panel {
		flex: 0 0 auto;
		padding: 20rpx 22rpx 16rpx;
		background: #fff;
		border: 1rpx solid #ece9f2;
		border-radius: 16rpx;
		box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
	}

	.mode-tabs {
		display: flex;
		gap: 12rpx;
		margin-bottom: 16rpx;
	}

	.mode-tab {
		flex: 1;
		height: 58rpx;
		color: #666;
		font-size: 24rpx;
		line-height: 58rpx;
		text-align: center;
		background: #f2f3f5;
		border-radius: 10rpx;
	}

	.mode-tab.active {
		color: #fff;
		background: #722ed1;
	}

	.quick-search-row {
		display: flex;
		align-items: center;
		gap: 14rpx;
	}

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
	
	.filter-action-right { display: flex; align-items: center; gap: 14rpx;flex: 1; justify-content: flex-end; }

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
		font-size: 25rpx;
		line-height: 58rpx;
		background: #722ed1;
		border-radius: 10rpx;
	}

	.quick-search-button::after,
	.add-button::after,
	.reset-button::after,
	.more-filter-button::after,
	.popup-actions button::after {
		border: 0;
	}

	.filter-actions {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 20rpx;
		margin-top: 16rpx;
	}

	.reset-button,
	.more-filter-button {
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

	.reset-button {
		color: #666;
		background: #f2f3f5;
	}

	.more-filter-button {
		position: relative;
		color: #722ed1;
		background: #f4efff;
	}

	.filter-count {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-width: 28rpx;
		height: 28rpx;
		padding: 0 5rpx;
		color: #fff;
		font-size: 17rpx;
		line-height: 28rpx;
		background: #722ed1;
		border-radius: 18rpx;
	}

	.list-summary {
		flex: 0 0 auto;
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 10rpx 6rpx 10rpx;
	}

	.summary-main {
		display: flex;
		flex-direction: column;
		gap: 4rpx;
	}

	.summary-title {
		color: #454252;
		font-size: 29rpx;
		font-weight: 600;
	}

	.summary-subtitle {
		color: #9a95a4;
		font-size: 20rpx;
	}

	.summary-count {
		color: #722ed1;
		font-size: 23rpx;
		font-weight: 600;
	}

	.partner-scroll {
		flex: 1;
		min-height: 0;
	}

	.partner-list {
		padding-bottom: 10rpx;
	}

	.partner-card {
		margin-bottom: 18rpx;
		padding: 22rpx;
		background: #fff;
		border: 1rpx solid #ece9f2;
		border-radius: 16rpx;
		box-shadow: 0 6rpx 20rpx rgba(67, 47, 119, .06);
	}

	.partner-head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 16rpx;
	}

	.partner-main {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}

	.partner-name {
		color: #1f1f1f;
		font-size: 31rpx;
		font-weight: 600;
		line-height: 1.3;
	}

	.partner-subtitle {
		margin-top: 8rpx;
		color: #6f6979;
		font-size: 22rpx;
		line-height: 1.4;
	}

	.debt-box {
		flex: 0 0 auto;
		text-align: right;
	}

	.debt-label {
		color: #9a95a4;
		font-size: 20rpx;
	}

	.debt-value {
		margin-top: 4rpx;
		color: #722ed1;
		font-size: 27rpx;
		font-weight: 600;
	}

	.tag-row {
		display: flex;
		flex-wrap: wrap;
		gap: 10rpx;
		margin-top: 12rpx;
	}

	.tag-item {
		max-width: 100%;
		padding: 4rpx 10rpx;
		overflow: hidden;
		color: #6b6676;
		font-size: 19rpx;
		text-overflow: ellipsis;
		white-space: nowrap;
		background: #f7f5fb;
		border-radius: 8rpx;
	}

	.footer-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12rpx;
		margin-top: 14rpx;
		padding-top: 12rpx;
		border-top: 1rpx solid #eeeaf4;
	}

	.status-tag {
		padding: 6rpx 14rpx;
		font-size: 20rpx;
		border-radius: 20rpx;
	}

	.status-tag.enabled {
		color: #00a870;
		background: #e8f8f2;
	}

	.status-tag.disabled {
		color: #e5484d;
		background: #fff0f0;
	}

	.create-time {
		color: #9a95a4;
		font-size: 20rpx;
	}

	.filter-popup {
		padding: 28rpx 28rpx calc(22rpx + env(safe-area-inset-bottom));
		background: #fff;
		border-radius: 28rpx 28rpx 0 0;
	}

	.popup-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 26rpx;
	}

	.popup-title {
		color: #30283d;
		font-size: 32rpx;
		font-weight: 600;
	}

	.popup-form-item {
		margin-bottom: 22rpx;
	}

	.popup-label {
		display: block;
		margin-bottom: 10rpx;
		color: #6b6676;
		font-size: 22rpx;
	}

	.popup-picker {
		display: flex;
		align-items: center;
		justify-content: space-between;
		width: 100%;
		height: 72rpx;
		padding: 0 20rpx;
		box-sizing: border-box;
		color: #393044;
		font-size: 25rpx;
		background: #fff;
		border: 1rpx solid #ded9e8;
		border-radius: 10rpx;
	}

	.placeholder {
		color: #aaa5b5;
	}

	.popup-actions {
		display: flex;
		gap: 18rpx;
		margin-top: 30rpx;
	}

	.popup-actions button {
		flex: 1;
		height: 78rpx;
		margin: 0;
		font-size: 26rpx;
		line-height: 78rpx;
		border-radius: 10rpx;
	}

	.popup-cancel-button {
		color: #666;
		background: #f2f3f5;
	}

	.popup-confirm-button {
		color: #fff;
		background: #722ed1;
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

	.bottom-space {
		height: 30rpx;
	}
</style>
