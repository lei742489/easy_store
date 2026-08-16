<template>
  <div class="banner">
    <div class="banner-inner">
      <a-carousel
        class="carousel"
        auto-play
        :interval="6000"
        animation-name="slide"
        show-arrow="never"
      >
        <a-carousel-item v-for="item in carouselItem" :key="item.slogan">
          <div :key="item.slogan" class="carousel-item">
            <div class="carousel-title">{{ item.slogan }}</div>
            <div class="carousel-sub-title">{{ item.subSlogan }}</div>
            <!--            <img class="carousel-image" :src="item.image" />-->
          </div>
        </a-carousel-item>
      </a-carousel>
    </div>
    <div class="mask"></div>
  </div>
</template>

<script lang="ts" setup>
  import { computed } from 'vue';
  import { useI18n } from 'vue-i18n';
  import bannerImage from '@/assets/images/login-banner.png';
  import appConfig from '@/config/app';

  const { appName } = appConfig;
  const { t } = useI18n();
  const carouselItem = computed(() => [
    {
      slogan: t('login.banner.slogan1', { appName }),
      subSlogan: t('login.banner.subSlogan1'),
      image: bannerImage,
    },
    {
      slogan: t('login.banner.slogan2'),
      subSlogan: t('login.banner.subSlogan2'),
      image: bannerImage,
    },
    {
      slogan: t('login.banner.slogan3'),
      subSlogan: t('login.banner.subSlogan3'),
      image: bannerImage,
    },
    {
      slogan: t('login.banner.slogan4'),
      subSlogan: t('login.banner.subSlogan4'),
      image: bannerImage,
    },
  ]);
</script>

<style lang="less" scoped>
  .banner {
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    -webkit-app-region: drag;
    .mask {
      width: 100%;
      height: 100%;
      position: absolute;
      z-index: 5;
      left: 0;
      top: 0;
      background: rgba(0, 0, 0, 0.1);
    }

    &-inner {
      flex: 1;
      height: 100%;
    }
  }

  .carousel {
    height: 100%;
    z-index: 10;
    &-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
    }

    &-title {
      color: #5941cd;
      font-weight: 500;
      font-size: 30px;
      line-height: 28px;
      text-shadow: 0 0 20px rgba(200, 200, 200, 1);
      user-select: none;
    }

    &-sub-title {
      user-select: none;
      margin-top: 12px;
      color: #fff;
      font-size: 21px;
      line-height: 22px;
      text-shadow: 0 0 10px rgba(89, 65, 205, 1);
    }

    &-image {
      width: 320px;
      margin-top: 30px;
    }
  }
</style>
