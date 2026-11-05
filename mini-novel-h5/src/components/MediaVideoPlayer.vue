<template>
  <div ref="rootEl" class="mvp" :class="{ 'is-playing': playing }" @contextmenu.prevent>
    <video
      ref="videoEl"
      class="mvp-video"
      :poster="poster || undefined"
      :preload="activated ? 'metadata' : 'none'"
      playsinline
      webkit-playsinline
      x5-playsinline
      x5-video-player-type="h5-page"
      x5-video-player-fullscreen="false"
      x-webkit-airplay="deny"
      disablepictureinpicture
      disableRemotePlayback
      controlslist="nodownload noplaybackrate noremoteplayback"
      @loadedmetadata="onMeta"
      @durationchange="onMeta"
      @timeupdate="onTime"
      @waiting="buffering = true"
      @playing="onPlaying"
      @pause="onPause"
      @ended="onEnded"
      @error="onError"
      @play="onPlayEvent"
      @dragstart.prevent
      @selectstart.prevent
    />

    <!-- 未开始播放：封面帧 + 播放按钮（点击才加载/播放，禁止长按保存与右键菜单） -->
    <div v-if="!activated || error" class="mvp-cover" @click="startPlay">
      <img v-if="poster" :src="poster" class="mvp-poster" draggable="false" alt="" />
      <div class="mvp-mask"></div>
      <div class="mvp-bigplay">
        <van-icon :name="error ? 'replay' : 'play'" size="22" color="#1f6f64" />
      </div>
      <div class="mvp-hint">{{ error || hint }}</div>
    </div>

    <!-- 播放中：整块画面点击暂停/继续（不渲染任何原生控件，故没有下载/分享/AirPlay 入口） -->
    <div v-else class="mvp-surface" @click="togglePlay">
      <div v-if="buffering" class="mvp-loading"><van-loading size="20" color="#ffffff" /></div>
    </div>

    <!-- 自定义控制条：播放/暂停 + 进度 + 时间 + 全屏（仅在容器全屏可用时显示） -->
    <div v-if="activated && !error" class="mvp-bar" @click.stop @contextmenu.prevent>
      <button type="button" class="mvp-btn" :aria-label="playing ? '暂停' : '播放'" @click.stop="togglePlay">
        <van-icon :name="playing ? 'pause' : 'play'" size="16" color="#ffffff" />
      </button>
      <span class="mvp-time">{{ fmtTime(current) }}</span>
      <input
        class="mvp-range"
        type="range"
        min="0"
        :max="duration || 0"
        step="0.1"
        :value="current"
        aria-label="播放进度"
        @input="onSeek"
      />
      <span class="mvp-time">{{ fmtTime(duration) }}</span>
      <button v-if="fullscreenAvailable" type="button" class="mvp-btn" aria-label="全屏" @click.stop="toggleFullscreen">
        <van-icon name="expand-o" size="16" color="#ffffff" />
      </button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';

/**
 * H5 媒体视频播放器（自定义控件）。
 *
 * 为什么不用原生 controls：原生控件会在移动端（iOS Safari / 微信 X5）带出
 * **下载、分享、AirPlay、画中画**等入口，`controlslist` 只被 Chromium 系完整支持。
 * 去掉原生控件 + 自定义控制条后，这些入口在页面层面就不存在了；
 * 再叠加 `x-webkit-airplay="deny"`、`disableRemotePlayback`、`disablepictureinpicture`
 * 与「禁右键/禁长按/禁拖拽」，做到 H5 端禁用下载与分享。
 *
 * 说明：浏览器/微信原生的「••• → 发送给朋友」属于客户端菜单，网页无法关闭（需公众号 JS-SDK）。
 */

const props = defineProps({
  /** 视频流地址（鉴权流式接口） */
  src: { type: String, required: true },
  /** 封面帧地址（可为空） */
  poster: { type: String, default: '' },
  /** 未播放时的提示文案 */
  hint: { type: String, default: '点击播放视频' }
});
const emit = defineEmits(['play']);

const rootEl = ref(null);
const videoEl = ref(null);
const activated = ref(false);
const playing = ref(false);
const buffering = ref(false);
const error = ref('');
const duration = ref(0);
const current = ref(0);
const fullscreenAvailable = ref(false);

function fmtTime(sec) {
  const s = Number(sec);
  if (!Number.isFinite(s) || s < 0) return '00:00';
  const total = Math.floor(s);
  const m = String(Math.floor(total / 60)).padStart(2, '0');
  const ss = String(total % 60).padStart(2, '0');
  return `${m}:${ss}`;
}

function onMeta() {
  const v = videoEl.value;
  if (v) duration.value = Number.isFinite(v.duration) ? v.duration : 0;
}

function onTime() {
  const v = videoEl.value;
  if (v) current.value = v.currentTime || 0;
}

