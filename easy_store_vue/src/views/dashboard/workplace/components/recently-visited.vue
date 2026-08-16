<template>
  <a-card
    class="general-card"
    :title="$t('workplace.recently.visited')"
    :header-style="{ paddingBottom: '0' }"
    :body-style="{ paddingTop: '26px' }"
  >
    <div style="margin-bottom: -1rem">
      <a-row :gutter="8">
        <a-col
          v-for="link in links"
          :key="link.text"
          :span="8"
          class="wrapper"
          @click="itemClick(link.url)"
        >
          <div class="icon">
            <component :is="link.icon" />
          </div>
          <a-typography-paragraph class="text">
            {{ $t(link.text) }}
          </a-typography-paragraph>
        </a-col>
      </a-row>
    </div>
  </a-card>
</template>

<script lang="ts" setup>
  import { useRouter } from 'vue-router';

  const router = useRouter();
  const links = [
    {
      text: 'menu.custom.appUserManagement',
      icon: 'icon-user-group',
      url: '/custom/appUser',
    },
    {
      text: 'menu.custom.appUnit',
      icon: 'icon-storage',
      url: '/custom/appUnit',
    },
    {
      text: 'menu.custom.setting',
      icon: 'icon-settings',
      url: '',
    },
  ];

  function itemClick(url: string) {
    if (url) {
      router.push(url);
    }
  }
</script>

<style lang="less" scoped>
  :deep(.arco-card-header-title) {
    line-height: inherit;
  }
</style>
