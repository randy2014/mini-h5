<template>
  <section>
    <el-row :gutter="12" class="stats"><el-col v-for="item in cards" :key="item.label" :xs="12" :sm="6"><el-card shadow="never"><div class="label">{{item.label}}</div><div class="value">{{item.value}}</div></el-card></el-col></el-row>
    <el-card shadow="never">
      <template #header><div class="bar"><b>成人授权内容审核</b><el-button @click="load">刷新</el-button></div></template>
      <el-alert title="只有已保存到隔离区的待审正文才能审核；仅有状态但无正文的章节显示为“待重新采集”。审核不会自动上架。" type="warning" :closable="false" show-icon/>
      <el-table :data="books" v-loading="loading" class="table">
        <el-table-column prop="title" label="书名" min-width="160"/><el-table-column prop="sourceCode" label="来源" width="180"/><el-table-column prop="chapterCount" label="目录章节" width="90"/><el-table-column prop="reviewableCount" label="可审核正文" width="105"/><el-table-column prop="recrawlCount" label="待重采" width="85"/><el-table-column prop="blockedCount" label="硬拦截" width="85"/><el-table-column prop="missingCount" label="缺失" width="75"/>
        <el-table-column label="风险标签" min-width="190"><template #default="{row}"><el-tag v-for="tag in row.riskLabels" :key="tag" :type="tag==='EXPLICIT_MINOR_BLOCKED'?'danger':'warning'" class="tag">{{tag}}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.reviewableCount?'warning':'info'">{{row.reviewableCount?'可审核':'待重新采集'}}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="primary" @click="openBook(row)">章节审核</el-button></template></el-table-column>
      </el-table><el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total,prev,pager,next" @current-change="load"/>
    </el-card>
    <el-drawer v-model="drawer" size="75%" :title="current?`${current.title} - 章节审核`:'章节审核'">
      <el-alert v-if="current" :title="`目录 ${current.chapterCount} 章，可审核 ${current.reviewableCount} 章，待重采 ${current.recrawlCount} 章，硬拦截 ${current.blockedCount} 章`" type="info" :closable="false"/>
      <div v-if="current" class="actions"><el-button type="success" :disabled="!canApproveBook" @click="decideBook('APPROVE')">整书批准</el-button><el-button type="danger" :disabled="!current.reviewableCount" @click="decideBook('REJECT')">整书拒绝</el-button><span class="hint">整书批准要求无硬拦截、无缺失且所有必要章节均有正文。</span></div>
      <el-table :data="chapters" v-loading="chapterLoading"><el-table-column prop="chapterNo" label="#" width="60"/><el-table-column prop="title" label="章节" min-width="180"/><el-table-column label="状态" width="190"><template #default="{row}"><el-tag :type="stateType(row.reviewState)">{{stateText(row.reviewState)}}</el-tag></template></el-table-column><el-table-column prop="contentLength" label="正文长度" width="95"/><el-table-column label="操作" width="220"><template #default="{row}"><template v-if="row.reviewState==='PENDING_REVIEW'"><el-button link type="primary" @click="viewContent(row)">查看隔离正文</el-button><el-button link type="success" @click="decideChapter(row,'APPROVE')">批准</el-button><el-button link type="danger" @click="decideChapter(row,'REJECT')">拒绝</el-button></template><span v-else-if="row.reviewState==='MISSING'" class="hint">待重新采集</span><span v-else-if="row.reviewState==='EXPLICIT_MINOR_BLOCKED'" class="blocked">禁止审核通过</span></template></el-table-column></el-table>
    </el-drawer>
    <el-dialog v-model="contentVisible" width="760px" title="隔离正文（仅管理员审核）"><el-alert title="此内容仅用于合规审核，不会通过公开 H5/API 暴露。" type="warning" :closable="false"/><pre class="content">{{isolatedContent}}</pre></el-dialog>
  </section>
</template>
<script setup>
import{computed,onMounted,ref}from'vue';import{ElMessage,ElMessageBox}from'element-plus';import{crawlerApi}from'../services/http';
const summary=ref({}),books=ref([]),chapters=ref([]),total=ref(0),page=ref(1),loading=ref(false),chapterLoading=ref(false),drawer=ref(false),current=ref(null),contentVisible=ref(false),isolatedContent=ref('');
const cards=computed(()=>[{label:'待审状态总数',value:summary.value.pendingTotal||0},{label:'可审核正文数',value:summary.value.reviewableTotal||0},{label:'待重新采集数',value:summary.value.recrawlTotal||0},{label:'硬拦截数',value:summary.value.blockedTotal||0}]);
const canApproveBook=computed(()=>current.value&&current.value.reviewableCount>0&&!current.value.blockedCount&&!current.value.missingCount&&!current.value.recrawlCount);
async function load(){loading.value=true;try{summary.value=await crawlerApi.get('/content-review/summary');const d=await crawlerApi.get('/content-review/books',{params:{page:page.value,size:20}});books.value=d.records;total.value=d.total}finally{loading.value=false}}
async function openBook(row){current.value=row;drawer.value=true;await loadChapters()}async function loadChapters(){chapterLoading.value=true;try{chapters.value=await crawlerApi.get(`/content-review/books/${current.value.bookRawId}/chapters`)}finally{chapterLoading.value=false}}
async function viewContent(row){const d=await crawlerApi.get(`/content-review/chapters/${row.chapterRawId}/content`);isolatedContent.value=d.content;contentVisible.value=true}
async function ask(decision,target){try{return(await ElMessageBox.prompt(`确认${decision==='APPROVE'?'批准':'拒绝'}${target}？请输入审核备注。`,'审核确认',{inputPattern:/\S+/,inputErrorMessage:'审核备注不能为空',type:'warning'})).value}catch{return null}}
async function decideChapter(row,decision){const remark=await ask(decision,`章节 #${row.chapterNo}`);if(remark===null)return;await crawlerApi.post(`/content-review/chapters/${row.chapterRawId}/decision`,{decision,remark});ElMessage.success('审核结果已记录');await refresh()}
async function decideBook(decision){const remark=await ask(decision,'整本书');if(remark===null)return;await crawlerApi.post(`/content-review/books/${current.value.bookRawId}/decision`,{decision,remark});ElMessage.success('整书审核结果已记录');await refresh()}
async function refresh(){await load();const row=books.value.find(x=>x.bookRawId===current.value.bookRawId);if(row)current.value=row;await loadChapters()}
function stateText(v){return{CONTENT_READY:'正文已通过',PENDING_REVIEW:'待人工审核',EXPLICIT_MINOR_BLOCKED:'明确未成年人硬拦截',MISSING:'待重新采集',REVIEW_REJECTED:'已拒绝'}[v]||v}function stateType(v){return{CONTENT_READY:'success',PENDING_REVIEW:'warning',EXPLICIT_MINOR_BLOCKED:'danger',MISSING:'info',REVIEW_REJECTED:'danger'}[v]||'info'}onMounted(load);
</script>
<style scoped>.stats{margin-bottom:12px}.label,.hint{color:#64748b;font-size:13px}.value{font-size:28px;font-weight:700;margin-top:6px}.bar{display:flex;justify-content:space-between}.table{margin:14px 0}.tag{margin-right:5px}.actions{margin:14px 0}.blocked{color:#dc2626;font-size:13px}.content{white-space:pre-wrap;max-height:60vh;overflow:auto;background:#f8fafc;padding:16px;line-height:1.7}</style>
