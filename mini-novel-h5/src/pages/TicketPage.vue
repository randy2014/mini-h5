<template>
  <section class="page ticket-page">
    <van-nav-bar
      title="工单服务"
      left-arrow
      right-text="新增"
      @click-left="$router.back()"
      @click-right="openCreate"
    />

    <div class="list">
      <div v-for="t in tickets" :key="t.id" class="ticket-card" @click="openDetail(t)">
        <div class="ticket-head">
          <span class="ticket-title">{{ t.title }}</span>
          <van-tag :type="t.status === 'OPEN' ? 'warning' : 'success'">
            {{ t.status === 'OPEN' ? '处理中' : '已关闭' }}
          </van-tag>
        </div>
        <div class="ticket-content">{{ t.content }}</div>
        <div class="ticket-foot">
          <span>{{ formatTime(t.createdAt) }}</span>
          <van-button v-if="t.status === 'OPEN'" size="mini" plain type="danger" @click.stop="closeTicket(t)">
            关闭工单
          </van-button>
        </div>
      </div>
      <van-empty v-if="tickets.length === 0" description="暂无工单" />
    </div>

    <van-dialog v-model:show="createVisible" title="新增工单" show-cancel-button @confirm="submitCreate">
      <div class="create-form">
        <van-field v-model="form.title" placeholder="标题（必填）" maxlength="100" />
        <van-field
          v-model="form.content"
          type="textarea"
          placeholder="正文（必填）"
          rows="4"
          maxlength="300"
          show-word-limit
        />
        <div class="word-count">标题+正文合计 {{ form.title.length + form.content.length }}/300</div>
      </div>
    </van-dialog>

    <van-popup v-model:show="detailVisible" position="bottom" round>
      <div class="detail">
        <h3>{{ current?.title }}</h3>
        <p class="detail-content">{{ current?.content }}</p>
        <div class="replies">
          <div class="reply-title">回复</div>
          <div v-for="r in replies" :key="r.id" class="reply">
            <span :class="r.replierType === 'ADMIN' ? 'admin' : 'user'">
              {{ r.replierType === 'ADMIN' ? '客服' : '我' }}
            </span>
            <p>{{ r.content }}</p>
          </div>
          <div v-if="replies.length === 0" class="no-reply">暂无回复</div>
        </div>
      </div>
    </van-popup>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { showConfirmDialog, showToast } from 'vant';
import { fetchTickets, fetchTicketDetail, createTicket, closeTicket as closeTicketApi } from '../services/ticket';

const tickets = ref([]);
const createVisible = ref(false);
const detailVisible = ref(false);
const current = ref(null);
const replies = ref([]);
const form = reactive({ title: '', content: '' });

onMounted(load);

async function load() {
  tickets.value = await fetchTickets();
}

function openCreate() {
  form.title = '';
  form.content = '';
  createVisible.value = true;
}

async function submitCreate() {
  const title = form.title.trim();
  const content = form.content.trim();
  if (!title || !content) {
    showToast('标题和正文不能为空');
    return false;
  }
  if (title.length + content.length > 300) {
    showToast('标题和正文合计不能超过300字');
    return false;
  }
  await createTicket(title, content);
  showToast('已提交');
  await load();
  return true;
}

async function closeTicket(t) {
  try {
    await showConfirmDialog({ title: '关闭工单', message: '确认关闭该工单？' });
  } catch {
    return;
  }
  await closeTicketApi(t.id);
  showToast('已关闭');
  await load();
}

async function openDetail(t) {
  current.value = t;
  try {
    const data = await fetchTicketDetail(t.id);
    replies.value = data.replies || [];
  } catch {
    replies.value = [];
  }
  detailVisible.value = true;
}

function formatTime(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.ticket-page { background: #f2f3f7; min-height: 100vh; }
.list { padding: 14px; }
.ticket-card { background: #fff; border-radius: 12px; padding: 13px; margin-bottom: 11px; }
.ticket-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.ticket-title { font-size: 14px; font-weight: 700; flex: 1; }
.ticket-content { font-size: 12px; color: #6b7280; margin: 8px 0; line-height: 1.6; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.ticket-foot { display: flex; align-items: center; justify-content: space-between; font-size: 11px; color: #a6adb9; }
.create-form { padding: 16px; }
.word-count { text-align: right; font-size: 11px; color: #a6adb9; margin-top: 8px; }
.detail { padding: 20px 16px 30px; }
.detail h3 { font-size: 16px; margin-bottom: 8px; }
.detail-content { font-size: 13px; color: #4b5563; line-height: 1.7; margin-bottom: 16px; }
.replies { border-top: 1px solid #f0f2f7; padding-top: 12px; }
.reply-title { font-size: 13px; font-weight: 700; margin-bottom: 10px; }
.reply { margin-bottom: 12px; }
.reply span { display: inline-block; font-size: 11px; padding: 2px 8px; border-radius: 8px; margin-bottom: 4px; }
.reply span.admin { background: #e9f4f0; color: #1f6f64; }
.reply span.user { background: #eef2f6; color: #314c5c; }
.reply p { font-size: 13px; color: #4b5563; line-height: 1.6; margin: 0; }
.no-reply { font-size: 12px; color: #a6adb9; }
</style>
