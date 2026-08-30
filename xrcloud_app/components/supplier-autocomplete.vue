<template>
  <view class="supplier-select">
    <view class="input-wrap" @click="openSelectPage">
      <text class="supplier-text" :class="{ placeholder: !modelValue }">
        {{ modelValue || placeholder }}
      </text>
      <uni-icons
        v-if="modelValue"
        class="clear-icon"
        type="clear"
        color="#aaa5b5"
        :size="18"
        @click.stop="clearValue"
      />
      <uni-icons type="right" color="#bbb4c7" :size="18" />
    </view>
  </view>
</template>

<script>
export default {
  props: {
    modelValue: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: '名称 / 联系人 / 手机 / 拼音'
    }
  },
  emits: ['update:modelValue', 'select'],
  methods: {
    openSelectPage() {
      uni.navigateTo({
        url: `/pages/partner-select/index?mode=supplier&title=${encodeURIComponent('供应商查询')}&keyword=${encodeURIComponent(this.modelValue || '')}`,
        success: (res) => {
          const channel = res && res.eventChannel
          if (!channel) return
          channel.on('select', (payload) => {
            if (!payload) return
            const value = String(payload.name || payload.id || '').trim()
            this.$emit('update:modelValue', value)
            this.$emit('select', value)
          })
        }
      })
    },
    clearValue() {
      this.$emit('update:modelValue', '')
      this.$emit('select', '')
    }
  }
}
</script>

<style lang="scss" scoped>
.supplier-select {
  position: relative;
  width: 100%;
}

.input-wrap {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 72rpx;
  padding: 0 18rpx;
  box-sizing: border-box;
  background: #fff;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}

.supplier-text {
  flex: 1;
  min-width: 0;
  margin-right: 12rpx;
  overflow: hidden;
  color: #393044;
  font-size: 25rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.placeholder {
  color: #aaa5b5;
}

.clear-icon {
  margin-right: 8rpx;
}
</style>
