<template>
  <section>
    <el-card shadow="never" class="panel">
      <template #header>创建采集任务</template>
      <el-form :inline="true">
        <el-form-item label="入口地址">
          <el-input v-model="seedUrl" class="seed-input" placeholder="https://m.qidian.com/ 或 https://example.com" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="run">开始采集</el-button>
          <el-button @click="load">刷新任务</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <template #header>采集任务</template>
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="sourceId" label="源ID" width="80" />
        <el-table-column prop="taskType" label="类型" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="message" label="信息" min-width="300" />
        <el-table-column prop="startedAt" label="开始时间" width="180" />
        <el-table-column prop="finishedAt" label="结束时间" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }"><el-button link type="primary" @click="retry(row)">重试</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { crawlerApi } from '../services/http';

const seedUrl = ref('https://m.qidian.com/');
const rows = ref([]);
const loading = ref(false);

function statusText(status) {
  return ({ 0: '待执行', 1: '成功', 2: '失败' })[status] || '未知';
}
function statusType(status) {
  return status === 1 ? 'success' : status === 2 ? 'danger' : 'info';
}
async function load() {
  loading.value = true;
  try { rows.value = await crawlerApi.get('/tasks'); } finally { loading.value = false; }
}
async function run() {
  await crawlerApi.post('/tasks', { sourceId: 1, seedUrl: seedUrl.value });
  ElMessage.success('采集任务已提交');
  setTimeout(load, 1200);
}
async function retry(row) {
  await crawlerApi.post(`/tasks/${row.id}/retry`);
  ElMessage.success('重试任务已提交');
  setTimeout(load, 1200);
}
onMounted(load);
</script>
