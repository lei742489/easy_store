<template>
  <partner-debt-detail
    mode="debt"
    :partner-id="partnerId"
    :start-date="startDate"
    :end-date="endDate"
  />
</template>

<script>
import PartnerDebtDetail from '../../components/partner-debt-detail.vue'

const startOfMonth = () => {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01`
}

const endOfMonth = () => {
  const date = new Date()
  const end = new Date(date.getFullYear(), date.getMonth() + 1, 0)
  return `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, '0')}-${String(end.getDate()).padStart(2, '0')}`
}

export default {
  components: {
    PartnerDebtDetail
  },
  data() {
    return {
      partnerId: '',
      startDate: startOfMonth(),
      endDate: endOfMonth()
    }
  },
  onLoad(options) {
    this.partnerId = options && options.id ? String(options.id) : ''
    if (options && options.startDate) {
      this.startDate = String(options.startDate)
    }
    if (options && options.endDate) {
      this.endDate = String(options.endDate)
    }
  }
}
</script>
