<template>
  <view class="page">
     <view class="filter-panel">
      <view v-if="isLog" class="log-filter">
        <view class="filter-line">
          <input v-model="filters.operatorName" class="quick-input" placeholder="操作员名称" confirm-type="search" @confirm="search" />
          <input v-model="filters.menuName" class="quick-input" placeholder="菜单名称" confirm-type="search" @confirm="search" />
        </view>
        <view class="filter-line">
          <picker class="operation-picker" :range="operationTypes" range-key="label" :value="operationTypeIndex" @change="changeOperationType">
            <view class="picker-field"><text>{{ operationTypes[operationTypeIndex].label }}</text><uni-icons type="down" color="#999" :size="16" /></view>
          </picker>
          <uni-datetime-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" :clear-icon="true" @change="changeDate" />
        </view>
      </view>
      <view v-else class="quick-search-row">
        <input v-model="keyword" class="quick-input" :placeholder="searchPlaceholder" confirm-type="search" @confirm="search" />
        <button class="search-button" :disabled="loading" @click="search"><uni-icons type="search" color="#fff" :size="18" /><text>查询</text></button>
      </view>
      <view class="toolbar">
        <text class="page-title">{{ pageTitle }}</text>
        <view class="toolbar-actions">
          <button v-if="!isLog" class="add-button" @click="openForm()"><uni-icons type="plusempty" color="#fff" :size="17" /><text>新增</text></button>
          <button class="reset-button" :disabled="loading" @click="reset"><uni-icons type="refresh" color="#666" :size="17" /><text>重置</text></button>
          <button v-if="isLog" class="search-button" :disabled="loading" @click="search"><uni-icons type="search" color="#fff" :size="17" /><text>查询</text></button>
        </view>
      </view>
    </view>

    <view v-if="records.length" class="record-list">
      <view v-for="(record, index) in records" :key="String(record.id || index)" class="record-card">
        <view v-if="mode === 'user'" class="record-header">
          <view class="record-main">
            <view class="record-icon"><uni-icons type="person-filled" color="#722ed1" :size="25" /></view>
            <view class="record-title-wrap">
              <text class="record-title">{{ record.realName || '-' }}</text>
              <text class="record-subtitle">{{ record.userName || '-' }}{{ record.roleId_dictText ? ` · ${record.roleId_dictText}` : '' }}</text>
            </view>
          </view>
          <text class="status" :class="{ disabled: Number(record.status) !== 1 }">{{ Number(record.status) === 1 ? '启用' : '停用' }}</text>
        </view>

        <view v-else-if="mode === 'role'" class="record-header">
          <view class="record-main">
            <view class="record-icon"><uni-icons type="staff-filled" color="#722ed1" :size="25" /></view>
            <view class="record-title-wrap"><text class="record-title">{{ record.name || '-' }}</text><text class="record-subtitle">{{ record.remarks || '暂无备注' }}</text></view>
          </view>
          <text class="status" :class="{ disabled: Number(record.status) !== 1 }">{{ Number(record.status) === 1 ? '启用' : '停用' }}</text>
        </view>

        <view v-else-if="mode === 'unit'" class="record-header">
          <view class="record-main">
            <view class="record-icon"><uni-icons type="list" color="#722ed1" :size="25" /></view>
            <view class="record-title-wrap"><text class="record-title">{{ record.name || '-' }}</text><text class="record-subtitle">常用单位</text></view>
          </view>
        </view>

        <view v-else class="record-header">
          <view class="record-main">
            <view class="record-icon"><uni-icons type="compose" color="#722ed1" :size="25" /></view>
            <view class="record-title-wrap"><text class="record-title">{{ record.menuName || '-' }}</text><text class="record-subtitle">{{ record.operatorName || '-' }} · {{ formatDate(record.operateTime) }}</text></view>
          </view>
          <text class="log-type" :class="`type-${record.operationType}`">{{ operationTypeText(record.operationType) }}</text>
        </view>

        <view v-if="mode === 'user'" class="info-grid">
          <view class="info-item"><text class="info-label">手机号</text><text class="info-value">{{ record.mobile || '-' }}</text></view>
          <view class="info-item"><text class="info-label">提成比例</text><text class="info-value">{{ formatNumber(record.commissionRate) }}%</text></view>
        </view>
        <view v-if="isLog" class="info-grid">
          <view class="info-item"><text class="info-label">请求地址</text><text class="info-value">{{ record.requestUri || '-' }}</text></view>
          <view class="info-item"><text class="info-label">IP</text><text class="info-value">{{ record.clientIp || '-' }}</text></view>
        </view>
        <view v-if="(mode === 'user' || mode === 'role') && record.remarks" class="note-row"><text class="note-label">备注</text><text class="note-text">{{ record.remarks }}</text></view>

        <view v-if="mode === 'user' || mode === 'role' || mode === 'unit'" class="card-actions">
          <button class="text-button" @click.stop="openForm(record)">编辑</button>
          <button v-if="Number(record.isRoot) !== 1" class="text-button danger" @click.stop="confirmRemove(record)">删除</button>
          <button v-if="mode === 'user'" class="text-button" @click.stop="confirmResetPassword(record)">重置密码</button>
        </view>
        <button v-if="isLog" class="data-button" @click.stop="showLogData(record.dataJson)">查看操作数据</button>
      </view>
    </view>

    <view v-else-if="!loading" class="empty-state"><uni-icons type="info" color="#aaa5b5" :size="52" /><text>{{ emptyText }}</text></view>
    <uni-load-more v-if="records.length || loading" :status="loadStatus" />
    <view class="bottom-space" />

    <uni-popup ref="formPopup" type="bottom" :safe-area="true" :is-mask-click="false">
      <view class="form-popup">
        <view class="popup-header"><text class="popup-title">{{ editingId ? `编辑${pageTitle}` : `新增${pageTitle}` }}</text><uni-icons type="closeempty" color="#999" :size="22" @click="closeForm" /></view>

        <view v-if="mode === 'user'">
          <view class="form-item"><text class="form-label">登录账号</text><input v-model="form.userName" class="form-input" :disabled="Boolean(editingId)" placeholder="请输入登录账号" /></view>
          <view class="form-item"><text class="form-label">姓名</text><input v-model="form.realName" class="form-input" placeholder="请输入姓名" /></view>
          <view v-if="!editingId" class="form-item"><text class="form-label">初始密码</text><input v-model="form.password" class="form-input" password placeholder="至少6位" /></view>
          <view class="form-item"><text class="form-label">角色</text><picker :range="roleOptions" range-key="name" :value="roleIndex" @change="changeRole"><view class="picker-field"><text :class="{ placeholder: !form.roleId }">{{ selectedRoleName }}</text><uni-icons type="down" color="#999" :size="16" /></view></picker></view>
          <view class="form-row">
            <view class="form-item half"><text class="form-label">手机号</text><input v-model="form.mobile" class="form-input" placeholder="手机号" /></view>
            <view class="form-item half"><text class="form-label">提成比例</text><input v-model="form.commissionRate" class="form-input" type="digit" placeholder="0" /></view>
          </view>
          <view class="form-item"><text class="form-label">备注</text><textarea v-model="form.remarks" class="form-textarea" placeholder="请输入备注" /></view>
        </view>

        <view v-else-if="mode === 'role'">
          <view class="form-item"><text class="form-label">角色名称</text><input v-model="form.name" class="form-input" placeholder="请输入角色名称" /></view>
          <view class="form-item"><text class="form-label">状态</text><view class="segmented"><button :class="{ active: Number(form.status) === 1 }" @click="form.status = 1">启用</button><button :class="{ active: Number(form.status) !== 1 }" @click="form.status = 0">停用</button></view></view>
          <view class="form-item"><text class="form-label">备注</text><textarea v-model="form.remarks" class="form-textarea" placeholder="请输入备注" /></view>
          <view class="form-item">
            <text class="form-label">菜单权限</text>
            <checkbox-group class="permission-list" @change="handlePermissionChange">
              <label v-for="item in permissionNodes" :key="item.value" class="permission-item" :class="{ child: item.kind === 'action' }"><checkbox :value="item.value" :checked="checkedPermissionValues.indexOf(item.value) > -1" color="#722ed1" /><text>{{ item.label }}</text></label>
            </checkbox-group>
          </view>
          <view class="form-item">
            <text class="form-label">数据查看权限</text>
            <checkbox-group class="permission-list" @change="handleDataViewPermissionChange">
              <label v-for="item in dataViewPermissions" :key="item.value" class="permission-item"><checkbox :value="item.value" :checked="dataViewPermissionCodes.indexOf(item.value) > -1" color="#722ed1" /><text>{{ item.label }}</text></label>
            </checkbox-group>
          </view>
        </view>

        <view v-else-if="mode === 'unit'" class="form-item"><text class="form-label">单位名称</text><input v-model="form.name" class="form-input" placeholder="请输入单位名称" /></view>
        <view class="popup-actions"><button class="cancel-button" :disabled="saving" @click="closeForm">取消</button><button class="confirm-button" :disabled="saving" @click="submitForm">{{ saving ? '保存中...' : '保存' }}</button></view>
      </view>
    </uni-popup>

    <uni-popup ref="dataPopup" type="center" :is-mask-click="true">
      <view class="data-popup"><view class="popup-header"><text class="popup-title">操作数据</text><uni-icons type="closeempty" color="#999" :size="22" @click="closeData" /></view><scroll-view scroll-y class="json-scroll"><text class="json-text">{{ selectedJson }}</text></scroll-view></view>
    </uni-popup> 
  </view>
