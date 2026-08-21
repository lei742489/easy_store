<template>
  <a-space direction="vertical" :style="{ width: '100%' }">
    <a-upload
      :action="actionUrl"
      :file-list="file ? [file] : []"
      :show-file-list="false"
      accept="image/*"
      :image-preview="true"
      class="upload-view"
      @change="onChange"
      @progress="onProgress"
      @success="uploadSuccess"
    >
      <template #upload-button>
        <div
          :class="`arco-upload-list-item${
            file && file.status === 'error'
              ? ' arco-upload-list-item-error'
              : ''
          }`"
        >
          <div
            v-if="file && file.url"
            class="arco-upload-list-picture custom-upload-avatar"
          >
            <img :src="file.url" />
            <div class="arco-upload-list-picture-mask">
              <IconEdit />
            </div>
            <a-progress
              v-if="file.status === 'uploading' && file.percent < 100"
              :percent="file.percent"
              type="circle"
              size="mini"
              :style="{
                position: 'absolute',
                left: '50%',
                top: '50%',
                transform: 'translateX(-50%) translateY(-50%)',
              }"
            />
          </div>
          <div v-else class="arco-upload-picture-card">
            <div class="arco-upload-picture-card-text">
              <IconPlus />
              <div style="margin-top: 6px; font-size: 13px; font-weight: 600"
                >点击上传</div
              >
            </div>
          </div>
        </div>
      </template>
    </a-upload>
  </a-space>
</template>

<script lang="ts">
  import { IconEdit, IconPlus } from '@arco-design/web-vue/es/icon';
  import { defineComponent, ref, watch } from 'vue';
  import { Message } from '@arco-design/web-vue';

  export default defineComponent({
    components: { IconPlus, IconEdit },
    props: {
      modelValue: {
        type: String,
        default: '',
      },
    },
    emits: ['update:modelValue'],

    setup(props, { emit, expose }) {
      const file = ref<any>();
      const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
      const baseImgUrl = ref(`${apiBaseUrl}/api/upload/static`);
      const actionUrl = ref(`${apiBaseUrl}/api/file/upload`);

      const fixedImageUrl = (url?: string) => {
        if (!url) return '';
        if (/^(https?:)?\/\//i.test(url) || /^(blob|data):/i.test(url)) {
          return url;
        }
        return `${baseImgUrl.value}${url.startsWith('/') ? url : `/${url}`}`;
      };

      watch(
        () => props.modelValue,
        (val) => {
          file.value = val ? { url: fixedImageUrl(val), status: 'done' } : undefined;
        },
        { immediate: true }
      );

      const init = (url: string) => {
        file.value = url ? { url: fixedImageUrl(url), status: 'done' } : undefined;
      };

      const onChange = (_: unknown, currentFile: unknown) => {};
      const onProgress = (currentFile: unknown) => {
        file.value = currentFile;
      };
      const uploadSuccess = (e: any) => {
        if (e.response.success) {
          file.value = { url: fixedImageUrl(e.response.data), status: 'done' };
          emit('update:modelValue', e.response.data);
        } else {
          Message.error({
            content: e.response.message || '请求错误',
            duration: 5 * 1000,
          });
        }
      };

      expose({
        init,
      });

      return {
        actionUrl,
        baseImgUrl,
        uploadSuccess,
        file,
        init,
        onChange,
        onProgress,
      };
    },
  });
</script>

<style lang="less"></style>
