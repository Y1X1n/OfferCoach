import { ref } from 'vue'
import type { ResumeLayout } from '@/types'

const STORAGE_PREFIX = 'oc_resume_layout_v1:'

/** 版式属于本机展示偏好，不写入服务端简历实体。 */
export function loadResumeLayout(resumeId: string): ResumeLayout {
  try {
    const value = localStorage.getItem(`${STORAGE_PREFIX}${resumeId}`)
    if (value === 'classic' || value === 'modern' || value === 'compact' || value === 'minimal') {
      return value
    }
  } catch {
    // 存储不可用时使用默认版式
  }
  return 'classic'
}

export function saveResumeLayout(resumeId: string, layout: ResumeLayout): void {
  try {
    localStorage.setItem(`${STORAGE_PREFIX}${resumeId}`, layout)
  } catch {
    // 存储不可用时只影响下次打开
  }
}

export function useResumeLayout(resumeId: () => string) {
  const layout = ref<ResumeLayout>('classic')

  function sync(): void {
    const id = resumeId()
    layout.value = id ? loadResumeLayout(id) : 'classic'
  }

  function choose(next: ResumeLayout): void {
    const id = resumeId()
    layout.value = next
    if (id) saveResumeLayout(id, next)
  }

  return { layout, sync, choose }
}