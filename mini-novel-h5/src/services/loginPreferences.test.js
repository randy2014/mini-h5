import test from 'node:test'
import assert from 'node:assert/strict'

import {
  INVITATION_TTL_MS,
  clearSavedInvitation,
  readRememberedMobile,
  readSavedInvitation,
  saveRememberedMobile,
  saveInvitation,
} from './loginPreferences.js'

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
