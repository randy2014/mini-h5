<template>
  <section class="crawler-page">
    <el-tabs v-model="activeTab" class="crawler-tabs">
      <el-tab-pane label="采集源" name="sources">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="card-header">
              <span>采集源配置</span>
              <el-button type="primary" @click="saveSource">保存采集源</el-button>
            </div>
          </template>
          <el-form :model="sourceForm" label-width="96px" class="form-grid">
            <el-form-item label="源编码">
              <el-input v-model="sourceForm.sourceCode" placeholder="qidian_public" />
            </el-form-item>
            <el-form-item label="源名称">
              <el-input v-model="sourceForm.name" placeholder="起点公开榜单" />
            </el-form-item>
            <el-form-item label="站点域名">
              <el-input v-model="sourceForm.baseUrl" placeholder="https://www.qidian.com" />
            </el-form-item>
            <el-form-item label="源类型">
              <el-select v-model="sourceForm.sourceType">
                <el-option label="公开内容" value="PUBLIC" />
                <el-option label="授权 VIP" value="AUTHORIZED_VIP" />
                <el-option label="手动导入" value="IMPORT" />
              </el-select>
            </el-form-item>
            <el-form-item label="认证方式">
              <el-select v-model="sourceForm.authMode">
                <el-option label="无需认证" value="NONE" />
                <el-option label="账号密码" value="PASSWORD" />
                <el-option label="Cookie" value="COOKIE" />
              </el-select>
            </el-form-item>
            <el-form-item label="优先级">
              <el-input-number v-model="sourceForm.priority" :min="1" :max="999" />
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="sourceForm.enabled" />
            </el-form-item>
            <el-form-item label="备注" class="wide">
              <el-input v-model="sourceForm.remark" placeholder="仅接入授权或公开可访问内容" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never">
          <template #header>采集源列表</template>
          <el-table :data="sources" v-loading="loading.sources">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="sourceCode" label="编码" width="160" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="baseUrl" label="域名" min-width="220" />
            <el-table-column prop="sourceType" label="类型" width="130" />
            <el-table-column prop="authMode" label="认证" width="110" />
            <el-table-column prop="priority" label="优先级" width="90" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="editSource(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="账号凭据" name="credentials">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="card-header">
              <span>授权账号配置</span>
              <el-button type="primary" @click="saveCredential">保存凭据</el-button>
            </div>
          </template>
          <el-form :model="credentialForm" label-width="110px" class="form-grid">
            <el-form-item label="采集源">
              <el-select v-model="credentialForm.sourceId" placeholder="选择采集源">
                <el-option v-for="item in sources" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="凭据名称">
              <el-input v-model="credentialForm.name" placeholder="起点 VIP 账号" />
            </el-form-item>
            <el-form-item label="认证方式">
              <el-select v-model="credentialForm.authMode">
                <el-option label="账号密码" value="PASSWORD" />
                <el-option label="Cookie" value="COOKIE" />
              </el-select>
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="credentialForm.username" placeholder="登录用户名" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="credentialForm.passwordCipher" type="password" show-password placeholder="留空则保持原密码" />
            </el-form-item>
            <el-form-item label="登录地址">
              <el-input v-model="credentialForm.loginUrl" placeholder="https://..." />
            </el-form-item>
            <el-form-item label="Cookie" class="wide">
              <el-input v-model="credentialForm.cookieText" type="textarea" :rows="3" placeholder="可选，留空则保持原 Cookie" />
            </el-form-item>
            <el-form-item label="请求头 JSON" class="wide">
              <el-input v-model="credentialForm.headersJson" type="textarea" :rows="3" placeholder='{"User-Agent":"..."}' />
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="credentialForm.enabled" />
            </el-form-item>
            <el-form-item label="备注" class="wide">
              <el-input v-model="credentialForm.remark" placeholder="用于授权采集场景，请确保账号具有对应授权" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never">
          <template #header>账号凭据列表</template>
          <el-table :data="credentials" v-loading="loading.credentials">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="sourceId" label="源 ID" width="90" />
            <el-table-column prop="name" label="名称" min-width="150" />
            <el-table-column prop="authMode" label="认证方式" width="110" />
            <el-table-column prop="username" label="用户名" width="160" />
            <el-table-column prop="status" label="校验状态" width="120" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="180" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="editCredential(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="榜单源" name="ranks">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="card-header">
              <span>榜单源配置</span>
              <el-button type="primary" @click="saveRank">保存榜单源</el-button>
            </div>
          </template>
          <el-form :model="rankForm" label-width="96px" class="form-grid">
            <el-form-item label="采集源">
              <el-select v-model="rankForm.sourceId" placeholder="选择采集源">
                <el-option v-for="item in sources" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="榜单名称">
              <el-input v-model="rankForm.rankName" placeholder="月票榜" />
            </el-form-item>
            <el-form-item label="榜单类型">
              <el-select v-model="rankForm.rankType">
                <el-option label="月榜" value="MONTH" />
                <el-option label="周榜" value="WEEK" />
                <el-option label="完结榜" value="COMPLETED" />
                <el-option label="热度榜" value="HOT" />
              </el-select>
            </el-form-item>
            <el-form-item label="榜单地址" class="wide">
              <el-input v-model="rankForm.rankUrl" placeholder="https://www.qidian.com/rank/yuepiao/" />
            </el-form-item>
            <el-form-item label="最大书数">
              <el-input-number v-model="rankForm.maxBooks" :min="1" :max="500" />
            </el-form-item>
            <el-form-item label="优先完结">
              <el-switch v-model="rankForm.preferCompleted" />
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="rankForm.enabled" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never">
          <template #header>榜单源列表</template>
          <el-table :data="rankSources" v-loading="loading.ranks">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="sourceId" label="源 ID" width="90" />
            <el-table-column prop="rankName" label="名称" min-width="150" />
            <el-table-column prop="rankType" label="类型" width="110" />
            <el-table-column prop="rankUrl" label="地址" min-width="260" />
            <el-table-column prop="maxBooks" label="数量" width="90" />
            <el-table-column label="优先完结" width="100">
              <template #default="{ row }">{{ row.preferCompleted ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="editRank(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="调度计划" name="schedules">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="card-header">
              <span>抓取频率配置</span>
              <el-button type="primary" @click="saveSchedule">保存计划</el-button>
            </div>
          </template>
          <el-form :model="scheduleForm" label-width="110px" class="form-grid">
            <el-form-item label="计划名称">
              <el-input v-model="scheduleForm.name" placeholder="热门榜单每日抓取" />
            </el-form-item>
            <el-form-item label="采集源">
              <el-select v-model="scheduleForm.sourceId" placeholder="全部或指定源">
                <el-option v-for="item in sources" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="授权凭据">
              <el-select v-model="scheduleForm.credentialId" clearable placeholder="公开抓取可不选">
                <el-option v-for="item in enabledCredentials" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="执行时间">
              <el-input v-model="scheduleForm.scheduleTimes" placeholder="00:00,08:00,14:00" />
            </el-form-item>
            <el-form-item label="时区">
              <el-input v-model="scheduleForm.timezone" placeholder="Asia/Shanghai" />
            </el-form-item>
            <el-form-item label="公开章节">
              <el-switch v-model="scheduleForm.crawlPublic" />
            </el-form-item>
            <el-form-item label="授权 VIP 章节">
              <el-switch v-model="scheduleForm.crawlVip" />
            </el-form-item>
            <el-form-item label="完成后清洗">
              <el-switch v-model="scheduleForm.autoMerge" />
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="scheduleForm.enabled" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never">
          <template #header>计划列表</template>
          <el-table :data="schedules" v-loading="loading.schedules">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="sourceId" label="源 ID" width="90" />
            <el-table-column prop="credentialId" label="凭据 ID" width="90" />
            <el-table-column prop="scheduleTimes" label="执行时间" width="170" />
            <el-table-column label="模式" width="180">
              <template #default="{ row }">{{ row.crawlVip ? '公开 + 授权 VIP' : '公开内容' }}</template>
            </el-table-column>
            <el-table-column label="自动清洗" width="100">
              <template #default="{ row }">{{ row.autoMerge ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastRunAt" label="最近执行" width="180" />
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="editSchedule(row)">编辑</el-button>
                <el-button link type="success" @click="runSchedule(row)">立即执行</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="采集任务" name="tasks">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>采集任务</span>
              <el-button @click="loadTasks">刷新</el-button>
            </div>
          </template>
          <el-table :data="tasks" v-loading="loading.tasks">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="scheduleId" label="计划 ID" width="90" />
            <el-table-column prop="sourceId" label="源 ID" width="90" />
            <el-table-column prop="credentialId" label="凭据 ID" width="90" />
            <el-table-column prop="taskType" label="类型" width="130" />
            <el-table-column label="状态" width="130">
              <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="totalCount" label="总数" width="80" />
            <el-table-column prop="successCount" label="成功" width="80" />
            <el-table-column prop="failCount" label="失败" width="80" />
            <el-table-column prop="message" label="信息" min-width="260" />
            <el-table-column prop="createdAt" label="创建时间" width="180" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="清洗入库" name="merge">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>清洗入库任务</span>
              <div class="header-actions">
                <el-button type="primary" @click="runPendingMergeTasks">执行待清洗</el-button>
                <el-button @click="loadMergeTasks">刷新</el-button>
              </div>
            </div>
          </template>
          <el-table :data="mergeTasks" v-loading="loading.merge">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="crawlTaskId" label="采集任务 ID" width="110" />
            <el-table-column label="状态" width="130">
              <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="totalCount" label="总数" width="80" />
            <el-table-column prop="mergedCount" label="已入库" width="90" />
            <el-table-column prop="pendingReviewCount" label="待审核" width="90" />
            <el-table-column prop="failedCount" label="失败" width="80" />
            <el-table-column prop="message" label="信息" min-width="260" />
            <el-table-column prop="createdAt" label="创建时间" width="180" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { crawlerApi } from '../services/http';

