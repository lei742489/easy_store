<template>
  <a-card :bordered="false">
    <a-space :size="8">
      <div class="head-url">
        <image-upload
          v-if="userStore.avatar"
          ref="uploadRef"
          v-model="userStore.avatar"
        />
      </div>

      <a-descriptions
        :data="renderData"
        :column="2"
        align="right"
        layout="inline-horizontal"
        :label-style="{
          width: '140px',
          fontWeight: 'normal',
          color: 'rgb(var(--gray-8))',
        }"
        :value-style="{
          width: '200px',
          paddingLeft: '8px',
          textAlign: 'left',
        }"
      >
        <template #label="{ label }">{{ label }} :</template>
        <template #value="{ value, data }">
          <a-tag v-if="data.label === '是否认证'" color="green" size="small">
            已认证
          </a-tag>
          <span v-else>{{ value }}</span>
        </template>
      </a-descriptions>
    </a-space>
  </a-card>
</template>

<script lang="ts" setup>
  import { computed, nextTick, ref, watch } from 'vue';
  import { useUserStore } from '@/store';
  import { BasicInfoModel } from '@/api/user-center';
  import ImageUpload from '@/components/upload/image-upload.vue';
  import { edit as userEdit } from '@/api/user';

  const userStore = useUserStore();
  const isRoot = computed(() => userStore.isRoot === 1);
  const uploadRef = ref<InstanceType<typeof ImageUpload> | null>(null);

  watch(
    () => userStore.avatar,
    async (newValue) => {
      if (!newValue) return;
      await nextTick();
      uploadRef.value?.init(newValue);
    },
    { immediate: true }
  );
  const renderData = computed(() => {
    if (!isRoot.value) {
      return [
        {
          label: '姓名',
          value: userStore.realName,
        },
        {
          label: '邮箱',
          value: userStore.email,
        },
        {
          label: '电话',
          value: userStore.mobile,
        },
      ];
    }

    return [
      {
        label: '用户名',
        value: userStore.userName,
      },
      {
        label: '是否认证',
        value: userStore.certification,
      },
      {
        label: '公司/店铺名称',
        value: userStore.companyName,
      },
      {
        label: '联系电话',
        value: userStore.mobile,
      },
      {
        label: '店铺地址',
        value: userStore.address,
      },
      {
        label: '注册时间',
        value: userStore.createTime,
      },
    ];
  });

  watch(
    () => userStore.avatar,
    async (newValue) => {
      await userEdit({
        id: userStore.id,
        avatar: newValue,
      } as BasicInfoModel);
    }
  );
</script>

<style scoped lang="less">
  .arco-card {
    padding: 14px 0 4px 4px;
    border-radius: 4px;
  }
  :deep(.arco-avatar-trigger-icon-button) {
    width: 32px;
    height: 32px;
    line-height: 32px;
    background-color: #e8f3ff;
    .arco-icon-camera {
      margin-top: 8px;
      color: rgb(var(--arcoblue-6));
      font-size: 14px;
    }
  }
</style>
