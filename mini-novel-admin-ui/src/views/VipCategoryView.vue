<template>
  <div class="vip-category-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="openCategory()">新增 VIP 分类</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
      <el-table :data="categories" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称" />
        <el-table-column prop="normalizedName" label="规范键" />
        <el-table-column prop="sort" label="排序" width="100" />
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCategory(row)">编辑</el-button>
            <el-button link type="danger" @click="removeCategory(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="sourceFilter" clearable placeholder="来源筛选" style="width: 220px" @change="loadMappings">
          <el-option label="xbookcn_authorized" value="xbookcn_authorized" />
          <el-option label="h528_authorized" value="h528_authorized" />
          <el-option label="novel69h_authorized" value="novel69h_authorized" />
        </el-select>
        <el-button type="primary" @click="openMapping()">新增来源映射</el-button>
        <el-button @click="loadMappings">刷新映射</el-button>
      </div>
      <el-table :data="mappings" v-loading="mappingLoading">
        <el-table-column prop="sourceCode" label="来源" width="190" />
        <el-table-column prop="sourceCategoryName" label="来源分类" />
        <el-table-column prop="normalizedName" label="规范键" />
        <el-table-column label="VIP 分类">
          <template #default="{ row }">{{ categoryName(row.vipCategoryId) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openMapping(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="categoryVisible" title="VIP 分类" width="420px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="categoryForm.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sort" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="categoryForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mappingVisible" title="来源分类映射" width="520px">
      <el-form label-width="110px">
        <el-form-item label="来源">
          <el-select v-model="mappingForm.sourceCode" style="width: 100%">
            <el-option label="xbookcn_authorized" value="xbookcn_authorized" />
            <el-option label="h528_authorized" value="h528_authorized" />
            <el-option label="novel69h_authorized" value="novel69h_authorized" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源分类"><el-input v-model="mappingForm.sourceCategoryName" /></el-form-item>
        <el-form-item label="VIP 分类">
          <el-select v-model="mappingForm.vipCategoryId" style="width: 100%">
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="mappingForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mappingVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMapping">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi } from '../services/http';

const categories = ref([]);
const mappings = ref([]);
const loading = ref(false);
const mappingLoading = ref(false);
const categoryVisible = ref(false);
const mappingVisible = ref(false);
const sourceFilter = ref('');
const categoryForm = reactive({});
const mappingForm = reactive({});
const categoryMap = computed(() => new Map(categories.value.map((item) => [item.id, item.name])));

function reset(target, value) {
  Object.keys(target).forEach((key) => delete target[key]);
  Object.assign(target, value);
}

function categoryName(id) {
  return categoryMap.value.get(id) || '未映射';
}

async function load() {
  loading.value = true;
  try { categories.value = await adminApi.get('/vip-categories'); } finally { loading.value = false; }
}

async function loadMappings() {
  mappingLoading.value = true;
  try {
    mappings.value = await adminApi.get('/vip-categories/source-mappings', {
      params: sourceFilter.value ? { sourceCode: sourceFilter.value } : {}
    });
  } finally { mappingLoading.value = false; }
}

function openCategory(row) {
  reset(categoryForm, row || { name: '', sort: 100, enabled: true });
  categoryVisible.value = true;
}

async function saveCategory() {
  categoryForm.id
    ? await adminApi.put(`/vip-categories/${categoryForm.id}`, categoryForm)
    : await adminApi.post('/vip-categories', categoryForm);
  ElMessage.success('已保存');
  categoryVisible.value = false;
  await load();
}

async function removeCategory(row) {
  await ElMessageBox.confirm(`删除 VIP 分类「${row.name}」？`, '确认');
  await adminApi.delete(`/vip-categories/${row.id}`);
  ElMessage.success('已删除');
  await load();
  await loadMappings();
}

function openMapping(row) {
  reset(mappingForm, row || {
    sourceCode: sourceFilter.value || 'h528_authorized',
    sourceCategoryName: '',
    vipCategoryId: categories.value[0]?.id,
    enabled: true
  });
  mappingVisible.value = true;
}

async function saveMapping() {
  await adminApi.post('/vip-categories/source-mappings', mappingForm);
  ElMessage.success('已保存');
  mappingVisible.value = false;
  await loadMappings();
}

onMounted(async () => {
  await load();
  await loadMappings();
});
</script>

<style scoped>
.vip-category-page { display: grid; gap: 16px; }
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
</style>
