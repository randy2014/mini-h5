<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button type="primary" @click="open()">新增套餐</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="套餐" />
      <el-table-column prop="durationDays" label="天数" width="100" />
      <el-table-column prop="price" label="价格" width="100" />
      <el-table-column prop="enabled" label="状态" width="100">
        <template #default="{ row }"><el-switch v-model="row.enabled" @change="setEnabled(row)" /></template>
      </el-table-column>
      <el-table-column prop="description" label="说明" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }"><el-button link type="primary" @click="open(row)">编辑</el-button></template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="visible" title="VIP 套餐" width="520px">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="天数"><el-input-number v-model="form.durationDays" :min="1" /></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="原价"><el-input-number v-model="form.originalPrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { adminApi } from '../services/http';

const rows = ref([]);
const loading = ref(false);
const visible = ref(false);
const form = reactive({});

async function load() {
  loading.value = true;
  try { rows.value = await adminApi.get('/vip/plans'); } finally { loading.value = false; }
}
function open(row) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, row || { name: '', durationDays: 30, price: 0, originalPrice: 0, sort: 0, enabled: true, description: '' });
  visible.value = true;
}
async function save() {
  form.id ? await adminApi.put(`/vip/plans/${form.id}`, form) : await adminApi.post('/vip/plans', form);
  ElMessage.success('已保存');
  visible.value = false;
  load();
}
async function setEnabled(row) {
  await adminApi.put(`/vip/plans/${row.id}/enabled`, { enabled: row.enabled });
  ElMessage.success('状态已更新');
}
onMounted(load);
</script>
