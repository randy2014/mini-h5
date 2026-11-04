<template>
  <section class="page media-post-page">
    <van-nav-bar :title="navTitle" left-arrow @click-left="goBackToChannel" />

    <div v-if="loading" class="center-loading"><van-loading /></div>
    <div v-else-if="error" class="error-box">
      <div class="lg">🔒</div>
      <p>{{ error }}</p>
      <div class="error-actions">
        <van-button v-if="gateAction" round color="#1f6f64" :to="gateAction.to">
          {{ gateAction.label }}
        </van-button>
        <van-button round plain @click="goBackToChannel">返回</van-button>
      </div>
    </div>

    <template v-else-if="detail">
      <!-- 标题（视频类标题已放到导航栏，正文不再重复） -->
      <div v-if="showBodyTitle" class="post-title">{{ detail.title }}</div>

      <!-- 图文（禁用长按/右键/拖拽保存） -->
      <div v-if="images.length" class="gallery" @contextmenu.prevent>
        <div v-for="(img, i) in images" :key="img.id" class="gi"
             :class="{ big: i === 0 }" @click="previewIndex = i">
          <img :src="imgUrl(img)" :alt="detail.title" draggable="false" class="no-save" />
        </div>
      </div>

      <!-- 视频：可多个，竖向顺序，点哪个播哪个（同时只播一个）；
           播放时才加载字节，禁下载/禁右键/禁画中画 -->
      <div v-if="videos.length" class="video-list">
        <div v-for="(v, i) in videos" :key="v.id" class="video-box" @contextmenu.prevent>
          <div v-if="videos.length > 1" class="video-index">视频 {{ i + 1 }} / {{ videos.length }}</div>
          <video
            v-if="stateOf(v.id).ready"
            :ref="(el) => setVideoEl(v.id, el)"
            :src="videoUrl(v)"
            class="player no-save"
            controls
            playsinline
            webkit-playsinline
            x5-playsinline
            preload="metadata"
            controlslist="nodownload noremoteplayback"
            disablepictureinpicture
            :poster="videoPosterUrl(v)"
            @play="pauseOthers(v.id)"
            @error="onVideoError(v)"
          />
          <div v-else class="video-placeholder" @click="playVideo(v)">
            <img v-if="v.posterPath" :src="videoPosterUrl(v)" class="poster no-save" draggable="false" alt="" />
            <div class="poster-mask"></div>
            <div class="bigplay">
              <van-icon name="play" size="22" color="#1f6f64" />
            </div>
            <div class="vhint">{{ stateOf(v.id).error || '点击播放视频' }}</div>
          </div>
        </div>
      </div>
    </template>

    <!-- 图片预览（vant 全屏查看器） -->
    <van-image-preview v-model:show="previewVisible" :images="previewImages" :start-position="previewIndex" />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { showToast } from 'vant';
import { classifyApiError, promptFor } from '../permission';
import { fetchFeedPostDetail, mediaFileUrl } from '../services/subscribe';
import { goBack } from '../utils/navigation';
import { detailNavTitle, shouldShowBodyTitle } from '../utils/mediaDetail';
import { readMediaMeta, rememberMediaMeta } from '../utils/mediaMetaCache';

const route = useRoute();
const router = useRouter();
const channelId = Number(route.params.channelId);
const postId = Number(route.params.postId);

/** 返回上一层；直接打开媒体详情时兜底回所属频道。 */
function goBackToChannel() {
  goBack(router, `/h5/subscribe/${channelId}`);
}

const loading = ref(true);
const error = ref('');
const gatePrompt = ref(null);
const detail = ref(null);
const previewVisible = ref(false);
const previewIndex = ref(0);

const gateAction = computed(() => gatePrompt.value?.action || null);

/** 视频类（VIDEO/MIXED）导航标题动态换成内容标题；纯图文仍显示「图文详情」。
 *  详情接口返回前，先用列表页缓存的标题（避免先闪「图文详情」）。 */
const cachedMeta = readMediaMeta(postId);
const navTitle = computed(() => {
  if (detail.value) return detailNavTitle(detail.value);
  if (cachedMeta) return detailNavTitle({ type: cachedMeta.type, title: cachedMeta.title });
  return '图文详情';
});
const showBodyTitle = computed(() => shouldShowBodyTitle(detail.value));

const images = computed(() => (detail.value?.assets || []).filter((a) => a.fileType === 1));
/** 视频按 seq 顺序返回（第 1 个即封面），一帖可挂多个 */
const videos = computed(() => (detail.value?.assets || []).filter((a) => a.fileType === 2));
const previewImages = computed(() => images.value.map((a) => imgUrl(a)));

