<template>
  <scroll-view class="form-scroll" scroll-y :show-scrollbar="false">
    <view class="form-content">
      <view class="form-section">
        <text class="section-title">付款单信息</text>
        <view class="section-body">
          <view class="placeholder-line" />
          <view class="placeholder-line short" />
        </view>
      </view>

      <view class="form-section">
        <text class="section-title">关联进货单</text>
        <view class="section-body large">
          <view class="placeholder-line" />
          <view class="placeholder-line" />
          <view class="placeholder-line short" />
        </view>
      </view>

      <view class="form-section">
        <text class="section-title">付款账户</text>
        <view class="section-body">
          <view class="placeholder-line" />
          <view class="placeholder-line short" />
        </view>
      </view>

      <view class="bottom-space" />
    </view>
  </scroll-view>
</template>

<script>
export default {
  name: 'BusinessPaymentForm'
}
</script>

<style lang="scss" scoped>
@import './form-placeholder.scss';
</style>
