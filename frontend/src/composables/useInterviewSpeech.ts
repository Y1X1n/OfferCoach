import { ref } from 'vue'
import type { Question } from '@/types'
import * as interviewsApi from '@/api/interviews'
import { errorMessage } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { getAutoReadSpeech, getSpeechVoice } from '@/utils/speechPrefs'
import type { SpeechVoice } from '@/utils/speechPrefs'

export interface InterviewSpeechDeps {
  /** 当前题目；无题时不朗读。 */
  currentQuestion: () => Question | null
  /** 会话标识，用于拼接朗读接口。 */
  sessionKey: () => string
  /** 进入新题时是否允许自动朗读（会话进行中且未作答）。 */
  canAutoRead: () => boolean
}

/**
 * 题目朗读（TTS）：取音频、按题缓存对象 URL、播放与停止，以及进入新题的自动朗读。
 *
 * <p>自动朗读失败（浏览器自动播放策略拦截或上游不可用）静默跳过，不打扰用户。</p>
 */
export function useInterviewSpeech(deps: InterviewSpeechDeps) {
  const { toast } = useToast()

  const speaking = ref(false)
  const speechError = ref('')
  const speechVoice = ref<SpeechVoice>(getSpeechVoice())
  const speechUrls = new Map<string, string>()
  let audioEl: HTMLAudioElement | null = null

  /**
   * 朗读当前题目：首次点击取音频，之后复用对象 URL；再次点击停止。
   */
  async function toggleSpeech(auto = false): Promise<void> {
    const question = deps.currentQuestion()
    if (!question) return
    if (speaking.value) {
      stopSpeech()
      return
    }
    speaking.value = true
    speechError.value = ''
    try {
      let url = speechUrls.get(question.id)
      if (!url) {
        const blob = await interviewsApi.fetchQuestionSpeech(deps.sessionKey(), question.id, speechVoice.value)
        url = URL.createObjectURL(blob)
        speechUrls.set(question.id, url)
      }
      const audio = new Audio(url)
      audioEl = audio
      audio.onended = () => {
        speaking.value = false
      }
      audio.onerror = () => {
        speaking.value = false
        speechError.value = '音频播放失败'
      }
      await audio.play()
    } catch (cause) {
      speaking.value = false
      if (auto) {
        return
      }
      speechError.value = errorMessage(cause, '题目朗读失败')
      toast(speechError.value, 'error')
    }
  }

  /**
   * 偏好开启时，进入新题自动朗读；首题可能被浏览器自动播放策略拦截。
   */
  function maybeAutoRead(): void {
    if (!getAutoReadSpeech()) return
    const question = deps.currentQuestion()
    if (!question || !deps.canAutoRead()) return
    if (question.status === 'SKIPPED') return
    void toggleSpeech(true)
  }

  function stopSpeech(): void {
    if (audioEl) {
      audioEl.pause()
      audioEl.currentTime = 0
      audioEl = null
    }
    speaking.value = false
  }

  /** 释放所有已缓存的对象 URL；离开页面时调用。 */
  function disposeSpeech(): void {
    stopSpeech()
    speechUrls.forEach((url) => URL.revokeObjectURL(url))
    speechUrls.clear()
  }

  return { speaking, speechError, speechVoice, toggleSpeech, stopSpeech, maybeAutoRead, disposeSpeech }
}
