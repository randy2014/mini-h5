<template>
  <section class="page recharge-page">
    <van-nav-bar title="快乐币充值" left-arrow @click-left="$router.back()" />

    <van-loading v-if="loading" class="center-loading" />

    <template v-else>
      <!-- 1. 选择套餐 + 支付方式 -->
      <div v-if="phase === 'choose'" class="recharge-step">
        <div class="recharge-title">
          <h2>选择充值套餐</h2>
          <span>人工确认到账，一般几分钟内到账</span>
        </div>

        <div class="package-grid">
          <button
            v-for="pkg in info.packages"
            :key="pkg.id"
            type="button"
            class="package-card"
            :class="{ active: packageId === pkg.id }"
            @click="packageId = pkg.id"
          >
            <strong>{{ pkg.name }}</strong>
            <span>到账 {{ pkg.coins }} 快乐币</span>
            <em>¥{{ amountText(pkg.amountYuan) }}</em>
          </button>
        </div>

        <div class="pay-method">
          <div class="recharge-title">
            <h2>支付方式</h2>
          </div>
          <div class="method-row">
            <button
              type="button"
              :class="{ active: payMethod === 1 }"
              @click="payMethod = 1"
            >微信</button>
            <button
              type="button"
              :class="{ active: payMethod === 2 }"
              @click="payMethod = 2"
            >支付宝</button>
          </div>
        </div>

        <div class="summary-card">
          <div>
            <span>应付金额</span>
            <strong>¥{{ currentAmount }}</strong>
          </div>
          <van-button type="primary" block :loading="creating" @click="createOrder">
            生成订单并付款
          </van-button>
        </div>
      </div>

      <!-- 2. 展示收款码 + 订单号，等待用户转账 -->
      <div v-else-if="phase === 'paying'" class="recharge-step">
        <div class="qr-panel">
          <van-image v-if="qrUrl" :src="qrUrl" fit="contain" class="qr-image" />
          <div v-else class="qr-placeholder">
            <van-icon name="qr" />
            <span>收款码未配置，请先在后台填写 {{ payMethod === 1 ? '微信' : '支付宝' }} 收款码图片地址</span>
          </div>

          <div class="qr-amount">
            <span>请转账</span>
            <strong>¥{{ orderAmount }}</strong>
          </div>

          <div class="order-no">
            <span>转账备注订单号</span>
            <div class="order-no-row">
              <b>{{ order.orderNo }}</b>
              <button type="button" @click="copyOrderNo">复制</button>
            </div>
          </div>
        </div>

        <van-notice-bar v-if="info.notice" wrapable :text="info.notice" class="recharge-notice" />

        <van-button type="primary" block :loading="submitted" @click="submitPaid">
          我已付款，等待确认
        </van-button>
        <p v-if="submitted" class="waiting-tip">
          已提交，正在等待工作人员确认到账…
        </p>
        <van-button plain block class="rechoose" @click="resetChoose">重新选择套餐</van-button>
      </div>

      <!-- 3. 到账成功 -->
      <div v-else-if="phase === 'done'" class="recharge-step done">
        <van-icon name="passed" class="done-icon" />
        <h2>充值成功</h2>
        <p>已到账 {{ order.coins }} 快乐币</p>
        <van-button type="primary" block @click="$router.replace('/h5/coin')">查看余额</van-button>
        <van-button plain block class="rechoose" @click="resetChoose">继续充值</van-button>
      </div>

      <!-- 我的充值记录 -->
      <div v-if="orders.length" class="history">
        <div class="recharge-title">
          <h2>我的充值记录</h2>
        </div>
        <div v-for="o in orders" :key="o.id" class="history-row">
          <div>
            <strong>{{ o.coins }} 快乐币</strong>
            <small>¥{{ amountText(o.amountYuan) }} · {{ timeText(o.createdAt) }}</small>
          </div>
          <van-tag :type="statusTag(o.status)">{{ statusText(o.status) }}</van-tag>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { showToast } from 'vant';
import {
  createRechargeOrder,
  fetchMyRechargeOrders,
  fetchRechargePackages
} from '../services/recharge';

const loading = ref(true);
const creating = ref(false);
const submitted = ref(false);
const phase = ref('choose');
const packageId = ref(null);
const payMethod = ref(2); // 2=支付宝, 1=微信
const info = ref({ packages: [], wxQrUrl: '', alipayQrUrl: '', notice: '' });
const order = ref(null);
const orders = ref([]);
let pollTimer = null;

const qrUrl = computed(() => (payMethod.value === 1 ? info.value.wxQrUrl : info.value.alipayQrUrl));
const currentAmount = computed(() => {
  const pkg = info.value.packages.find((p) => p.id === packageId.value);
  return pkg ? amountText(pkg.amountYuan) : '0.00';
});
const orderAmount = computed(() => (order.value ? amountText(order.value.amountYuan) : '0.00'));

function amountText(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n.toFixed(2) : '0.00';
}

function timeText(value) {
  if (!value) return '';
  return String(value).replace('T', ' ').slice(0, 16);
}

function statusText(status) {
  return { 0: '待确认', 1: '已到账', 2: '已取消' }[status] || String(status);
}

function statusTag(status) {
  return { 0: 'warning', 1: 'success', 2: 'default' }[status] || 'default';
}

