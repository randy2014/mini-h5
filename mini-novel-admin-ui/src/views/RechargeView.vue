<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-radio-group v-model="statusFilter" @change="load">
        <el-radio-button :value="-1">全部</el-radio-button>
        <el-radio-button :value="0">待确认</el-radio-button>
        <el-radio-button :value="1">已到账</el-radio-button>
        <el-radio-button :value="2">已取消</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="orderNo" label="订单号" min-width="180" />
      <el-table-column label="用户" min-width="150">
        <template #default="{ row }">{{ row.nickname }}（{{ row.mobile || row.userId }}）</template>
      </el-table-column>
      <el-table-column label="金额" width="100">
        <template #default="{ row }">¥{{ row.amountYuan }}</template>
      </el-table-column>
      <el-table-column prop="coins" label="到账币" width="90" />
      <el-table-column label="支付方式" width="90">
        <template #default="{ row }">{{ row.payMethod === 1 ? '微信' : '支付宝' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="到账时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.confirmedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" link type="primary" @click="confirm(row)">确认到账</el-button>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import { adminApi } from '../services/http';
import { formatDateTime } from '../utils/date';

const rows = ref([]);
const loading = ref(false);
const statusFilter = ref(-1);

function statusText(status) {
  return { 0: '待确认', 1: '已到账', 2: '已取消' }[status] || String(status);
}

function statusTag(status) {
  return { 0: 'warning', 1: 'success', 2: 'info' }[status] || 'info';
}

async function load() {
  loading.value = true;
  try {
    const params = statusFilter.value === -1 ? {} : { status: statusFilter.value };
    rows.value = await adminApi.get('/recharges', { params });
  } finally {
    loading.value = false;
  }
}

async function confirm(row) {
  await ElMessageBox.confirm(
    `确认已收到用户「${row.nickname}」（${row.mobile || row.userId}）的 ¥${row.amountYuan}，到账 ${row.coins} 快乐币？`,
    '确认到账',
    { type: 'warning', confirmButtonText: '确认到账', cancelButtonText: '取消' }
  );
  await adminApi.post(`/recharges/${row.orderNo}/confirm`);
  ElMessage.success('已确认到账，快乐币已发放');
  load();
}

onMounted(load);
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
.muted { color: #c0c4cc; }
</style>
