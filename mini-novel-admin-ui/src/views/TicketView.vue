<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="userId" label="用户ID" width="90" />
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'OPEN' ? 'warning' : 'success'">
            {{ row.status === 'OPEN' ? '处理中' : '已关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看/回复</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" title="工单详情" width="600px">
      <div class="detail-title">{{ current?.title }}</div>
      <div class="detail-content">{{ current?.content }}</div>
      <el-divider>回复记录</el-divider>
      <div v-for="r in replies" :key="r.id" class="reply">
        <span :class="r.replierType === 'ADMIN' ? 'admin' : 'user'">
          {{ r.replierType === 'ADMIN' ? '客服' : '用户' }}
        </span>
        <p>{{ r.content }}</p>
      </div>
      <div v-if="replies.length === 0" class="no-reply">暂无回复</div>
      <el-divider />
      <el-input v-model="replyContent" type="textarea" :rows="3" placeholder="输入回复内容" />
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="submitReply">回复</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { adminApi } from '../services/http';
import { formatDateTime } from '../utils/date';

const rows = ref([]);
const loading = ref(false);
const detailVisible = ref(false);
const current = ref(null);
const replies = ref([]);
const replyContent = ref('');

onMounted(load);

async function load() {
  loading.value = true;
  try {
    rows.value = await adminApi.get('/ticket/list');
  } finally {
    loading.value = false;
  }
}

async function openDetail(row) {
  current.value = row;
  const data = await adminApi.get(`/ticket/${row.id}`);
  replies.value = data.replies || [];
  replyContent.value = '';
  detailVisible.value = true;
}

async function submitReply() {
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容');
    return;
  }
  await adminApi.post(`/ticket/${current.value.id}/reply`, { content: replyContent.value });
  ElMessage.success('已回复');
  detailVisible.value = false;
  load();
}
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
.detail-title { font-size: 16px; font-weight: 700; }
.detail-content { margin-top: 8px; color: #4b5563; line-height: 1.7; }
.reply { margin-bottom: 10px; }
.reply span { display: inline-block; font-size: 12px; padding: 2px 8px; border-radius: 8px; margin-bottom: 4px; }
.reply span.admin { background: #e9f4f0; color: #1f6f64; }
.reply span.user { background: #eef2f6; color: #314c5c; }
.reply p { margin: 0; color: #4b5563; line-height: 1.6; }
.no-reply { color: #a6adb9; font-size: 13px; }
</style>