</template>

<script>
import { getUser, isLoggedIn, clearSession, updateUser } from '../common/auth'
import { getUserInfo, listAppUserPage, addAppUser, editAppUser, removeAppUser, resetAppUserPassword, listAppRolePage, listAppRoles, addAppRole, editAppRole, removeAppRole, listAllAppMenus, listAppRoleMenuIds, listAppRolePermissionCodes, listAppUnitPage, addAppUnit, editAppUnit, removeAppUnit, listAppOperationLogPage } from '../common/api'

const pad = (value) => String(value).padStart(2, '0')
const monthRange = () => {
  const date = new Date()
  const end = new Date(date.getFullYear(), date.getMonth() + 1, 0)
  return [`${date.getFullYear()}-${pad(date.getMonth() + 1)}-01`, `${end.getFullYear()}-${pad(end.getMonth() + 1)}-${pad(end.getDate())}`]
}
const createForm = (mode) => mode === 'user'
  ? { userName: '', realName: '', password: '', roleId: '', mobile: '', commissionRate: 0, remarks: '', status: 1 }
  : mode === 'role' ? { name: '', remarks: '', status: 1 } : { name: '' }

export default {
  props: { mode: { type: String, default: 'user' } },
  data() {
    return {
      user: getUser() || {}, keyword: '', records: [], current: 1, pageSize: 20, total: 0,
      loading: false, saving: false, initialized: false, initializing: false, redirecting: false,
      editingId: '', form: createForm(this.mode), roleOptions: [], roleIndex: 0, permissionNodes: [],
      checkedPermissionValues: [], dataViewPermissionCodes: [],
      dataViewPermissions: [
        { label: '成本价', value: 'data_view:cost_price' }, { label: '进货价', value: 'data_view:purchase_price' },
        { label: '批发价', value: 'data_view:trade_price' }, { label: '销售价', value: 'data_view:sale_price' }
      ],
      filters: { operatorName: '', menuName: '', operationType: '', clientIp: '' },
      operationTypes: [
        { label: '全部类型', value: '' }, { label: '添加', value: 'add' }, { label: '编辑', value: 'edit' }, { label: '删除', value: 'remove' }
      ],
      operationTypeIndex: 0, dateRange: monthRange(), selectedJson: ''
    }
  },
  computed: {
    isLog() { return this.mode === 'log' },
    pageTitle() { return this.mode === 'user' ? '员工管理' : this.mode === 'role' ? '角色管理' : this.mode === 'unit' ? '单位管理' : '操作日志' },
    searchPlaceholder() { return this.mode === 'user' ? '姓名 / 登录账号 / 手机号' : this.mode === 'role' ? '角色名称' : '单位名称' },
    hasMore() { return this.records.length < this.total },
    loadStatus() { return this.loading ? 'loading' : this.hasMore ? 'more' : 'noMore' },
    emptyText() { return this.isLog ? '暂无操作日志' : `暂无${this.pageTitle}数据` },
    selectedRoleName() { const role = this.roleOptions[this.roleIndex]; return role ? role.name : '请选择角色' }
  },
  mounted() { this.initialize() },
  methods: {
    async initialize() {
      if (this.initializing || this.redirecting) return
      this.initializing = true
      try {
        const currentUser = await getUserInfo()
        this.user = { ...this.user, ...(currentUser || {}) }
        updateUser(this.user)
        if (Number(this.user.isRoot) !== 1) {
          uni.showToast({ title: '仅老板账号可以访问', icon: 'none' })
          setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 500)
          return
        }
        if (this.mode === 'user') await this.loadRoles()
        if (this.mode === 'role') await this.loadPermissionTree()
        this.initialized = true
        await this.query(true)
      } catch (error) { this.handleError(error, '页面加载失败') } finally { this.initializing = false }
    },
    apiForMode() { return this.mode === 'user' ? listAppUserPage : this.mode === 'role' ? listAppRolePage : this.mode === 'unit' ? listAppUnitPage : listAppOperationLogPage },
    buildQuery(page) {
      if (this.isLog) return { ...this.filters, operationType: this.filters.operationType || undefined, startDate: this.dateRange[0], endDate: this.dateRange[1], current: page, pageSize: this.pageSize, userId: this.user.id }
      const params = { current: page, pageSize: this.pageSize, userId: this.user.id, column: 'id', order: 'desc' }
      const value = String(this.keyword || '').trim()
      if (value) {
        if (this.mode === 'user') params.searchKey = value
        else params.name = value
      }
      return params
    },
    async query(reset = false) {
      if (this.loading || this.redirecting || !this.initialized) return
      const page = reset ? 1 : this.current
      this.loading = true
      try {
        const result = await this.apiForMode()(this.buildQuery(page))
        const source = result || {}
        const rows = Array.isArray(source.records) ? source.records : Array.isArray(source) ? source : []
        this.records = reset ? rows : this.records.concat(rows)
        this.current = Number(source.current || page)
        this.total = Number(source.total || this.records.length)
      } catch (error) { this.handleError(error, '查询失败') } finally { this.loading = false }
    },
    search() { this.current = 1; this.query(true) },
    refresh() { return this.query(true) },
    loadMore() { if (!this.loading && this.hasMore) { this.current += 1; this.query(false) } },
    reset() { this.keyword = ''; this.filters = { operatorName: '', menuName: '', operationType: '', clientIp: '' }; this.operationTypeIndex = 0; this.dateRange = monthRange(); this.search() },
    changeOperationType(event) { this.operationTypeIndex = Number(event.detail.value || 0); this.filters.operationType = this.operationTypes[this.operationTypeIndex].value },
    changeDate(value) { this.dateRange = Array.isArray(value) ? value : [] },
    async loadRoles() { const result = await listAppRoles(); this.roleOptions = Array.isArray(result) ? result : [] },
    async loadPermissionTree() {
      const tree = await listAllAppMenus()
      const flat = []
      const walk = (nodes, depth) => (Array.isArray(nodes) ? nodes : []).forEach((node) => {
        if (!node) return
        if (node.menuId !== undefined && node.menuId !== null) {
          flat.push({ value: `menu:${node.menuId}`, label: `${'　'.repeat(depth)}${node.title || node.menuCode || '菜单'}`, kind: 'menu', menuId: Number(node.menuId), actionKeys: (node.children || []).filter((child) => child && child.action).map((child) => child.key) })
        }
        ;(node.children || []).filter((child) => child && child.action).forEach((child) => flat.push({ value: child.key, label: `${'　'.repeat(depth + 1)}${child.title || child.action}`, kind: 'action', menuId: node.menuId }))
        walk((node.children || []).filter((child) => child && !child.action), depth + 1)
      })
      walk(tree, 0)
      this.permissionNodes = flat
    },
    openForm(record) {
      this.editingId = record && record.id !== undefined ? String(record.id) : ''
      this.form = { ...createForm(this.mode), ...(record || {}) }
      if (this.mode === 'user') {
        this.form.password = ''
        this.roleIndex = Math.max(0, this.roleOptions.findIndex((role) => String(role.id) === String(this.form.roleId)))
      }
      if (this.mode === 'role') {
        this.checkedPermissionValues = []
        this.dataViewPermissionCodes = []
        if (this.editingId) {
          Promise.all([listAppRoleMenuIds(this.editingId), listAppRolePermissionCodes(this.editingId)]).then(([menuIds, codes]) => {
            this.checkedPermissionValues = (Array.isArray(menuIds) ? menuIds : []).map((id) => `menu:${id}`)
            const permissions = Array.isArray(codes) ? codes : []
            this.checkedPermissionValues = this.checkedPermissionValues.concat(permissions.filter((code) => !this.dataViewPermissions.some((item) => item.value === code)))
            this.dataViewPermissionCodes = permissions.filter((code) => this.dataViewPermissions.some((item) => item.value === code))
          }).catch((error) => this.handleError(error, '角色权限加载失败'))
        }
      }
      this.$refs.formPopup && this.$refs.formPopup.open()
    },
    closeForm() { this.$refs.formPopup && this.$refs.formPopup.close() },
    changeRole(event) { this.roleIndex = Number(event.detail.value || 0); this.form.roleId = this.roleOptions[this.roleIndex] ? this.roleOptions[this.roleIndex].id : '' },
    handlePermissionChange(event) {
      this.checkedPermissionValues = event && event.detail && Array.isArray(event.detail.value)
        ? event.detail.value
        : []
    },
    handleDataViewPermissionChange(event) {
      this.dataViewPermissionCodes = event && event.detail && Array.isArray(event.detail.value)
        ? event.detail.value
        : []
    },
    async submitForm() {
      if (this.mode === 'user' && (!String(this.form.userName).trim() || !String(this.form.realName).trim() || !this.form.roleId || (!this.editingId && String(this.form.password).length < 6))) return uni.showToast({ title: '请完善员工信息', icon: 'none' })
      if (this.mode !== 'user' && !String(this.form.name).trim()) return uni.showToast({ title: `请输入${this.mode === 'role' ? '角色' : '单位'}名称`, icon: 'none' })
      this.saving = true
      try {
        if (this.mode === 'user') await (this.editingId ? editAppUser : addAppUser)({ ...this.form, id: this.editingId || undefined, userId: this.user.id, commissionRate: Number(this.form.commissionRate || 0) })
        else if (this.mode === 'unit') await (this.editingId ? editAppUnit : addAppUnit)({ ...this.form, id: this.editingId || undefined, userId: this.user.id })
        else {
          const menuIds = []
          const permissionCodes = [...this.dataViewPermissionCodes]
          this.checkedPermissionValues.forEach((value) => {
            if (value.indexOf('menu:') === 0) {
              const node = this.permissionNodes.find((item) => item.value === value)
              menuIds.push(Number(value.slice(5)))
              if (node) permissionCodes.push(...node.actionKeys)
            } else {
              permissionCodes.push(value)
              const node = this.permissionNodes.find((item) => item.value === value)
              if (node && node.menuId !== undefined) menuIds.push(Number(node.menuId))
            }
          })
          await (this.editingId ? editAppRole : addAppRole)({ ...this.form, id: this.editingId || undefined, userId: this.user.id, menuIds: [...new Set(menuIds)], permissionCodes: [...new Set(permissionCodes)] })
        }
        uni.showToast({ title: '保存成功', icon: 'success' })
        this.closeForm()
        await this.query(true)
      } catch (error) { this.handleError(error, '保存失败') } finally { this.saving = false }
    },
    confirmRemove(record) {
      uni.showModal({ title: '确认删除', content: `确认删除${this.mode === 'unit' ? '该单位' : '该数据'}吗？`, success: async (result) => {
        if (!result.confirm) return
        try {
          const api = this.mode === 'user' ? removeAppUser : this.mode === 'role' ? removeAppRole : removeAppUnit
          await api({ id: record.id, userId: this.user.id })
          uni.showToast({ title: '删除成功', icon: 'success' })
          this.query(true)
        } catch (error) { this.handleError(error, '删除失败') }
      } })
    },
    confirmResetPassword(record) {
      uni.showModal({ title: '重置密码', content: '确认将密码重置为123456吗？', success: async (result) => {
        if (!result.confirm) return
        try { await resetAppUserPassword({ id: record.id, userId: this.user.id }); uni.showToast({ title: '密码已重置', icon: 'success' }) } catch (error) { this.handleError(error, '重置失败') }
      } })
    },
    showLogData(value) {
      if (!value) this.selectedJson = '{}'
      else { try { this.selectedJson = JSON.stringify(JSON.parse(value), null, 2) } catch (error) { this.selectedJson = String(value) } }
      this.$refs.dataPopup && this.$refs.dataPopup.open()
    },
    closeData() { this.$refs.dataPopup && this.$refs.dataPopup.close() },
    operationTypeText(type) { return type === 'add' ? '添加' : type === 'edit' ? '编辑' : type === 'remove' ? '删除' : type || '-' },
    formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '-' },
    formatNumber(value) { const number = Number(value || 0); return Number.isFinite(number) ? number.toFixed(2) : '0.00' },
    handleError(error, fallback) {
      const message = error && error.message ? error.message : fallback
      if (/登录|token|过期/i.test(message)) { this.redirecting = true; clearSession(); uni.reLaunch({ url: '/pages/login/index' }) }
      else uni.showToast({ title: message, icon: 'none' })
    }
  }
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; padding: 20rpx 22rpx 0; box-sizing: border-box; color: #33303f; background: #f5f4fb; }
.filter-panel, .record-card, .form-popup, .data-popup { background: #fff; border: 1rpx solid #ece9f2; border-radius: 16rpx; box-shadow: 0 6rpx 20rpx rgba(67,47,119,.06); }
.filter-panel { padding: 20rpx 22rpx 16rpx; }
.quick-search-row, .filter-line, .toolbar, .record-header, .record-main, .card-actions, .popup-header, .form-row, .segmented { display: flex; align-items: center; }
.quick-search-row, .filter-line { gap: 14rpx; }
.quick-input, .form-input { flex: 1; min-width: 0; height: 58rpx; padding: 0 18rpx; box-sizing: border-box; color: #393044; font-size: 24rpx; background: #fff; border: 1rpx solid #ded9e8; border-radius: 10rpx; }
.search-button, .add-button, .reset-button, .text-button, .data-button, .cancel-button, .confirm-button, .segmented button { display: flex; align-items: center; justify-content: center; gap: 6rpx; margin: 0; padding: 0 16rpx; border: 0; border-radius: 10rpx; }
.search-button, .add-button { flex: 0 0 124rpx; height: 58rpx; color: #fff; font-size: 23rpx; background: #722ed1; }
.reset-button { flex: 0 0 124rpx; height: 58rpx; color: #666; font-size: 23rpx; background: #f2f3f5; }
.search-button::after, .add-button::after, .reset-button::after, .text-button::after, .data-button::after, .cancel-button::after, .confirm-button::after, .segmented button::after { border: 0; }
.toolbar { justify-content: space-between; gap: 14rpx; margin-top: 18rpx; }
.page-title { color: #454252; font-size: 29rpx; font-weight: 600; }
.toolbar-actions { display: flex; gap: 12rpx; }
.log-filter { display: flex; flex-direction: column; gap: 12rpx; }
.log-filter .filter-line > .quick-input { width: 0; }
.log-filter .filter-line > .quick-input:first-child,
.log-filter .filter-line > .quick-input:last-child { flex: 1; }
.operation-picker { flex: 0 0 190rpx; }
.log-filter .filter-line > :deep(.uni-date) { flex: 1; min-width: 0; }
.log-filter .filter-line > :deep(.uni-date-editor--x) { height: 58rpx; }
.log-filter .filter-line > :deep(.uni-date-x--border) { height: 58rpx; box-sizing: border-box; border-radius: 10rpx; }
.picker-field { display: flex; align-items: center; justify-content: space-between; width: 100%; height: 58rpx; padding: 0 16rpx; box-sizing: border-box; overflow: hidden; color: #393044; font-size: 23rpx; background: #fff; border: 1rpx solid #ded9e8; border-radius: 10rpx; }
.placeholder { color: #aaa5b5; }
.record-list { padding-top: 18rpx; }
.record-card { margin-bottom: 16rpx; padding: 20rpx; }
.record-header, .popup-header { justify-content: space-between; gap: 12rpx; }
.record-main { flex: 1; min-width: 0; gap: 14rpx; }
.record-icon { display: flex; align-items: center; justify-content: center; flex: 0 0 62rpx; width: 62rpx; height: 62rpx; background: #f4efff; border-radius: 14rpx; }
.record-title-wrap { display: flex; flex-direction: column; min-width: 0; }
.record-title { overflow: hidden; color: #30283d; font-size: 29rpx; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.record-subtitle { margin-top: 6rpx; overflow: hidden; color: #8d8797; font-size: 21rpx; text-overflow: ellipsis; white-space: nowrap; }
.status, .log-type { flex: 0 0 auto; padding: 6rpx 12rpx; color: #00a870; font-size: 20rpx; background: #e8f8f2; border-radius: 18rpx; }
.status.disabled, .log-type.type-remove { color: #e5484d; background: #fff0f0; }
.log-type.type-edit { color: #1677ff; background: #eaf3ff; }
.info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12rpx; margin-top: 18rpx; padding: 16rpx; background: #faf9fc; border-radius: 10rpx; }
.info-item { min-width: 0; }
.info-label, .note-label { display: block; color: #918b99; font-size: 20rpx; }
.info-value { display: block; margin-top: 6rpx; overflow: hidden; color: #4d4858; font-size: 22rpx; text-overflow: ellipsis; white-space: nowrap; }
.note-row { display: flex; gap: 12rpx; margin-top: 14rpx; font-size: 22rpx; }
.note-label { flex: 0 0 60rpx; }
.note-text { flex: 1; color: #686171; word-break: break-all; }
.card-actions { justify-content: flex-end; gap: 8rpx; margin-top: 16rpx; padding-top: 12rpx; border-top: 1rpx solid #eeeaf4; }
.text-button { min-width: 108rpx; height: 52rpx; color: #722ed1; font-size: 21rpx; background: #f4efff; }
.text-button.danger { color: #e5484d; background: #fff0f0; }
.data-button { width: 100%; height: 54rpx; margin-top: 16rpx; color: #722ed1; font-size: 22rpx; background: #f4efff; }
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 500rpx; gap: 18rpx; color: #aaa5b5; font-size: 24rpx; }
.bottom-space { height: 30rpx; }
.form-popup { max-height: 88vh; padding: 28rpx 28rpx calc(24rpx + env(safe-area-inset-bottom)); box-sizing: border-box; border-radius: 26rpx 26rpx 0 0; overflow-y: auto; }
.popup-title { color: #30283d; font-size: 31rpx; font-weight: 600; }
.form-item { margin-top: 20rpx; }
.form-item.half { flex: 1; min-width: 0; }
.form-row { gap: 14rpx; align-items: flex-start; }
.form-label { display: block; margin-bottom: 10rpx; color: #6b6676; font-size: 22rpx; }
.form-input { width: 100%; }
.form-textarea { width: 100%; min-height: 130rpx; padding: 14rpx 18rpx; box-sizing: border-box; color: #393044; font-size: 24rpx; border: 1rpx solid #ded9e8; border-radius: 10rpx; }
.segmented { gap: 12rpx; }
.segmented button { flex: 1; height: 56rpx; color: #666; font-size: 22rpx; background: #f2f3f5; }
.segmented button.active { color: #fff; background: #722ed1; }
.permission-list { max-height: 300rpx; overflow-y: auto; border: 1rpx solid #eeeaf4; border-radius: 10rpx; }
.permission-item { display: flex; align-items: center; min-height: 62rpx; padding: 0 14rpx; color: #4d4858; font-size: 22rpx; border-bottom: 1rpx solid #f0edf5; }
.permission-item.child { padding-left: 44rpx; color: #777482; }
.permission-item:last-child { border-bottom: 0; }
.popup-actions { display: flex; gap: 16rpx; margin-top: 26rpx; }
.cancel-button, .confirm-button { flex: 1; height: 76rpx; font-size: 25rpx; }
.cancel-button { color: #666; background: #f2f3f5; }
.confirm-button { color: #fff; background: #722ed1; }
.data-popup { width: 88vw; padding: 26rpx; box-sizing: border-box; }
.json-scroll { max-height: 65vh; margin-top: 18rpx; padding: 16rpx; box-sizing: border-box; background: #faf9fc; border-radius: 10rpx; }
.json-text { color: #4d4858; font-size: 21rpx; line-height: 1.5; word-break: break-all; }
</style>
