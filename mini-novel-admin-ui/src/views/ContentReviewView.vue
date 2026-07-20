<template>
  <section>
    <el-row :gutter="12" class="stats">
      <el-col v-for="item in cards" :key="item.label" :span="6">
        <el-card><div class="label">{{ item.label }}</div><div class="value">{{ item.value }}</div></el-card>
      </el-col>
    </el-row>
    <el-card>
      <div class="bar">
        <el-select v-model="sourceCode" style="width:260px" @change="switchSource">
          <el-option label="H528 授权源" value="h528_authorized" />
          <el-option label="69H Novel 授权源" value="novel69h_authorized" />
        </el-select>
        <el-alert title="仅展示 AUTHORIZED_VIP 隔离内容；免费来源不会进入此审核入口。" type="warning" :closable="false" />
      </div>
      <div class="actions">
        <el-button type="success" :disabled="!selectedBooks.length || batchLoading" :loading="batchLoading" @click="batchBookRows('APPROVE')">批量批准当前页（{{ selectedBooks.length }} 本）</el-button>
        <el-button type="danger" :disabled="!selectedBooks.length || batchLoading" :loading="batchLoading" @click="batchBookRows('REJECT')">批量拒绝当前页（{{ selectedBooks.length }} 本）</el-button>
        <el-button :disabled="!books.some(isBookReviewable) || batchLoading" @click="selectCurrentBookPage">全选当前页可审核书目</el-button>
      </div>
      <el-table ref="bookTable" :data="books" v-loading="loading" class="table" @selection-change="selectedBooks = $event">
        <el-table-column type="selection" width="48" :selectable="isBookReviewable" />
        <el-table-column prop="title" label="书名" min-width="220" />
        <el-table-column prop="sourceCode" label="来源" width="190" />
        <el-table-column prop="chapterCount" label="目录章节" width="90" />
        <el-table-column prop="reviewableCount" label="可审核正文" width="105" />
        <el-table-column prop="recrawlCount" label="待重采" width="85" />
        <el-table-column prop="blockedCount" label="硬拦截" width="85" />
        <el-table-column label="风险标签" min-width="180">
          <template #default="{ row }"><el-tag v-for="tag in row.riskLabels" :key="tag" type="warning" class="tag">{{ tag }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="openBook(row)">章节审核</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total,prev,pager,next" @current-change="load" />
    </el-card>

    <el-drawer v-model="drawer" size="80%" :title="current ? `${current.title} - 章节审核` : '章节审核'">
      <el-alert v-if="current" :title="`目录 ${current.chapterCount} 章，可审核 ${current.reviewableCount} 章，待重采 ${current.recrawlCount} 章，硬拦截 ${current.blockedCount} 章`" type="info" :closable="false" />
      <div class="actions">
        <el-button type="success" :disabled="!selected.length || batchLoading" :loading="batchLoading" @click="batchDecision('APPROVE')">批量批准（{{ selected.length }}）</el-button>
        <el-button type="danger" :disabled="!selected.length || batchLoading" :loading="batchLoading" @click="batchDecision('REJECT')">批量拒绝（{{ selected.length }}）</el-button>
        <el-button :disabled="!pageReviewable.length || batchLoading" @click="selectCurrentPage">全选当前页可审核章节</el-button>
        <span class="hint">每批最多 100 条；缺正文、非待审或状态冲突会逐项失败，不会影响其他条目。</span>
      </div>
      <el-table ref="chapterTable" :data="pagedChapters" v-loading="chapterLoading" @selection-change="selected = $event">
        <el-table-column type="selection" width="48" :selectable="isReviewable" />
        <el-table-column prop="chapterNo" label="#" width="60" />
        <el-table-column prop="title" label="章节" min-width="180" />
        <el-table-column label="状态" width="180"><template #default="{ row }"><el-tag :type="stateType(row.reviewState)">{{ stateText(row.reviewState) }}</el-tag></template></el-table-column>
        <el-table-column prop="contentLength" label="正文长度" width="95" />
        <el-table-column label="操作" width="220"><template #default="{ row }">
          <template v-if="row.reviewState === 'PENDING_REVIEW'">
            <el-button link type="primary" @click="viewContent(row)">查看隔离正文</el-button>
            <el-button link type="success" :disabled="batchLoading" @click="decideChapter(row, 'APPROVE')">批准</el-button>
            <el-button link type="danger" :disabled="batchLoading" @click="decideChapter(row, 'REJECT')">拒绝</el-button>
          </template>
          <span v-else-if="row.reviewState === 'MISSING'" class="hint">待重新采集</span>
          <span v-else-if="row.reviewState === 'EXPLICIT_MINOR_BLOCKED'" class="blocked">禁止审核通过</span>
        </template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="chapterPage" :page-size="chapterPageSize" :total="chapters.length" layout="total,prev,pager,next" @current-change="clearSelection" />
    </el-drawer>
    <el-dialog v-model="contentVisible" width="760px" title="隔离正文（仅管理员审核）">
      <el-alert title="此内容仅用于合规审核，不会通过公开 H5/API 暴露。" type="warning" :closable="false" />
      <pre class="content">{{ isolatedContent }}</pre>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { crawlerApi } from '../services/http'