const activeTab = ref('sources');
const sources = ref([]);
const credentials = ref([]);
const rankSources = ref([]);
const schedules = ref([]);
const tasks = ref([]);
const mergeTasks = ref([]);
const loading = reactive({ sources: false, credentials: false, ranks: false, schedules: false, tasks: false, merge: false });

const sourceForm = reactive(defaultSource());
const credentialForm = reactive(defaultCredential());
const rankForm = reactive(defaultRank());
const scheduleForm = reactive(defaultSchedule());

const enabledCredentials = computed(() => credentials.value.filter((item) => item.enabled));

function defaultSource() {
  return {
    id: null,
    sourceCode: 'qidian_public',
    name: '起点公开榜单',
    baseUrl: 'https://www.qidian.com',
    sourceType: 'PUBLIC',
    authMode: 'NONE',
    enabled: true,
    priority: 10,
    remark: ''
  };
}

function defaultCredential() {
  return {
    id: null,
    sourceId: null,
    name: '授权采集账号',
    authMode: 'PASSWORD',
    username: '',
    passwordCipher: '',
    cookieText: '',
    headersJson: '',
    loginUrl: '',
    status: 'UNVERIFIED',
    enabled: true,
    remark: ''
  };
}

function defaultRank() {
  return {
    id: null,
    sourceId: null,
    rankName: '月票榜',
    rankType: 'MONTH',
    rankUrl: 'https://www.qidian.com/rank/yuepiao/',
    preferCompleted: true,
    maxBooks: 50,
    enabled: true
  };
}

