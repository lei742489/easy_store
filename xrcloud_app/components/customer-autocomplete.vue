<template>
  <view class="customer-autocomplete">
    <view class="input-wrap">
      <input
        :value="modelValue"
        class="customer-input"
        type="text"
        :placeholder="placeholder"
        confirm-type="search"
        @input="handleInput"
        @focus="handleFocus"
        @confirm="handleConfirm"
        @blur="handleBlur"
      />
      <uni-icons
        v-if="loading"
        class="loading-icon"
        type="spinner-cycle"
        color="#722ed1"
        :size="18"
      />
      <uni-icons
        v-else-if="modelValue"
        class="clear-icon"
        type="clear"
        color="#aaa5b5"
        :size="18"
        @click="clearValue"
      />
    </view>

    <view v-if="showOptions && options.length" class="options-panel">
      <view
        v-for="(option, index) in options"
        :key="`${option}-${index}`"
        class="option-item"
        @touchstart.stop.prevent="selectOption(option)"
        @click.stop="selectOption(option)"
      >
        <uni-icons type="person" color="#722ed1" :size="17" />
        <text>{{ option }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import { searchCustomers } from '../common/api'

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
  emits: ['update:modelValue', 'select', 'confirm'],
  data() {
    return {
      options: [],
      loading: false,
      showOptions: false,
      searchTimer: null,
      searchRequestId: 0
    }
  },
  beforeUnmount() {
    this.clearSearchTimer()
  },
  methods: {
    clearSearchTimer() {
      if (this.searchTimer) {
        clearTimeout(this.searchTimer)
        this.searchTimer = null
      }
    },
    handleInput(event) {
      const value = String(event.detail && event.detail.value ? event.detail.value : '')
      this.$emit('update:modelValue', value)
      this.showOptions = Boolean(value.trim())
      this.clearSearchTimer()
      if (!value.trim()) {
        this.options = []
        return
      }
      this.searchTimer = setTimeout(() => this.search(value), 250)
    },
    handleFocus() {
      if (String(this.modelValue || '').trim() && this.options.length) {
        this.showOptions = true
      }
    },
    handleConfirm() {
      this.$emit('confirm', this.modelValue)
    },
    async search(value) {
      const keyword = String(value || '').trim()
      if (!keyword) return
      const requestId = ++this.searchRequestId
      this.loading = true
      try {
        const result = await searchCustomers(keyword)
        if (requestId !== this.searchRequestId) return
        this.options = Array.isArray(result) ? result.filter(Boolean) : []
        this.showOptions = this.options.length > 0
      } catch (error) {
        if (requestId === this.searchRequestId) this.options = []
      } finally {
        if (requestId === this.searchRequestId) this.loading = false
      }
    },
    selectOption(option) {
      const value = String(option || '').trim()
      this.$emit('update:modelValue', value)
      this.$emit('select', value)
      this.options = []
      this.showOptions = false
    },
    clearValue() {
      this.searchRequestId += 1
      this.clearSearchTimer()
      this.options = []
      this.showOptions = false
      this.$emit('update:modelValue', '')
      this.$emit('select', '')
    },
    handleBlur() {
      // 延迟关闭，保证触摸选项时先完成选中事件。
      setTimeout(() => {
        this.showOptions = false
      }, 180)
    }
  }
}
</script>

<style lang="scss" scoped>
.customer-autocomplete {
  position: relative;
  width: 100%;
}

.input-wrap {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
}

.customer-input {
  width: 100%;
  height: 72rpx;
  padding: 0 54rpx 0 20rpx;
  box-sizing: border-box;
  color: #393044;
  font-size: 25rpx;
  background: #fff;
  border: 1rpx solid #ded9e8;
  border-radius: 10rpx;
}

.loading-icon,
.clear-icon {
  position: absolute;
  right: 18rpx;
}

.clear-icon { z-index: 2; }

.options-panel {
  position: absolute;
  top: 80rpx;
  right: 0;
  left: 0;
  z-index: 30;
  max-height: 360rpx;
  overflow-y: auto;
  background: #fff;
  border: 1rpx solid #e3deeb;
  border-radius: 10rpx;
  box-shadow: 0 10rpx 28rpx rgba(48, 39, 75, .16);
}

.option-item {
  display: flex;
  align-items: center;
  min-height: 72rpx;
  padding: 0 20rpx;
  box-sizing: border-box;
  color: #4d4758;
  font-size: 24rpx;
  border-bottom: 1rpx solid #f1eef5;
}

.option-item:last-child { border-bottom: 0; }
.option-item text { margin-left: 10rpx; }
.option-item:active { background: #f5f0fc; }
</style>
