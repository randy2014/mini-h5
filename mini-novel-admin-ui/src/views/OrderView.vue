<template>
  <el-card shadow="never">
    <div class="toolbar"><el-button type="primary" @click="load">刷新</el-button></div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="orderNo" label="订单号" min-width="180" />
      <el-table-column prop="userId" label="用户ID" width="100" />
      <el-table-column prop="vipPlanId" label="套餐ID" width="100" />
      <el-table-column prop="amount" label="金额" width="100" />
      <el-table-column prop="payStatus" label="状态" width="110">
        <template #default="{ row }"><el-tag :type="row.payStatus === 1 ? 'success' : 'info'">{{ row.payStatus === 1 ? '已支付' : '待支付' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180"><template #default="{row}">{{formatDateTime(row.createdAt)}}</template></el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }"><el-button link type="primary" :disabled="row.payStatus === 1" @click="markPaid(row)">模拟支付</el-button></template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { adminApi } from '../services/http';
import { formatDateTime } from '../utils/date';

const rows = ref([]);
const loading = ref(false);

async function load() {
  loading.value = true;
  try { rows.value = await adminApi.get('/vip/orders'); } finally { loading.value = false; }
}
async function markPaid(row) {
  await adminApi.put(`/vip/orders/${row.id}/paid`);
  ElMessage.success('订单已标记支付');
  load();
}
onMounted(load);
</script>
