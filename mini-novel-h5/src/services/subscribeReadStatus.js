const STORAGE_KEY = 'mini_novel_subscribe_read';

function readState(storage = localStorage) {
  try {
    const parsed = JSON.parse(storage.getItem(STORAGE_KEY) || '{}');
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function writeState(state, storage = localStorage) {
  storage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function normalizeNovelId(value) {
  const id = Number(value || 0);
  return Number.isFinite(id) && id > 0 ? String(id) : '';
}

export function subscribeReadIds(storage = localStorage) {
  return new Set(Object.keys(readState(storage)));
}

export function isSubscribeRead(novelId, storage = localStorage) {
  const key = normalizeNovelId(novelId);
  return Boolean(key && readState(storage)[key]);
}

export function markSubscribeRead(novelId, meta = {}, storage = localStorage) {
  const key = normalizeNovelId(novelId);
  if (!key) return false;
  const state = readState(storage);
  state[key] = {
    novelId: Number(key),
    chapterId: Number(meta.chapterId || 0) || undefined,
    chapterNo: Number(meta.chapterNo || 0) || undefined,
    title: meta.title || '',
    author: meta.author || '',
    updatedAt: Date.now()
  };
  writeState(state, storage);
  return true;
}

export function subscribeReadList(storage = localStorage) {
  return Object.values(readState(storage)).sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
}

export function clearSubscribeRead(storage = localStorage) {
  storage.removeItem(STORAGE_KEY);
}
