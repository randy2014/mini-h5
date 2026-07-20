import test from 'node:test'
import assert from 'node:assert/strict'

import {
  INVITATION_TTL_MS,
  clearSavedInvitation,
  clearSubmittedInvitation,
  consumeInvitationQuery,
  readRememberedMobile,
  readSavedInvitation,
  saveRememberedMobile,
  saveInvitation,
  safeRedirect,
} from './loginPreferences.js'
import { clearAuthenticationStorage } from './authStorage.js'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
    keys: () => [...values.keys()],
  }
}

test('仅记忆手机号，不产生密码存储项', () => {
  const storage = memoryStorage()
  saveRememberedMobile(' 13800000000 ', storage)

  assert.equal(readRememberedMobile(storage), '13800000000')
  assert.equal(storage.keys().some((key) => /password/i.test(key)), false)
})

test('邀请码保存三十天并在到期后自动清除', () => {
  const storage = memoryStorage()
  const savedAt = 1_700_000_000_000
  saveInvitation(' invite-code ', storage, savedAt)

  assert.equal(readSavedInvitation(storage, savedAt + INVITATION_TTL_MS - 1), 'INVITE-CODE')
  assert.equal(readSavedInvitation(storage, savedAt + INVITATION_TTL_MS), '')
  assert.equal(storage.keys().some((key) => /invitation/i.test(key)), false)
})

test('邀请码可在服务端终态响应后显式清除', () => {
  const storage = memoryStorage()
  saveInvitation('PROMO', storage, 123)
  clearSavedInvitation(storage)

  assert.equal(readSavedInvitation(storage, 124), '')
})

test('直接登录链接消费邀请码并保留 redirect', () => {
  const storage = memoryStorage()
  const result = consumeInvitationQuery({ invite: ' promo_2026 ', redirect: '/h5/vip' }, storage, 123)

  assert.equal(result.invitation, 'PROMO_2026')
  assert.deepEqual(result.query, { redirect: '/h5/vip' })
  assert.equal(readSavedInvitation(storage, 124), 'PROMO_2026')
})

test('其他 H5 入口兼容多个参数名并在跳转登录后继续自动填充', () => {
  for (const key of ['inviteCode', 'invite_code', 'invitationCode']) {
    const storage = memoryStorage()
    const result = consumeInvitationQuery({ [key]: 'VIP-CODE', page: '2' }, storage, 123)
    assert.equal(result.found, true)
    assert.deepEqual(result.query, { page: '2' })
    assert.equal(readSavedInvitation(storage, 124), 'VIP-CODE')
  }
})

test('非法邀请码同样从地址栏消费但不保存', () => {
  const storage = memoryStorage()
  const result = consumeInvitationQuery({ invite: 'https://bad.example/' }, storage, 123)

  assert.equal(result.found, true)
  assert.equal(result.invitation, '')
  assert.deepEqual(result.query, {})
  assert.equal(readSavedInvitation(storage, 124), '')
})

test('成功及无效邀请码响应清除，网络失败路径保留', () => {
  const successStorage = memoryStorage()
  saveInvitation('VALID-CODE', successStorage, 123)
  clearSubmittedInvitation('VALID-CODE', successStorage)
  assert.equal(readSavedInvitation(successStorage, 124), '')

  const invalidStorage = memoryStorage()
  saveInvitation('INVALID-CODE', invalidStorage, 123)
  clearSubmittedInvitation('INVALID-CODE', invalidStorage)
  assert.equal(readSavedInvitation(invalidStorage, 124), '')

  const networkStorage = memoryStorage()
  saveInvitation('RETRY-CODE', networkStorage, 123)
  assert.equal(readSavedInvitation(networkStorage, 124), 'RETRY-CODE')
})

test('退出仅清认证数据，保留手机号和未核销邀请码', () => {
  const storage = memoryStorage()
  storage.setItem('mini_novel_user_id', '7')
  storage.setItem('mini_novel_auth_token', 'redacted')
  storage.setItem('mini_novel_auth_token_name', 'Authorization')
  saveRememberedMobile('13800000000', storage)
  saveInvitation('PENDING-CODE', storage, 123)

  clearAuthenticationStorage(storage)

  assert.equal(storage.getItem('mini_novel_auth_token'), null)
  assert.equal(readRememberedMobile(storage), '13800000000')
  assert.equal(readSavedInvitation(storage, 124), 'PENDING-CODE')
})

test('redirect 只允许站内 H5 路径', () => {
  assert.equal(safeRedirect('/h5/vip?tab=1'), '/h5/vip?tab=1')
  assert.equal(safeRedirect('https://evil.example/'), '/h5/profile')
  assert.equal(safeRedirect('//evil.example/'), '/h5/profile')
  assert.equal(safeRedirect('/h5/\\evil'), '/h5/profile')
})
