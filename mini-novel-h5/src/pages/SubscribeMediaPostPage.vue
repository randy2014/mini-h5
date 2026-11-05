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

      <!-- 视频：可多个，竖向顺序，点哪个播哪个（同时只播一个）。
           用自定义控件播放器：不渲染原生控件 → 没有下载/分享/AirPlay/画中画入口；
           点击才加载字节，禁右键/禁长按/禁拖拽 -->
      <div v-if="videos.length" class="video-list">
        <div v-for="(v, i) in videos" :key="v.id" class="video-box" @contextmenu.prevent>
          <div v-if="videos.length > 1" class="video-index">视频 {{ i + 1 }} / {{ videos.length }}</div>
          <MediaVideoPlayer
            :ref="(el) => setVideoRef(v.id, el)"
            :src="videoUrl(v)"
            :poster="videoPosterUrl(v)"
            @play="pauseOthers(v.id)"
          />
        </div>
      </div>
    </template>

    <!-- 图片预览（vant 全屏查看器） -->
    <van-image-preview v-model:show="previewVisible" :images="previewImages" :start-position="previewIndex" />
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { classifyApiError, promptFor } from '../permission';
import { fetchFeedPostDetail, mediaFileUrl } from '../services/subscribe';
import { goBack } from '../utils/navigation';
import { detailNavTitle, shouldShowBodyTitle } from '../utils/mediaDetail';
import { readMediaMeta, rememberMediaMeta } from '../utils/mediaMetaCache';
import MediaVideoPlayer from '../components/MediaVideoPlayer.vue';

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

/** 自定义播放器实例：{ [assetId]: 组件实例 }，用于「同屏只播一个」 */
const videoRefs = ref({});

function setVideoRef(id, el) {
  if (el) {
    videoRefs.value[id] = el;
  } else {
    delete videoRefs.value[id];
  }
}

/** 同时只播一个：某个视频开始播放时暂停其余（播放器暴露 pause()） */
function pauseOthers(activeId) {
  Object.entries(videoRefs.value).forEach(([id, player]) => {
    if (String(id) !== String(activeId)) {
      player?.pause?.();
    }
  });
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
.video-box :deep(.mvp) { border-radius: 12px; }

.video-index {
  position: absolute; z-index: 2; top: 8px; left: 8px; padding: 1px 8px; border-radius: 999px;
  background: rgba(0, 0, 0, .55); color: #fff; font-size: 11px; pointer-events: none;
}

.no-save {
  -webkit-user-select: none; user-select: none;
  -webkit-touch-callout: none;
  -webkit-user-drag: none;
}
</style>
