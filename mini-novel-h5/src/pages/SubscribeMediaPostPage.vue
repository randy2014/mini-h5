<template>
  <section class="page media-post-page">
    <van-nav-bar :title="typeLabel" left-arrow @click-left="$router.back()" />

    <div v-if="loading" class="center-loading"><van-loading /></div>
    <div v-else-if="error" class="error-box">
      <div class="lg">🔒</div>
      <p>{{ error }}</p>
      <van-button round color="#1f6f64" @click="$router.back()">返回</van-button>
    </div>

    <template v-else-if="detail">
      <!-- 标题 -->
      <div class="post-title">{{ detail.title }}</div>

      <!-- 图文（禁用长按/右键/拖拽保存） -->
      <div v-if="images.length" class="gallery" @contextmenu.prevent>
        <div v-for="(img, i) in images" :key="img.id" class="gi"
             :class="{ big: i === 0 }" @click="previewIndex = i">
          <img :src="imgUrl(img)" :alt="detail.title" draggable="false" class="no-save" />
        </div>
      </div>

      <!-- 视频（禁下载/禁右键/禁画中画） -->
      <div v-if="video" class="video-box" @contextmenu.prevent>
        <video
          v-if="videoReady"
          :src="videoUrl(video)"
          class="player no-save"
          controls
          playsinline
          webkit-playsinline
          x5-playsinline
          preload="metadata"
          controlslist="nodownload noremoteplayback"
          disablepictureinpicture
          :poster="videoPosterUrl(video)"
          @error="onVideoError"
        />
        <div v-else class="video-placeholder" @click="playVideo">
          <div class="bigplay">
            <van-icon name="play" size="22" color="#1f6f64" />
          </div>
          <div class="vhint">{{ videoError || '点击播放视频' }}</div>
        </div>
      </div>
    </template>

    <!-- 图片预览（vant 全屏查看器） -->
    <van-image-preview v-model:show="previewVisible" :images="previewImages" :start-position="previewIndex" />
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { showToast } from 'vant';
import { fetchFeedPostDetail, mediaFileUrl } from '../services/subscribe';

const route = useRoute();
const channelId = Number(route.params.channelId);
const postId = Number(route.params.postId);

const loading = ref(true);
const error = ref('');
const detail = ref(null);
const previewVisible = ref(false);
const previewIndex = ref(0);

const videoReady = ref(false);
const videoError = ref('');

const typeLabel = computed(() => {
  if (!detail.value) return '图文/视频';
  return detail.value.type === 'VIDEO' ? '视频详情'
    : detail.value.type === 'MIXED' ? '图文+视频'
    : '图文详情';
});

const images = computed(() => (detail.value?.assets || []).filter((a) => a.fileType === 'IMAGE'));
const video = computed(() => (detail.value?.assets || []).find((a) => a.fileType === 'VIDEO'));
const previewImages = computed(() => images.value.map((a) => imgUrl(a)));

function imgUrl(asset) {
  return mediaFileUrl(channelId, asset.id, 'full');
}

function videoUrl(asset) {
  return mediaFileUrl(channelId, asset.id, 'full');
}

function videoPosterUrl(asset) {
  return asset.posterPath ? mediaFileUrl(channelId, asset.id, 'poster') : '';
}

function playVideo() {
  videoReady.value = true;
}

function onVideoError() {
  videoReady.value = false;
  videoError.value = '视频加载失败，请检查网络或稍后重试';
  showToast(videoError.value);
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    detail.value = await fetchFeedPostDetail(channelId, postId);
  } catch (e) {
    error.value = e.message || '内容不存在或未订阅';
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
.error-box p { margin: 12px 0 18px; font-size: 13px; }
.post-title { padding: 14px 14px 2px; font-size: 17px; font-weight: 700; color: #1d2a39; line-height: 1.45; }
.gallery { display: grid; grid-template-columns: repeat(3, 1fr); gap: 3px; margin: 10px 12px 0; background: #fff; border-radius: 12px; overflow: hidden; }
.gallery .gi { position: relative; overflow: hidden; aspect-ratio: 1; }
.gallery .gi.big { grid-column: span 2; grid-row: span 2; aspect-ratio: auto; height: 100%; }
.gallery img { width: 100%; height: 100%; object-fit: cover; display: block; }
.video-box { margin: 10px 12px 0; border-radius: 12px; overflow: hidden; background: #000; }
.video-box .player { width: 100%; max-height: 320px; background: #000; display: block; }
.video-placeholder { height: 190px; background: linear-gradient(160deg, #1c2c3b, #0b1218); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; color: #fff; }
.bigplay { width: 54px; height: 54px; border-radius: 50%; background: rgba(255,255,255,.94); display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 14px rgba(0,0,0,.4); }
.vhint { font-size: 11px; color: rgba(255,255,255,.75); }
.no-save {
  -webkit-user-select: none; user-select: none;
  -webkit-touch-callout: none;
  -webkit-user-drag: none;
}
</style>
