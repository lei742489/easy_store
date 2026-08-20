import type { Router, LocationQueryRaw } from 'vue-router';
import NProgress from 'nprogress'; // progress bar

import { useUserStore } from '@/store';
import { DEFAULT_ROUTE_NAME } from '@/router/constants';
import { isLogin } from '@/utils/auth';

export default function setupUserLoginInfoGuard(router: Router) {
  router.beforeEach(async (to, from, next) => {
    NProgress.start();
    const userStore = useUserStore();
    if (to.path === '/') {
      next({
        name: isLogin() ? DEFAULT_ROUTE_NAME : 'login',
        replace: true,
      });
      return;
    }
    if (isLogin()) {
      const needsRefresh =
        !userStore.role ||
        userStore.isRoot === undefined ||
        userStore.isRoot === null;
      if (!needsRefresh) {
        next();
      } else {
        try {
          await userStore.info();
          next();
        } catch (error) {
          await userStore.logout();
          next({
            name: 'login',
            query: {
              redirect: to.name,
              ...to.query,
            } as LocationQueryRaw,
          });
        }
      }
    } else {
      if (to.name === 'login') {
        next();
        return;
      }
      next({
        name: 'login',
        query: {
          redirect: to.name,
          ...to.query,
        } as LocationQueryRaw,
      });
    }
  });
}
