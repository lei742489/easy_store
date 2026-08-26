<template>
	<view class="status-bar" :style="`height:${height}px;width:${devWidth}px;`">
		
	</view>
</template>

<script>
	export default {
		name:"uni-status-bar",
		props:{
			color:{
				type:String,
				default:""
			}
		},
		data() {
			return {
				height:uni.getSystemInfoSync().statusBarHeight,
				devWidth:uni.getSystemInfoSync().windowWidth,
			};
		}
	}
</script>

<style lang="less">
	.status-bar{
		position: fixed;
		top: 0;
		left: 0;
		z-index: 999999;
	}
</style>