function defaultSchedule() {
  return {
    id: null,
    name: '热门榜单每日抓取',
    sourceId: null,
    credentialId: null,
    scheduleTimes: '00:00,08:00,14:00',
    timezone: 'Asia/Shanghai',
    crawlPublic: true,
    crawlVip: false,
    autoMerge: true,
    enabled: true
  };
}

function assignForm(target, value) {
  Object.keys(target).forEach((key) => delete target[key]);
  Object.assign(target, value);
}

function statusType(status) {
  return status === 'MERGED' || status === 'SUCCESS' || status === 'DONE'
    ? 'success'
    : status === 'FAILED'
      ? 'danger'
      : status === 'RUNNING' || status === 'MERGING'
        ? 'warning'
        : 'info';
}

async function loadSources() {
  loading.sources = true;
  try {
    sources.value = await crawlerApi.get('/config/sources');
    const firstSource = sources.value[0]?.id || null;
    if (!credentialForm.sourceId) credentialForm.sourceId = firstSource;
    if (!rankForm.sourceId) rankForm.sourceId = firstSource;
    if (!scheduleForm.sourceId) scheduleForm.sourceId = firstSource;
  } finally {
    loading.sources = false;
  }
}

async function loadCredentials() {
  loading.credentials = true;
  try {
    credentials.value = await crawlerApi.get('/config/credentials');
  } finally {
    loading.credentials = false;
  }
}

