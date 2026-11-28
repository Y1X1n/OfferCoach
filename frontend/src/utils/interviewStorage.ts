/**
 * 面试间写在 localStorage 里的键：答案草稿、计时截止时间、计时剩余秒数。
 *
 * <p>键都是在会话维度上拼出来的（`前缀 + sessionId + '_' + questionId`），清理必须按
 * 同样维度扫前缀：登出只清 `oc_draft_` 会漏掉两类计时键，单场会话收尾只清当前题目又会
 * 把该场其他题目的草稿留在浏览器里。前缀集中在这里，两处清理才不会再各清一半。</p>
 */
export const DRAFT_KEY_PREFIX = 'oc_draft_'
export const TIMER_DEADLINE_KEY_PREFIX = 'oc_timer_'
export const TIMER_REMAINING_KEY_PREFIX = 'oc_timer_rem_'

/** 某场面试的全部键前缀（含 sessionId）。 */
function sessionPrefixes(sessionId: string): string[] {
  return [
    `${DRAFT_KEY_PREFIX}${sessionId}_`,
    `${TIMER_DEADLINE_KEY_PREFIX}${sessionId}_`,
    `${TIMER_REMAINING_KEY_PREFIX}${sessionId}_`,
  ]
}

/**
 * 删除某场面试留下的草稿与计时快照；放弃或结束之后这些数据不会再被读取。
 */
export function removeSessionStorage(sessionId: string, storage = getStorage()): number {
  if (!storage) return 0
  return removeByPrefixes(storage, sessionPrefixes(sessionId))
}

/**
 * 删除所有面试草稿与计时快照，用于登出 / 切换账号。
 */
export function removeAllInterviewStorage(storage = getStorage()): number {
  if (!storage) return 0
  return removeByPrefixes(storage, [
    DRAFT_KEY_PREFIX,
    TIMER_DEADLINE_KEY_PREFIX,
    TIMER_REMAINING_KEY_PREFIX,
  ])
}

function removeByPrefixes(storage: Storage, prefixes: string[]): number {
  let removed = 0
  try {
    // 倒序删除：正序删除会让索引整体前移，漏掉紧随其后的键。
    for (let index = storage.length - 1; index >= 0; index -= 1) {
      const key = storage.key(index)
      if (key !== null && prefixes.some((prefix) => key.startsWith(prefix))) {
        storage.removeItem(key)
        removed += 1
      }
    }
  } catch {
    // 隐私模式等场景下 storage 可能不可用：清理失败不影响主流程。
  }
  return removed
}

function getStorage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}
