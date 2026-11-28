import { beforeEach, describe, expect, it } from 'vitest'
import {
  DRAFT_KEY_PREFIX,
  TIMER_DEADLINE_KEY_PREFIX,
  TIMER_REMAINING_KEY_PREFIX,
  removeAllInterviewStorage,
  removeSessionStorage,
} from './interviewStorage'

function seed(): void {
  localStorage.clear()
  localStorage.setItem(`${DRAFT_KEY_PREFIX}100_1`, '第一题草稿')
  localStorage.setItem(`${TIMER_DEADLINE_KEY_PREFIX}100_1`, '1700000000000')
  localStorage.setItem(`${TIMER_REMAINING_KEY_PREFIX}100_1`, '120')
  localStorage.setItem(`${DRAFT_KEY_PREFIX}200_1`, '另一场面试的草稿')
  localStorage.setItem(`${TIMER_DEADLINE_KEY_PREFIX}200_1`, '1700000000001')
  localStorage.setItem('oc_token_hint', 'keep-me')
  localStorage.setItem('unrelated', 'keep-me')
}

beforeEach(seed)

describe('removeSessionStorage', () => {
  it('只清掉指定会话的三类键，其他会话与无关键保留', () => {
    expect(removeSessionStorage('100')).toBe(3)

    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}100_1`)).toBeNull()
    expect(localStorage.getItem(`${TIMER_DEADLINE_KEY_PREFIX}100_1`)).toBeNull()
    expect(localStorage.getItem(`${TIMER_REMAINING_KEY_PREFIX}100_1`)).toBeNull()
    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}200_1`)).toBe('另一场面试的草稿')
    expect(localStorage.getItem(`${TIMER_DEADLINE_KEY_PREFIX}200_1`)).toBe('1700000000001')
    expect(localStorage.getItem('unrelated')).toBe('keep-me')
  })

  /**
   * `oc_timer_rem_100_1` 以 `oc_timer_` 开头：按会话前缀清理时必须靠 sessionId 区分，
   * 否则「清会话 100」会连 `oc_timer_rem_100_*` 一起漏掉或误伤别的会话。
   */
  it('剩余秒数键不会被会话 10 的清理误伤', () => {
    seed()
    localStorage.setItem(`${DRAFT_KEY_PREFIX}10_1`, '会话 10')

    removeSessionStorage('10')

    expect(localStorage.getItem(`${DRAFT_KEY_PREFIX}10_1`)).toBeNull()
    expect(localStorage.getItem(`${TIMER_REMAINING_KEY_PREFIX}100_1`)).toBe('120')
  })
})

describe('removeAllInterviewStorage', () => {
  it('清掉所有草稿与计时键，保留无关键', () => {
    expect(removeAllInterviewStorage()).toBe(5)

    const keys = Array.from({ length: localStorage.length }, (_, index) => localStorage.key(index))
    expect(keys.sort()).toEqual(['oc_token_hint', 'unrelated'])
  })
})
