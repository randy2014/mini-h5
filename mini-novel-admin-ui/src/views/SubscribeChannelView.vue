<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button type="primary" @click="open()">新增频道</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="频道名称" />
      <el-table-column prop="sort" label="排序" width="90" />
      <el-table-column label="频道内容" width="170">
        <template #default="{ row }">
          <span class="muted">小说 {{ row.novelCount ?? 0 }} · 图文视频 {{ row.mediaCount ?? 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="简介" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ row.status === 'PUBLISHED' ? '已发布' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="290">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看频道详情</el-button>
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button v-if="row.status !== 'PUBLISHED'" link type="success" @click="publish(row)">发布</el-button>
          <el-button v-else link type="warning" @click="offline(row)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" title="订阅频道" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { adminApi } from '../services/http';

const router = useRouter();
const rows = ref([]);
const loading = ref(false);
const visible = ref(false);
const form = reactive({});

async function load() {
  loading.value = true;
  try {
    rows.value = await adminApi.get('/subscribe-channels');
  } finally {
    loading.value = false;
  }
}

function open(row) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, row || { name: '', sort: 100, description: '' });
  visible.value = true;
}

// 频道详情页：频道内小说 + 已发布图文/视频内容
function openDetail(row) {
  router.push(`/admin/subscribe-channels/${row.id}`);
}

async function save() {
  if (form.id) {
    await adminApi.put(`/subscribe-channels/${form.id}`, form);
  } else {
    await adminApi.post('/subscribe-channels', form);
  }
  ElMessage.success('已保存');
  visible.value = false;
  load();
}

async function publish(row) {
  await adminApi.put(`/subscribe-channels/${row.id}/publish`);
  ElMessage.success('已发布');
  load();
}

async function offline(row) {
  try {
    await ElMessageBox.confirm(`下架频道「${row.name}」？`, '确认');
  } catch {
    return;
  }
  try {
    await adminApi.put(`/subscribe-channels/${row.id}/offline`);
    ElMessage.success('已下架');
    load();
  } catch (e) {
    ElMessage.error(e.message || '下架失败');
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
</style>
