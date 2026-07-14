<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="昵称/手机号" clearable @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="nickname" label="昵称" width="140" />
      <el-table-column prop="mobile" label="手机号" width="140" />
      <el-table-column prop="status" label="账号状态" width="110">
        <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="vipStatus" label="VIP状态" width="110">
        <template #default="{ row }"><el-tag :type="row.vipStatus ? 'warning' : 'info'">{{ vipText(row) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="vipExpireTime" label="VIP到期" width="190" />
      <el-table-column prop="vipSource" label="VIP来源" width="110" />
      <el-table-column label="操作" min-width="360">
        <template #default="{ row }">
          <el-button link type="primary" @click="openVip(row)">调整VIP</el-button>
          <el-button link type="primary" @click="openInvite(row)">邀请码</el-button>
          <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
          <el-button link type="primary" @click="openLogs(row)">VIP记录</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="vipVisible" title="调整用户 VIP" width="520px">
      <el-form label-width="110px">
        <el-form-item label="用户">{{ currentUser?.nickname }} {{ currentUser?.mobile }}</el-form-item>
        <el-form-item label="调整类型">
          <el-select v-model="vipForm.action">
            <el-option label="升级/续期" value="UPGRADE" />
            <el-option label="指定到期" value="SET" />
            <el-option label="降级" value="DOWNGRADE" />
            <el-option label="停用" value="SUSPEND" />
            <el-option label="恢复" value="RESTORE" />
          </el-select>
        </el-form-item>
        <el-form-item label="天数"><el-input-number v-model="vipForm.days" :min="1" /></el-form-item>
        <el-form-item label="指定到期"><el-date-picker v-model="vipForm.expireAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="vipForm.reason" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="vipVisible=false">取消</el-button><el-button type="primary" @click="saveVip">保存</el-button></template>
    </el-dialog>
    <el-drawer v-model="logVisible" title="VIP 调整记录" size="640px">
      <el-table :data="logs">
        <el-table-column prop="action" label="动作" width="110" />
        <el-table-column prop="beforeExpireTime" label="调整前" width="180" />
        <el-table-column prop="afterExpireTime" label="调整后" width="180" />
        <el-table-column prop="reason" label="原因" />
      </el-table>
    </el-drawer>
    <el-drawer v-model="inviteVisible" title="邀请码管理" size="720px">
      <el-descriptions :column="2" border v-if="inviteCode">
        <el-descriptions-item label="邀请码">{{ inviteCode.code }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ inviteCode.status }}</el-descriptions-item>
        <el-descriptions-item label="总额度">{{ inviteCode.totalQuota }}</el-descriptions-item>
        <el-descriptions-item label="剩余额度">{{ inviteCode.remainingQuota }}</el-descriptions-item>
        <el-descriptions-item label="已使用次数">{{ inviteCode.usedQuota }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ inviteCode.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="过期时间">{{ inviteCode.expiresAt || '长期有效' }}</el-descriptions-item>
        <el-descriptions-item label="操作"><el-button link type="primary" @click="copyCode(inviteCode.code)">复制邀请码</el-button></el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无邀请码" />
      <el-form v-if="!inviteCode" :inline="true" class="toolbar">
        <el-form-item label="最大使用次数"><el-input-number v-model="generateForm.maxUses" :min="1" :max="1000" /></el-form-item>
        <el-form-item label="过期时间"><el-date-picker v-model="generateForm.expiresAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="generateForm.reason" placeholder="生成原因" /></el-form-item>
        <el-button type="primary" @click="generateInvite">生成邀请码</el-button>
      </el-form>
      <div class="toolbar">
        <el-input-number v-model="quotaForm.totalQuota" :min="0" />
        <el-input v-model="quotaForm.reason" placeholder="原因" />
        <el-button type="primary" :disabled="!inviteCode" @click="saveQuota">调整额度</el-button>
        <el-button :disabled="!inviteCode" @click="setInviteEnabled(true)">启用</el-button>
        <el-button :disabled="!inviteCode" @click="setInviteEnabled(false)">停用</el-button>
        <el-button type="warning" @click="reissueInvite">作废重发</el-button>
      </div>
      <h3>邀请码列表</h3>
      <el-table :data="inviteCodes">
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="usedQuota" label="已使用" width="80" />
        <el-table-column prop="totalQuota" label="最大次数" width="90" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column prop="expiresAt" label="过期时间" width="170" />
        <el-table-column label="操作"><template #default="{row}"><el-button link type="primary" @click="copyCode(row.code)">复制</el-button><el-button v-if="row.status==='ENABLED'" link type="danger" @click="disableListedCode(row)">禁用</el-button></template></el-table-column>
      </el-table>
      <h3>邀请记录</h3>
      <el-table :data="inviteRecords">
        <el-table-column prop="codeSnapshot" label="邀请码" width="120" />
        <el-table-column prop="inviteeUserId" label="被邀请用户" width="120" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="activatedAt" label="激活时间" />
      </el-table>
      <h3>审计摘要</h3>
      <el-table :data="operationLogs">
        <el-table-column prop="action" label="动作" width="140" />
        <el-table-column prop="operatorId" label="操作人" width="100" />
        <el-table-column prop="reason" label="原因" />
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi } from '../services/http';

