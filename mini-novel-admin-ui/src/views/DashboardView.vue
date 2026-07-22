<template>
  <section>
    <div class="metric-grid">
      <div v-for="item in metrics" :key="item.label" class="metric-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>
    <el-card class="panel" shadow="never">
      <template #header>最新文章</template>
      <el-table :data="dashboard.latestNovels || []" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="author" label="作者" width="140" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.status === 0 ? 'danger' : 'success'">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="190"><template #default="{row}">{{formatDateTime(row.updatedAt)}}</template></el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { adminApi } from '../services/http';
import { formatDateTime } from '../utils/date';

const loading = ref(false);
const dashboard = ref({});

const metrics = computed(() => [
  { label: '文章数', value: dashboard.value.novelCount || 0 },
  { label: '章节数', value: dashboard.value.chapterCount || 0 },
  { label: '分类数', value: dashboard.value.categoryCount || 0 },
  { label: '用户数', value: dashboard.value.userCount || 0 },
  { label: 'VIP 用户', value: dashboard.value.activeVipUsers || 0 },
  { label: '付费金额', value: dashboard.value.paidAmount || 0 },
  { label: '采集任务', value: dashboard.value.crawlTaskCount || 0 },
  { label: 'VIP 章节', value: dashboard.value.vipChapterCount || 0 }
]);

function statusText(status) {
  return ({ 0: '下架', 1: '正常', 2: '完结', 3: '草稿', 4: '审核中' })[status] || '未知';
}

async function load() {
  loading.value = true;
  try {
    dashboard.value = await adminApi.get('/dashboard');
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
