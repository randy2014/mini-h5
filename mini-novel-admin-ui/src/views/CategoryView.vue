<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button type="primary" @click="open()">新增分类</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="分类名称" />
      <el-table-column prop="sort" label="排序" width="120" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="visible" title="分类编辑" width="420px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi } from '../services/http';

const rows = ref([]);
const loading = ref(false);
const visible = ref(false);
const form = reactive({});

async function load() {
  loading.value = true;
  try { rows.value = await adminApi.get('/categories'); } finally { loading.value = false; }
}
function open(row) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, row || { name: '', sort: 0 });
  visible.value = true;
}
async function save() {
  form.id ? await adminApi.put(`/categories/${form.id}`, form) : await adminApi.post('/categories', form);
  ElMessage.success('已保存');
  visible.value = false;
  load();
}
async function remove(row) {
  await ElMessageBox.confirm(`删除分类「${row.name}」？`, '确认');
  await adminApi.delete(`/categories/${row.id}`);
  ElMessage.success('已删除');
  load();
}
onMounted(load);
</script>
