const MOBILE_KEY = 'mini_novel_login_mobile';
const INVITATION_KEY = 'mini_novel_saved_invitation';
export const INVITATION_QUERY_KEYS = ['invite', 'inviteCode', 'invite_code', 'invitationCode'];
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

export function consumeInvitationQuery(query, storage = localStorage, now = Date.now()) {
  const source = query && typeof query === 'object' ? query : {};
  const cleaned = { ...source };
  let invitation = '';
  let found = false;
  for (const key of INVITATION_QUERY_KEYS) {
    if (Object.prototype.hasOwnProperty.call(source, key)) found = true;
    const raw = Array.isArray(source[key]) ? source[key][0] : source[key];
    if (!invitation) invitation = normalizeInvitation(raw);
    delete cleaned[key];
  }
  if (invitation) saveInvitation(invitation, storage, now);
  return { found, invitation, query: cleaned };
}

export function safeRedirect(value, fallback = '/h5/profile') {
  const target = String(Array.isArray(value) ? value[0] : value || '').trim();
  if (!target.startsWith('/h5/') || target.startsWith('//') || target.includes('\\')) return fallback;
  return target;
}

export function clearSavedInvitation(storage = localStorage) {
  storage.removeItem(INVITATION_KEY);
}

export function clearSubmittedInvitation(value, storage = localStorage) {
  if (normalizeInvitation(value)) clearSavedInvitation(storage);
}

function normalizeInvitation(value) {
  const normalized = String(value || '').trim().toUpperCase();
  return /^[A-Z0-9_-]{4,32}$/.test(normalized) ? normalized : '';
}