function onPlaying() {
  playing.value = true;
  buffering.value = false;
}

function onPause() {
  playing.value = false;
  buffering.value = false;
}

function onEnded() {
  playing.value = false;
}

function onError() {
  error.value = '视频加载失败，请检查网络或稍后重试';
  playing.value = false;
  buffering.value = false;
  activated.value = false;
}

function startPlay() {
  const v = videoEl.value;
  if (!v) return;
  // 关键：在用户点击的同一个事件里同步设置 src 并 play()，
  // 既保证 iOS 的「用户手势才允许播放」，也避免等 Vue 渲染后才执行导致首次点击无声。
  if (error.value) {
    error.value = '';
    v.load();
  }
  if (!v.getAttribute('src')) {
    v.setAttribute('src', props.src);
  }
  activated.value = true;
  const p = v.play();
  if (p && typeof p.catch === 'function') {
    p.catch(() => {
      // 自动播放被拦截（静音策略等）：保持封面态，等用户再点一次
      playing.value = false;
    });
  }
}

function togglePlay() {
  const v = videoEl.value;
  if (!v) return;
  if (v.paused) {
    const p = v.play();
    if (p && typeof p.catch === 'function') p.catch(() => {});
  } else {
    v.pause();
  }
}

function onSeek(event) {
  const v = videoEl.value;
  const next = Number(event?.target?.value);
  if (!v || !Number.isFinite(next)) return;
  current.value = next;
  try {
    v.currentTime = next;
  } catch {
    /* 某些浏览器在 metadata 未就绪时赋值会抛错，忽略 */
  }
}

function toggleFullscreen() {
  const el = rootEl.value;
  if (!el) return;
  const request = el.requestFullscreen || el.webkitRequestFullscreen;
  if (!request) return;
  try {
    const r = request.call(el);
    if (r && typeof r.catch === 'function') r.catch(() => {});
  } catch {
    /* 全屏被拒绝（iframe/权限）时静默忽略 */
  }
}

/** 父级用来「同屏只播一个」：暂停别的播放器 */
function pause() {
  const v = videoEl.value;
  if (v && !v.paused) v.pause();
}

/** 停播并回到封面态（例如页面切走后回收） */
function reset() {
  pause();
  activated.value = false;
  playing.value = false;
  buffering.value = false;
  current.value = 0;
}

defineExpose({ pause, reset });

/** 播放开始：通知父级暂停其它视频（同屏只播一个） */
function onPlayEvent() {
  emit('play');
}

onMounted(() => {
  const el = rootEl.value;
  fullscreenAvailable.value = Boolean(el && (el.requestFullscreen || el.webkitRequestFullscreen));
});
</script>

<style scoped>
.mvp {
  position: relative;
  width: 100%;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  -webkit-user-select: none;
  user-select: none;
  -webkit-touch-callout: none;
}
.mvp-video {
  display: block;
  width: 100%;
  max-height: 320px;
  background: #000;
  -webkit-touch-callout: none;
  -webkit-user-select: none;
  user-select: none;
  -webkit-user-drag: none;
}
.mvp-cover {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #fff;
  background: linear-gradient(160deg, #1c2c3b, #0b1218);
  overflow: hidden;
  -webkit-touch-callout: none;
}
.mvp-poster {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  -webkit-user-drag: none;
  pointer-events: none;
}
.mvp-mask { position: absolute; inset: 0; background: rgba(0, 0, 0, .28); pointer-events: none; }
.mvp-bigplay {
  position: relative;
  z-index: 1;
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: rgba(255, 255, 255, .94);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(0, 0, 0, .4);
}
.mvp-hint { position: relative; z-index: 1; font-size: 11px; color: rgba(255, 255, 255, .75); padding: 0 20px; text-align: center; }
.mvp-surface { position: absolute; inset: 0; z-index: 1; display: flex; align-items: center; justify-content: center; }
.mvp-loading { pointer-events: none; }
.mvp-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: linear-gradient(to top, rgba(0, 0, 0, .72), rgba(0, 0, 0, 0));
}
.mvp-btn {
  flex: none;
  width: 26px;
  height: 26px;
  border: 0;
  padding: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, .16);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.mvp-btn:active { background: rgba(255, 255, 255, .3); }
.mvp-time { flex: none; font-size: 11px; color: rgba(255, 255, 255, .85); font-variant-numeric: tabular-nums; }
.mvp-range {
  flex: 1;
  min-width: 0;
  height: 18px;
  margin: 0;
  background: transparent;
  accent-color: #2fceab;
  cursor: pointer;
}
.mvp-range::-webkit-slider-runnable-track { height: 3px; border-radius: 999px; background: rgba(255, 255, 255, .35); }
.mvp-range::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 12px;
  height: 12px;
  margin-top: -4.5px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, .4);
}
</style>
