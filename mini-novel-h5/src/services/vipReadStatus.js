const STORAGE_KEY = 'mini_novel_vip_read_books';

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

export function vipReadIds(storage = localStorage) {
  return new Set(Object.keys(readState(storage)));
}

export function isVipBookRead(novelId, storage = localStorage) {
  const key = normalizeNovelId(novelId);
  return Boolean(key && readState(storage)[key]);
}

export function markVipBookRead(novelId, meta = {}, storage = localStorage) {
  const key = normalizeNovelId(novelId);
  if (!key) return false;
  const state = readState(storage);
  state[key] = {
    novelId: Number(key),
    chapterId: Number(meta.chapterId || 0) || undefined,
    chapterNo: Number(meta.chapterNo || 0) || undefined,
    title: meta.title || '',
    updatedAt: Date.now()
  };
  writeState(state, storage);
  return true;
}

export function clearVipReadBooks(storage = localStorage) {
  storage.removeItem(STORAGE_KEY);
}

export const VIP_READ_STORAGE_KEY = STORAGE_KEY;
