<template>
  <partner-statement
    ref="statementRef"
    mode="receivable"
    :initial-partner-id="partnerId"
    :initial-start-date="startDate"
    :initial-end-date="endDate"
  />
</template>

<script>
import PartnerStatement from '../../components/partner-statement.vue'

const pad = (value) => String(value).padStart(2, '0')

const monthRange = () => {
  const date = new Date()
  const endDate = new Date(date.getFullYear(), date.getMonth() + 1, 0)
  return {
    start: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-01`,
    end: `${endDate.getFullYear()}-${pad(endDate.getMonth() + 1)}-${pad(endDate.getDate())}`
  }
}

export default {
  components: {
    PartnerStatement
  },
  data() {
    const range = monthRange()
    return {
      partnerId: '',
      startDate: range.start,
      endDate: range.end
    }
  },
  onLoad(options) {
    this.partnerId = options && options.id ? String(options.id) : ''
    if (options && options.startDate) this.startDate = String(options.startDate)
    if (options && options.endDate) this.endDate = String(options.endDate)
  }
}
</script>
