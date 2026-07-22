import test from 'node:test'
import assert from 'node:assert/strict'
import{formatDate,formatDateTime,toEpochMillis}from'../src/utils/date.js'
test('Z按北京时间显示',()=>assert.equal(formatDateTime('2026-07-22T04:00:02Z'),'2026-07-22 12:00:02'))
test('offset保持同一瞬间',()=>assert.equal(formatDateTime('2026-07-22T12:00:02+08:00'),'2026-07-22 12:00:02'))
test('LocalDateTime视为北京时间',()=>{assert.equal(formatDateTime('2026-07-22T10:52:02'),'2026-07-22 10:52:02');assert.equal(toEpochMillis('2026-07-22T10:52:02'),Date.parse('2026-07-22T10:52:02+08:00'))})
test('日期与数组',()=>{assert.equal(formatDate('2026-07-22'),'2026-07-22');assert.equal(formatDateTime([2026,7,22,10,52,2]),'2026-07-22 10:52:02')})
test('秒和毫秒时间戳',()=>{assert.equal(formatDateTime(0),'1970-01-01 08:00:00');assert.equal(formatDateTime(1784683202000),formatDateTime(1784683202))})
test('空值和非法值',()=>{for(const v of[null,undefined,'','bad','2026-02-30',[]])assert.equal(formatDateTime(v),'-')})