async function load() {
  loading.value = true;
  try {
    const [packages, myOrders] = await Promise.all([fetchRechargePackages(), fetchMyRechargeOrders()]);
    info.value = packages;
    // 只配置了一种收款码时，自动选中对应支付方式（当前仅内置微信收款码）
    payMethod.value = !packages.wxQrUrl && packages.alipayQrUrl ? 2 : 1;
    if (packages.packages?.length) {
      packageId.value = packages.packages[0].id;
    }
    orders.value = myOrders || [];
  } catch {
    showToast('加载失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}

async function createOrder() {
  if (!packageId.value) {
    showToast('请选择充值套餐');
    return;
  }
  creating.value = true;
  try {
    order.value = await createRechargeOrder({ packageId: packageId.value, payMethod: payMethod.value });
    phase.value = 'paying';
  } catch {
    // http 层已统一 toast
  } finally {
    creating.value = false;
  }
}

async function submitPaid() {
  submitted.value = true;
  pollOrder();
}

function pollOrder() {
  stopPolling();
  pollTimer = setInterval(async () => {
    try {
      const latest = await fetchRechargeOrder(order.value.orderNo);
      order.value = latest;
      if (latest.status === 1) {
        stopPolling();
        phase.value = 'done';
        orders.value = await fetchMyRechargeOrders();
        showToast('充值已到账');
      } else if (latest.status === 2) {
        stopPolling();
        showToast('订单已取消');
      }
    } catch {
      // 网络抖动时继续下一次轮询
    }
  }, 3000);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function resetChoose() {
  stopPolling();
  submitted.value = false;
  order.value = null;
  phase.value = 'choose';
}

async function copyOrderNo() {
  try {
    await navigator.clipboard.writeText(order.value.orderNo);
    showToast('订单号已复制');
  } catch {
    showToast(order.value.orderNo);
  }
}

onMounted(load);
onBeforeUnmount(stopPolling);
</script>

<style scoped>
.recharge-page { background: #f2f3f7; min-height: 100vh; }
.recharge-step { margin-top: 6px; }
.recharge-title { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin: 14px 0 12px; }
.recharge-title h2 { margin: 0; font-size: 17px; }
.recharge-title span { color: #a6adb9; font-size: 12px; }

.package-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.package-card { min-height: 96px; padding: 14px; border: 1px solid rgba(31, 37, 40, 0.08); border-radius: 12px; background: #fff; text-align: left; }
.package-card.active { border-color: #1f6f64; background: #e9f4f0; box-shadow: 0 0 0 2px rgba(31, 111, 100, 0.12); }
.package-card strong, .package-card span, .package-card em { display: block; }
.package-card strong { font-size: 15px; }
.package-card span { margin-top: 8px; color: #8a92a3; font-size: 12px; }
.package-card em { margin-top: 10px; color: #d9792a; font-size: 22px; font-style: normal; font-weight: 800; }

.method-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.method-row button { min-height: 44px; border: 1px solid rgba(31, 37, 40, 0.08); border-radius: 10px; background: #fff; font-size: 14px; font-weight: 700; }
.method-row button.active { border-color: #1f6f64; color: #1f6f64; background: #e9f4f0; }

.summary-card { margin-top: 16px; padding: 16px; border-radius: 12px; background: #fff; }
.summary-card > div { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 14px; }
.summary-card span { color: #8a92a3; font-size: 13px; }
.summary-card strong { color: #d9792a; font-size: 26px; }

.qr-panel { margin-top: 6px; padding: 20px; border-radius: 14px; background: #fff; text-align: center; }
.qr-image { width: 220px; height: 220px; margin: 0 auto; border: 1px solid #f0f2f7; border-radius: 8px; }
.qr-placeholder { display: grid; place-items: center; gap: 10px; width: 220px; height: 220px; margin: 0 auto; padding: 20px; border: 1px dashed #d9dfdc; border-radius: 8px; color: #a6adb9; font-size: 12px; }
.qr-placeholder .van-icon { font-size: 40px; }
.qr-amount { margin-top: 16px; }
.qr-amount span, .qr-amount strong { display: block; }
.qr-amount span { color: #8a92a3; font-size: 13px; }
.qr-amount strong { margin-top: 4px; color: #d9792a; font-size: 30px; }
.order-no { margin-top: 16px; padding-top: 14px; border-top: 1px dashed #f0f2f7; }
.order-no > span { display: block; color: #8a92a3; font-size: 12px; }
.order-no-row { display: flex; align-items: center; justify-content: center; gap: 10px; margin-top: 8px; }
.order-no-row b { font-size: 15px; letter-spacing: 0.5px; }
.order-no-row button { padding: 4px 10px; border: 1px solid #1f6f64; border-radius: 6px; background: #fff; color: #1f6f64; font-size: 12px; }

.recharge-notice { margin-top: 12px; }
.waiting-tip { margin: 10px 0 0; color: #8a92a3; font-size: 12px; text-align: center; }
.rechoose { margin-top: 10px; }

.done { padding-top: 40px; text-align: center; }
.done-icon { color: #1f6f64; font-size: 60px; }
.done h2 { margin: 14px 0 6px; font-size: 22px; }
.done p { margin: 0 0 20px; color: #8a92a3; font-size: 14px; }

.history { margin-top: 22px; }
.history-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 12px 14px; border-bottom: 1px solid #f0f2f7; background: #fff; }
.history-row:first-of-type { border-radius: 12px 12px 0 0; }
.history-row:last-of-type { border-radius: 0 0 12px 12px; border-bottom: 0; }
.history-row strong, .history-row small { display: block; }
.history-row strong { font-size: 14px; }
.history-row small { margin-top: 3px; color: #a6adb9; font-size: 11px; }
</style>
