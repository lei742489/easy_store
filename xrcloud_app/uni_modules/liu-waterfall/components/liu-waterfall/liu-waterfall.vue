<template>
	<view class="waterfall" :style="'background-color:'+bgColor">
		<view v-for="(item, index) in columnData" :key="index" class="column" :style="{ width: columnWidth + 'px' }">
			<view v-for="(childItem, childIndex) in item" :key="childIndex" style="width: 100%"
				:id="'item' + childItem.id" @click.stop="click(childItem)">
				<view class="item"
					:style="'background-color:'+cardBgColor+';margin:'+margin+'rpx;border-radius:'+radius+'rpx;'">
					<image :src="childItem.picUrl" mode="widthFix" lazy-load
						:style="{height:childItem.height,width: '100%'}">
					</image>
					<view class="title-info">
						<view class="item-title">{{ childItem.title }}</view>
						<view class="item-desc">{{ childItem.desc }}</view>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	export default {
		props: {
			//数据源
			dataList: {
				type: Array,
				required: true,
				default: []
			},
			//显示列数
			column: {
				type: Number,
				required: true,
				default: 2
			},
			//卡片margin(rpx)
			margin: {
				type: Number,
				default: 10
			},
			//卡片圆角(rpx)
			radius: {
				type: Number,
				default: 8
			},
			//页面背景颜色
			bgColor: {
				type: String,
				default: '#edeef2'
			},
			//卡片背景颜色
			cardBgColor: {
				type: String,
				default: '#FFFFFF'
			},
		},
		data() {
			return {
				columnData: [],
				columnWidth: 0,
				loading: false,
			};
		},
		watch: {
			dataList: {
				immediate: true,
				deep: true,
				handler(newValue, oldValue) {
					this.$nextTick(()=>{
						this.setColumnWidth()
						this.setData()
					})
				},
			},
			column: {
				immediate: false,
				deep: true,
				handler(newValue) {
					this.$nextTick(()=>{
						this.setColumnWidth()
						this.setData()
					})
				},
			},
		},
		methods: {
			//计算每列的高度
			getElemHeight(index) {
				this.$nextTick(() => {
					var arr = [];
					this.dataList.map((item, index) => {
						uni.getImageInfo({
							src: item.picUrl,
							success: (e) => {
								item.height = (e.height * (this.columnWidth / e.width)) + 'px'
								this.createSelectorQuery().select('#item' + item.id)
									.boundingClientRect(res => {
										arr.push({
											...{
												itemHeight: res.height
											},
											...item
										});
										if (arr.length == this.dataList.length) {
											this.columnData = this.distributeToNArrays(arr,
												this.column);
										}
									}).exec();
							}
						})
					})
				})
			},
			distributeToNArrays(arr, n) {
				let sums = new Array(n).fill(0);
				return arr.reduce(
					(arrays, item) => {
						let minSum = Math.min(...sums);
						let minIndex = sums.indexOf(minSum);
						arrays[minIndex].push(item);
						sums[minIndex] += item.itemHeight;
						return arrays;
					},
					new Array(n).fill().map(() => []),
				)
			},
			setColumnWidth() {
				let width = uni.getSystemInfoSync().windowWidth
				this.columnWidth = Math.floor(width / this.column)
			},
			setData() {
				const resultArray = this.dataList.reduce(
					(acc, cur, index) => {
						const targetIndex = index % this.column;
						acc[targetIndex].push(cur);
						return acc;
					},
					Array.from(Array(this.column), () => []),
				);
				this.columnData = resultArray
				this.getElemHeight()
			},
			click(item) {
				this.$emit('click', item)
			}
		},
	};
</script>

<style scoped>
	.waterfall {
		display: flex;
		flex-direction: row;
		flex-wrap: wrap;
	}

	.column {
		display: flex;
		flex-direction: column;
		align-items: center;
	}

	.item {
		overflow: hidden;
	}

	.title-info {
		padding: 0rpx 20rpx 20rpx 20rpx;
	}

	.item-title {
		font-size: 30rpx;
		color: #333333;
		line-height: 46rpx;
		text-align: justify;
		overflow: hidden;
		text-overflow: ellipsis;
		display: -webkit-box;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 1;
	}

	.item-desc {
		margin-top: 4rpx;
		font-size: 26rpx;
		color: #666666;
		line-height: 34rpx;
		text-align: justify;
		overflow: hidden;
		text-overflow: ellipsis;
		display: -webkit-box;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 2;
	}
</style>