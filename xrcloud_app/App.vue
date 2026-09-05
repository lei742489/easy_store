<script>
import { initClientVersion } from './common/config'

export default {
  onLaunch() {
    console.log('App Launch')
    initClientVersion()
  },
  onShow() {
    console.log('App Show')
  },
  onHide() {
    console.log('App Hide')
  }
}
</script>

<style>
/* 全局样式入口 */
</style>