async function loadRanks() {
  loading.ranks = true;
  try {
    rankSources.value = await crawlerApi.get('/config/rank-sources');
  } finally {
    loading.ranks = false;
  }
}

async function loadSchedules() {
  loading.schedules = true;
  try {
    schedules.value = await crawlerApi.get('/config/schedules');
  } finally {
    loading.schedules = false;
  }
}

async function loadTasks() {
  loading.tasks = true;
  try {
    tasks.value = await crawlerApi.get('/config/tasks');
  } finally {
    loading.tasks = false;
  }
}

async function loadMergeTasks() {
  loading.merge = true;
  try {
    mergeTasks.value = await crawlerApi.get('/config/merge-tasks');
  } finally {
    loading.merge = false;
  }
}

async function loadAll() {
  await loadSources();
  await Promise.all([loadCredentials(), loadRanks(), loadSchedules(), loadTasks(), loadMergeTasks()]);
}

async function saveSource() {
  const payload = { ...sourceForm };
  const request = payload.id ? crawlerApi.put(`/config/sources/${payload.id}`, payload) : crawlerApi.post('/config/sources', payload);
  await request;
  ElMessage.success('采集源已保存');
  assignForm(sourceForm, defaultSource());
  await loadSources();
}

function editSource(row) {
  assignForm(sourceForm, { ...defaultSource(), ...row });
  activeTab.value = 'sources';
}

async function saveCredential() {
  const payload = { ...credentialForm };
  if (!payload.passwordCipher) payload.passwordCipher = '__KEEP__';
  if (!payload.cookieText) payload.cookieText = '__KEEP__';
  const request = payload.id ? crawlerApi.put(`/config/credentials/${payload.id}`, payload) : crawlerApi.post('/config/credentials', payload);
  await request;
  ElMessage.success('账号凭据已保存');
  assignForm(credentialForm, { ...defaultCredential(), sourceId: sources.value[0]?.id || null });
  await loadCredentials();
}

function editCredential(row) {
  assignForm(credentialForm, { ...defaultCredential(), ...row, passwordCipher: '', cookieText: '' });
  activeTab.value = 'credentials';
}

async function saveRank() {
  const payload = { ...rankForm };
  const request = payload.id ? crawlerApi.put(`/config/rank-sources/${payload.id}`, payload) : crawlerApi.post('/config/rank-sources', payload);
  await request;
  ElMessage.success('榜单源已保存');
  assignForm(rankForm, { ...defaultRank(), sourceId: sources.value[0]?.id || null });
  await loadRanks();
}

function editRank(row) {
  assignForm(rankForm, { ...defaultRank(), ...row });
  activeTab.value = 'ranks';
}

async function saveSchedule() {
  const payload = { ...scheduleForm };
  const request = payload.id ? crawlerApi.put(`/config/schedules/${payload.id}`, payload) : crawlerApi.post('/config/schedules', payload);
  await request;
  ElMessage.success('调度计划已保存');
  assignForm(scheduleForm, { ...defaultSchedule(), sourceId: sources.value[0]?.id || null });
  await loadSchedules();
}

function editSchedule(row) {
  assignForm(scheduleForm, { ...defaultSchedule(), ...row });
  activeTab.value = 'schedules';
}

async function runSchedule(row) {
  await crawlerApi.post(`/config/schedules/${row.id}/run-now`);
  ElMessage.success('采集任务已创建');
  activeTab.value = 'tasks';
  await Promise.all([loadSchedules(), loadTasks(), loadMergeTasks()]);
}

async function runPendingMergeTasks() {
  await crawlerApi.post('/config/merge-tasks/run-pending');
  ElMessage.success('待清洗任务已执行');
  await Promise.all([loadMergeTasks(), loadTasks()]);
}

onMounted(loadAll);
</script>