const rows = ref([]);
const logs = ref([]);
const loading = ref(false);
const keyword = ref('');
const currentUser = ref(null);
const vipVisible = ref(false);
const logVisible = ref(false);
const inviteVisible = ref(false);
const inviteCode = ref(null);
const inviteRecords = ref([]);
const inviteCodes = ref([]);
const operationLogs = ref([]);
const vipForm = reactive({ action: 'UPGRADE', days: 30, expireAt: '', reason: '' });
const quotaForm = reactive({ totalQuota: 3, reason: '' });
const generateForm = reactive({ maxUses: 3, expiresAt: '', reason: '' });

function vipText(row) {
  if (row.vipStatus === 2) return '永久VIP';
  return row.vipExpireTime ? 'VIP' : '非会员';
}
async function load() {
  loading.value = true;
  try { rows.value = await adminApi.get('/users', { params: { keyword: keyword.value } }); } finally { loading.value = false; }
}
async function toggleStatus(row) {
  await adminApi.put(`/users/${row.id}/status`, { status: row.status === 1 ? 0 : 1 });
  ElMessage.success('账号状态已更新');
  load();
}
function openVip(row) {
  currentUser.value = row;
  Object.assign(vipForm, { action: 'UPGRADE', days: 30, expireAt: '', reason: '' });
  vipVisible.value = true;
}
async function saveVip() {
  if (!vipForm.reason) {
    ElMessage.warning('请填写原因');
    return;
  }
  await ElMessageBox.confirm('确认调整该用户 VIP 状态？', '二次确认');
  await adminApi.put(`/users/${currentUser.value.id}/vip`, { ...vipForm, operatorId: 1, requestId: requestId('vip') });
  ElMessage.success('VIP 状态已更新');
  vipVisible.value = false;
  load();
}
async function openLogs(row) {
  logs.value = await adminApi.get(`/users/${row.id}/vip-logs`);
  logVisible.value = true;
}
async function openInvite(row) {
  currentUser.value = row;
  inviteCode.value = await adminApi.get(`/users/${row.id}/invite-code`);
  inviteRecords.value = await adminApi.get(`/users/${row.id}/invite-records`);
  inviteCodes.value = await adminApi.get(`/users/${row.id}/invite-codes`);
  operationLogs.value = await adminApi.get(`/users/${row.id}/operation-logs`);
  quotaForm.totalQuota = inviteCode.value?.totalQuota || 3;
  quotaForm.reason = '';
  inviteVisible.value = true;
}
async function generateInvite() {
  if (!generateForm.reason) { ElMessage.warning('请填写生成原因'); return; }
  await ElMessageBox.confirm('确认生成邀请码？', '二次确认');
  await adminApi.post(`/users/${currentUser.value.id}/invite-code`, { ...generateForm, operatorId: 1, requestId: requestId('generate') });
  ElMessage.success('邀请码已生成');
  await openInvite(currentUser.value);
}
async function copyCode(code) {
  try { await navigator.clipboard.writeText(code); ElMessage.success('邀请码已复制'); }
  catch { ElMessage.error('复制失败，请手动复制'); }
}
async function disableListedCode(row) {
  await ElMessageBox.confirm('确认禁用该邀请码？', '二次确认');
  await adminApi.put(`/users/invite-codes/${row.id}/disable`, { operatorId: 1, reason: '后台禁用邀请码', requestId: requestId('disable') });
  ElMessage.success('邀请码已禁用');
  await openInvite(currentUser.value);
}
async function saveQuota() {
  await adminApi.put(`/users/invite-codes/${inviteCode.value.id}/quota`, { ...quotaForm, operatorId: 1, requestId: requestId('quota') });
  ElMessage.success('额度已更新');
  openInvite(currentUser.value);
}
async function setInviteEnabled(enabled) {
  await adminApi.put(`/users/invite-codes/${inviteCode.value.id}/${enabled ? 'enable' : 'disable'}`, {
    operatorId: 1,
    reason: quotaForm.reason || (enabled ? '启用邀请码' : '停用邀请码'),
    requestId: requestId(enabled ? 'enable' : 'disable')
  });
  ElMessage.success('邀请码状态已更新');
  openInvite(currentUser.value);
}
async function reissueInvite() {
  await ElMessageBox.confirm('确认作废旧邀请码并重发？', '二次确认');
  await adminApi.put(`/users/${currentUser.value.id}/invite-code/reissue`, {
    operatorId: 1,
    reason: quotaForm.reason || '后台作废重发',
    requestId: requestId('reissue')
  });
  ElMessage.success('邀请码已重发');
  openInvite(currentUser.value);
}
function requestId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
onMounted(load);
</script>
