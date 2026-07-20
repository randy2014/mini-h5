const MOBILE_KEY = 'mini_novel_login_mobile';
const INVITATION_KEY = 'mini_novel_saved_invitation';
export const INVITATION_TTL_MS = 30 * 24 * 60 * 60 * 1000;

export function readRememberedMobile(storage = localStorage) {
  const value = String(storage.getItem(MOBILE_KEY) || '').trim();
  return /^1\d{10}$/.test(value) ? value : '';
}

export function saveRememberedMobile(value, storage = localStorage) {
  const normalized = String(value || '').trim();
  if (/^1\d{10}$/.test(normalized)) storage.setItem(MOBILE_KEY, normalized);
}

export function readSavedInvitation(storage = localStorage, now = Date.now()) {
  try {
    const saved = JSON.parse(storage.getItem(INVITATION_KEY) || 'null');
    if (!saved?.value || !Number.isFinite(saved.savedAt) || now - saved.savedAt >= INVITATION_TTL_MS) {
      clearSavedInvitation(storage);
      return '';
    }
    return normalizeInvitation(saved.value);
  } catch {
    clearSavedInvitation(storage);
    return '';
  }
}

export function saveInvitation(value, storage = localStorage, now = Date.now()) {
  const normalized = normalizeInvitation(value);
  if (!normalized) {
    clearSavedInvitation(storage);
    return;
  }
  storage.setItem(INVITATION_KEY, JSON.stringify({ value: normalized, savedAt: now }));
}

export function clearSavedInvitation(storage = localStorage) {
  storage.removeItem(INVITATION_KEY);
}

function normalizeInvitation(value) {
  return String(value || '').trim().toUpperCase().slice(0, 32);
}