/** 每个视频的播放态：{ [assetId]: { ready, error } }；点开才挂 src 播放，避免多视频同时拉流 */
const videoState = reactive({});
const videoEls = ref({});

function stateOf(id) {
  return videoState[id] || { ready: false, error: '' };
}

function setVideoEl(id, el) {
  if (el) {
    videoEls.value[id] = el;
  } else {
    delete videoEls.value[id];
  }
}

function playVideo(asset) {
  videoState[asset.id] = { ready: true, error: '' };
}

/** 同时只播一个：某个视频开始播放时暂停其余 */
function pauseOthers(activeId) {
  Object.entries(videoEls.value).forEach(([id, el]) => {
    if (String(id) !== String(activeId) && el && !el.paused) {
      el.pause();
    }
  });
}

function onVideoError(asset) {
  videoState[asset.id] = { ready: false, error: '视频加载失败，请检查网络或稍后重试' };
  showToast(videoState[asset.id].error);
}

function imgUrl(asset) {
  return mediaFileUrl(channelId, asset.id, 'full');
}

function videoUrl(asset) {
  return mediaFileUrl(channelId, asset.id, 'full');
}

function videoPosterUrl(asset) {
  return asset.posterPath ? mediaFileUrl(channelId, asset.id, 'poster') : '';
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    // silent：本页用自己的错误区块表达门禁原因，避免再叠一层 toast
    detail.value = await fetchFeedPostDetail(channelId, postId, { silent: true });
    // 详情接口回来后刷新标题缓存，下次进入可直接用缓存标题
    rememberMediaMeta(postId, { title: detail.value?.title, type: detail.value?.type });
    // 初始化每个视频的播放态（渲染期不写响应式数据）
    videos.value.forEach((v) => {
      videoState[v.id] = videoState[v.id] || { ready: false, error: '' };
    });
  } catch (e) {
    gatePrompt.value = promptFor(classifyApiError(e, { scope: 'CHANNEL' }).gate, { message: e.message });
    error.value = gatePrompt.value.text;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.media-post-page { background: #f2f3f7; min-height: 100vh; padding-bottom: 30px; }
.center-loading { display: flex; justify-content: center; padding: 60px 0; }
.error-box { text-align: center; padding: 60px 30px; color: #8a92a3; }
.error-box .lg { font-size: 44px; }
.error-box p { margin: 12px 0 18px; font-size: 13px; line-height: 1.7; }
.error-actions { display: flex; justify-content: center; gap: 10px; }
.post-title { padding: 14px 14px 2px; font-size: 17px; font-weight: 700; color: #1d2a39; line-height: 1.45; }
.gallery { display: grid; grid-template-columns: repeat(3, 1fr); gap: 3px; margin: 10px 12px 0; background: #fff; border-radius: 12px; overflow: hidden; }
.gallery .gi { position: relative; overflow: hidden; aspect-ratio: 1; }
.gallery .gi.big { grid-column: span 2; grid-row: span 2; aspect-ratio: auto; height: 100%; }
.gallery img { width: 100%; height: 100%; object-fit: cover; display: block; }
.video-list { display: flex; flex-direction: column; gap: 10px; margin: 10px 12px 0; }
.video-box { position: relative; border-radius: 12px; overflow: hidden; background: #000; }
.video-box .player { width: 100%; max-height: 320px; background: #000; display: block; }
.video-index {
  position: absolute; z-index: 2; top: 8px; left: 8px; padding: 1px 8px; border-radius: 999px;
  background: rgba(0, 0, 0, .55); color: #fff; font-size: 11px; pointer-events: none;
}
.video-placeholder {
  position: relative; height: 190px; background: linear-gradient(160deg, #1c2c3b, #0b1218);
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px;
  color: #fff; overflow: hidden;
}
.video-placeholder .poster { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.video-placeholder .poster-mask { position: absolute; inset: 0; background: rgba(0, 0, 0, .28); }
.video-placeholder .bigplay { position: relative; z-index: 1; }
.video-placeholder .vhint { position: relative; z-index: 1; }
.bigplay { width: 54px; height: 54px; border-radius: 50%; background: rgba(255,255,255,.94); display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 14px rgba(0,0,0,.4); }
.vhint { font-size: 11px; color: rgba(255,255,255,.75); }
.no-save {
  -webkit-user-select: none; user-select: none;
  -webkit-touch-callout: none;
  -webkit-user-drag: none;
}
</style>