const sourceCode = ref('h528_authorized')
const summary = ref({})
const books = ref([])
const selectedBooks = ref([])
const chapters = ref([])
const selected = ref([])
const total = ref(0)
const page = ref(1)
const chapterPage = ref(1)
const chapterPageSize = 20
const loading = ref(false)
const chapterLoading = ref(false)
const batchLoading = ref(false)
const drawer = ref(false)
const current = ref(null)
const chapterTable = ref(null)
const bookTable = ref(null)
const contentVisible = ref(false)
const isolatedContent = ref('')

const cards = computed(() => [
  { label: '待审状态总数', value: summary.value.pendingTotal || 0 },
  { label: '可审核正文数', value: summary.value.reviewableTotal || 0 },
  { label: '待重新采集数', value: summary.value.recrawlTotal || 0 },
  { label: '硬拦截数', value: summary.value.blockedTotal || 0 }
])
const pagedChapters = computed(() => chapters.value.slice((chapterPage.value - 1) * chapterPageSize, chapterPage.value * chapterPageSize))
const pageReviewable = computed(() => pagedChapters.value.filter(isReviewable))

function isReviewable(row) { return row.reviewState === 'PENDING_REVIEW' && Number(row.contentLength) > 0 }
function isBookReviewable(row) { return Array.isArray(row.reviewableChapterIds) && row.reviewableChapterIds.length > 0 }
async function load() {
  loading.value = true
  try {
    const params = { sourceCode: sourceCode.value }
    summary.value = await crawlerApi.get('/content-review/summary', { params })
    const data = await crawlerApi.get('/content-review/books', { params: { ...params, page: page.value, size: 20 } })
    books.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}
async function switchSource() { page.value = 1; current.value = null; chapters.value = []; drawer.value = false; await load() }
function selectCurrentBookPage() { selectedBooks.value = []; bookTable.value?.clearSelection(); books.value.filter(isBookReviewable).forEach(row => bookTable.value?.toggleRowSelection(row, true)) }
async function openBook(row) { current.value = row; chapterPage.value = 1; drawer.value = true; await loadChapters() }
async function loadChapters() {
  chapterLoading.value = true
  try { chapters.value = await crawlerApi.get(`/content-review/books/${current.value.bookRawId}/chapters`, { params: { sourceCode: sourceCode.value } }); clearSelection() }
  finally { chapterLoading.value = false }
}
function clearSelection() { selected.value = []; chapterTable.value?.clearSelection() }
function selectCurrentPage() { clearSelection(); pageReviewable.value.forEach(row => chapterTable.value?.toggleRowSelection(row, true)) }
async function viewContent(row) {
  const data = await crawlerApi.get(`/content-review/chapters/${row.chapterRawId}/content`, { params: { sourceCode: sourceCode.value } })
  isolatedContent.value = data.content
  contentVisible.value = true
}
async function ask(decision, target) {
  try {
    return (await ElMessageBox.prompt(`确认${decision === 'APPROVE' ? '批准' : '拒绝'}${target}？请输入审核备注。`, '审核确认', { inputPattern: /\S+/, inputErrorMessage: '审核备注不能为空', type: 'warning', confirmButtonText: '确认执行' })).value
  } catch { return null }
}
async function decideChapter(row, decision) {
  const remark = await ask(decision, `章节 #${row.chapterNo}`)
  if (remark === null) return
  await crawlerApi.post(`/content-review/chapters/${row.chapterRawId}/decision`, { decision, remark }, { params: { sourceCode: sourceCode.value } })
  ElMessage.success('审核结果已记录')
  await refresh()
}
async function batchDecision(decision) {
  const ids = selected.value.map(row => row.chapterRawId)
  const remark = await ask(decision, `${sourceCode.value} 当前页选中的 ${ids.length} 个章节`)
  if (remark === null) return
  batchLoading.value = true
  try {
    const result = await crawlerApi.post('/content-review/chapters/batch-decision', { decision, remark, chapterRawIds: ids }, { params: { sourceCode: sourceCode.value } })
    if (result.failureCount) {
      const failures = result.results.filter(item => !item.success).map(item => `#${item.chapterRawId}: ${item.reason}`).join('\n')
      await ElMessageBox.alert(`成功 ${result.successCount} 条，失败 ${result.failureCount} 条。\n${failures}`, '批量审核部分完成', { type: 'warning' })
    } else ElMessage.success(`批量审核完成：成功 ${result.successCount} 条`)
    await refresh()
  } finally { batchLoading.value = false }
}
async function batchBookRows(decision) {
  const ids = [...new Set(selectedBooks.value.flatMap(row => row.reviewableChapterIds || []))]
  if (ids.length > 100) { ElMessage.warning('当前选择超过单批 100 章，请减少选择数量'); return }
  const remark = await ask(decision, `${sourceCode.value} 当前页选中的 ${selectedBooks.value.length} 本、${ids.length} 个章节`)
  if (remark === null) return
  await executeBatch(decision, remark, ids)
}
async function executeBatch(decision, remark, ids) {
  batchLoading.value = true
  try {
    const result = await crawlerApi.post('/content-review/chapters/batch-decision', { decision, remark, chapterRawIds: ids }, { params: { sourceCode: sourceCode.value } })
    if (result.failureCount) {
      const failures = result.results.filter(item => !item.success).map(item => `#${item.chapterRawId}: ${item.reason}`).join('\n')
      await ElMessageBox.alert(`成功 ${result.successCount} 条，失败 ${result.failureCount} 条。\n${failures}`, '批量审核部分完成', { type: 'warning' })
    } else ElMessage.success(`批量审核完成：成功 ${result.successCount} 条`)
    selectedBooks.value = []; bookTable.value?.clearSelection(); await refresh()
  } finally { batchLoading.value = false }
}
async function refresh() {
  await load()
  const row = books.value.find(item => item.bookRawId === current.value?.bookRawId)
  if (row) { current.value = row; await loadChapters() } else { drawer.value = false; current.value = null }
}
function stateText(value) { return { CONTENT_READY: '正文已通过', PENDING_REVIEW: '待人工审核', EXPLICIT_MINOR_BLOCKED: '明确未成年人硬拦截', MISSING: '待重新采集', REVIEW_REJECTED: '已拒绝' }[value] || value }
function stateType(value) { return { CONTENT_READY: 'success', PENDING_REVIEW: 'warning', EXPLICIT_MINOR_BLOCKED: 'danger', MISSING: 'info', REVIEW_REJECTED: 'danger' }[value] || 'info' }
onMounted(load)
</script>

<style scoped>
.stats{margin-bottom:12px}.label,.hint{color:#64748b;font-size:13px}.value{font-size:28px;font-weight:700;margin-top:6px}.bar{display:flex;gap:16px;align-items:center}.bar .el-alert{flex:1}.table{margin:14px 0}.tag{margin-right:5px}.actions{display:flex;align-items:center;gap:10px;margin:14px 0}.blocked{color:#dc2626;font-size:13px}.content{white-space:pre-wrap;max-height:60vh;overflow:auto;background:#f8fafc;padding:16px;line-height:1.7}
</style>
