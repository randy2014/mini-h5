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
      <el-table-column label="操作" min-width="260">
        <template #default="{ row }">
          <el-button link type="primary" @click="openVip(row)">调整VIP</el-button>
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
            <el-option label="续期" value="EXTEND" />
            <el-option label="指定到期" value="SET" />
            <el-option label="永久VIP" value="PERMANENT" />
            <el-option label="取消VIP" value="CANCEL" />
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
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { adminApi } from '../services/http';

const rows = ref([]);
const logs = ref([]);
const loading = ref(false);
const keyword = ref('');
const currentUser = ref(null);
const vipVisible = ref(false);
const logVisible = ref(false);
const vipForm = reactive({ action: 'EXTEND', days: 30, expireAt: '', reason: '' });

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
  Object.assign(vipForm, { action: 'EXTEND', days: 30, expireAt: '', reason: '' });
  vipVisible.value = true;
}
async function saveVip() {
  await adminApi.put(`/users/${currentUser.value.id}/vip`, { ...vipForm, operatorId: 1 });
  ElMessage.success('VIP 状态已更新');
  vipVisible.value = false;
  load();
}
async function openLogs(row) {
  logs.value = await adminApi.get(`/users/${row.id}/vip-logs`);
  logVisible.value = true;
}
onMounted(load);
</script>
