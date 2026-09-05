<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="手机号 / 昵称" style="width: 260px" clearable @keyup.enter="searchUsers" />
      <el-button type="primary" @click="searchUsers">查找用户</el-button>
    </div>

    <el-table :data="users" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="mobile" label="手机号" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="openRecharge(row)">充值</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="rechargeVisible" title="快乐币充值" width="440px">
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span>{{ currentUser?.nickname || '' }}（{{ currentUser?.mobile || '' }}）</span>
        </el-form-item>
        <el-form-item label="当前余额">
          <span style="color: #e6a23c; font-weight: 600;">{{ currentBalance }} 快乐币</span>
        </el-form-item>
        <el-form-item label="币数">
          <el-input-number v-model="rechargeForm.amount" :min="1" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="rechargeForm.remark" placeholder="如：邀请码赠送 / 活动补偿 / 内测发放" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRecharge">确认充值</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { adminApi } from '../services/http';

const keyword = ref('');
const users = ref([]);
const loading = ref(false);
const rechargeVisible = ref(false);
const currentUser = ref(null);
const rechargeForm = reactive({ amount: 100, remark: '' });
const currentBalance = ref(0);

async function searchUsers() {
  loading.value = true;
  try {
    users.value = await adminApi.get('/coins/users', { params: { keyword: keyword.value } });
  } finally {
    loading.value = false;
  }
}

async function openRecharge(row) {
  currentUser.value = row;
  rechargeForm.amount = 100;
  rechargeForm.remark = '';
  currentBalance.value = 0;
  rechargeVisible.value = true;
  try {
    const data = await adminApi.get('/coins/balance', { params: { userId: row.id } });
    currentBalance.value = data.balance;
  } catch {
    // ignore balance load failure
  }
}

async function submitRecharge() {
  await adminApi.post('/coins/recharge', {
    userId: currentUser.value.id,
    amount: rechargeForm.amount,
    remark: rechargeForm.remark
  });
  ElMessage.success('充值成功');
  rechargeVisible.value = false;
}

onMounted(searchUsers);
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
</style>
