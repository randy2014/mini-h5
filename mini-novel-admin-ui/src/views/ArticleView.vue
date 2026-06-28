<template>
  <section>
    <el-card shadow="never">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索标题/作者" clearable @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="状态" clearable>
          <el-option label="正常" :value="1" />
          <el-option label="完结" :value="2" />
          <el-option label="下架" :value="0" />
          <el-option label="草稿" :value="3" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="openEdit()">新增文章</el-button>
        <el-button type="success" @click="openImport">TXT 导入</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="author" label="作者" width="130" />
        <el-table-column prop="wordCount" label="字数" width="100" />
        <el-table-column prop="latestChapterTitle" label="最新章节" min-width="220" />
        <el-table-column prop="vipRequired" label="VIP" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.vipRequired" type="warning">VIP</el-tag>
            <span v-else>免费</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'danger' : 'success'">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="185" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openChapters(row)">章节</el-button>
            <el-button link :type="row.status === 0 ? 'success' : 'danger'" @click="toggleStatus(row)">
              {{ row.status === 0 ? '上架' : '下架' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="editVisible" title="文章编辑" width="680px">
      <el-form label-width="110px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="form.author" /></el-form-item>
        <el-form-item label="分类ID"><el-input-number v-model="form.categoryId" :min="1" /></el-form-item>
        <el-form-item label="整本VIP"><el-switch v-model="form.vipRequired" /></el-form-item>
        <el-form-item label="免费章节数"><el-input-number v-model="form.freeChapterCount" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="下架" :value="0" />
            <el-option label="正常" :value="1" />
            <el-option label="完结" :value="2" />
            <el-option label="草稿" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介"><el-input v-model="form.intro" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="TXT 正文导入" width="840px">
      <el-form label-width="110px">
        <el-form-item label="标题"><el-input v-model="importForm.title" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="importForm.author" /></el-form-item>
        <el-form-item label="分类ID"><el-input-number v-model="importForm.categoryId" :min="1" /></el-form-item>
        <el-form-item label="整本VIP"><el-switch v-model="importForm.vipRequired" /></el-form-item>
        <el-form-item label="免费章节数"><el-input-number v-model="importForm.freeChapterCount" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="importForm.status">
            <el-option label="正常" :value="1" />
            <el-option label="完结" :value="2" />
            <el-option label="草稿" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介"><el-input v-model="importForm.intro" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="importForm.content"
            type="textarea"
            :rows="16"
            placeholder="粘贴 TXT 正文。支持按“第一章 标题 / 第1章 标题 / Chapter 1”自动切分章节。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="import-hint">当前字数：{{ importForm.content.length }}</span>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="submitImport">导入正文</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="chapterVisible" title="章节管理" size="720px">
      <el-table :data="chapters">
        <el-table-column prop="chapterNo" label="序号" width="80" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="vip" label="VIP" width="100">
          <template #default="{ row }"><el-switch v-model="row.vip" @change="saveChapterVip(row)" /></template>
        </el-table-column>
        <el-table-column prop="priceCoin" label="价格" width="110">
          <template #default="{ row }"><el-input-number v-model="row.priceCoin" :min="0" size="small" @change="saveChapterVip(row)" /></template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi } from '../services/http';

const loading = ref(false);
const importing = ref(false);
const rows = ref([]);
const chapters = ref([]);
const editVisible = ref(false);
const importVisible = ref(false);
const chapterVisible = ref(false);
const query = reactive({ keyword: '', status: null });
const form = reactive({});
const importForm = reactive(defaultImportForm());

function defaultImportForm() {
  return {
    title: '手动导入示例小说',
    author: '人工导入',
    categoryId: 1,
    status: 1,
    vipRequired: false,
    freeChapterCount: 0,
    intro: '后台手动导入的正文内容',
    content: ''
  };
}

function statusText(status) {
  return ({ 0: '下架', 1: '正常', 2: '完结', 3: '草稿', 4: '审核中' })[status] || '未知';
}

async function load() {
  loading.value = true;
  try {
    rows.value = await adminApi.get('/novels', { params: query });
  } finally {
    loading.value = false;
  }
}

function openEdit(row) {
  Object.keys(form).forEach((key) => delete form[key]);
  Object.assign(form, row || { title: '', author: '', categoryId: 1, status: 1, vipRequired: false, freeChapterCount: 0 });
  editVisible.value = true;
}

async function save() {
  if (form.id) {
    await adminApi.put(`/novels/${form.id}`, form);
  } else {
    await adminApi.post('/novels', form);
  }
  ElMessage.success('已保存');
  editVisible.value = false;
  load();
}

function openImport() {
  Object.assign(importForm, defaultImportForm());
  importVisible.value = true;
}

async function submitImport() {
  importing.value = true;
  try {
    const novel = await adminApi.post('/novels/import-text', importForm);
    ElMessage.success(`导入成功：${novel.title}`);
    importVisible.value = false;
    await load();
  } finally {
    importing.value = false;
  }
}

async function toggleStatus(row) {
  const status = row.status === 0 ? 1 : 0;
  let reason = '';
  if (status === 0) {
    const result = await ElMessageBox.prompt('请输入下架原因', '文章下架', { inputPlaceholder: '版权/违规/人工处理' });
    reason = result.value;
  }
  await adminApi.put(`/novels/${row.id}/status`, { status, reason, operatorId: 1 });
  ElMessage.success(status === 0 ? '已下架' : '已上架');
  load();
}

async function openChapters(row) {
  chapters.value = await adminApi.get(`/novels/${row.id}/chapters`);
  chapterVisible.value = true;
}

async function saveChapterVip(row) {
  await adminApi.put(`/novels/chapters/${row.id}/vip`, { vip: row.vip, priceCoin: row.priceCoin });
  ElMessage.success('章节 VIP 已更新');
}

onMounted(load);
</script>
