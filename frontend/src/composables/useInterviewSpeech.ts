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

/** 对象 URL 缓存上限：整套题 10 题左右，留出换音色后新旧并存的余量。 */
const MAX_CACHED_URLS = 20

/**
 * 题目朗读（TTS）：取音频、按题缓存对象 URL、播放与停止，以及进入新题的自动朗读。
 *
 * 自动朗读失败（浏览器自动播放策略拦截或上游不可用）静默跳过，不打扰用户。
 */
export function useInterviewSpeech(deps: InterviewSpeechDeps) {
  const { toast } = useToast()

  const speaking = ref(false)
  const speechError = ref('')
  const speechVoice = ref<SpeechVoice>(getSpeechVoice())
  const speechUrls = new Map<string, string>()
  let audioEl: HTMLAudioElement | null = null
  /**
   * 播放代次：每次开始/停止朗读自增。取音频是异步的，若期间用户已切题或再次点击，
   * 旧请求返回后必须放弃播放，否则会出现两段音频同时朗读、或停止后又响起来。
   */
  let playbackGeneration = 0

  /** 缓存键带音色：切换音色后不能复用旧音频。 */
  function cacheKey(questionId: string, voice: SpeechVoice): string {
    return `${voice}:${questionId}`
  }

  function rememberUrl(key: string, url: string): void {
    speechUrls.set(key, url)
    // 超上限时淘汰最早写入的一条，避免长会话无限占用内存。
    while (speechUrls.size > MAX_CACHED_URLS) {
      const oldestKey = speechUrls.keys().next().value
      if (oldestKey === undefined) break
      const oldestUrl = speechUrls.get(oldestKey)
      speechUrls.delete(oldestKey)
      if (oldestUrl) URL.revokeObjectURL(oldestUrl)
    }
  }

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
    const generation = ++playbackGeneration
    const voice = speechVoice.value
    speaking.value = true
    speechError.value = ''
    try {
      const key = cacheKey(question.id, voice)
      let url = speechUrls.get(key)
      if (!url) {
        const blob = await interviewsApi.fetchQuestionSpeech(deps.sessionKey(), question.id, voice)
        // 请求返回时用户可能已停止或切题：此时不再播放，也不缓存这份结果。
        if (generation !== playbackGeneration) return
        url = URL.createObjectURL(blob)
        rememberUrl(key, url)
      }
      if (generation !== playbackGeneration) return
      const audio = new Audio(url)
      audioEl = audio
      audio.onended = () => {
        if (audioEl === audio) {
          audioEl = null
          speaking.value = false
        }
      }
      audio.onerror = () => {
        if (audioEl === audio) {
          audioEl = null
          speaking.value = false
          speechError.value = '音频播放失败'
        }
      }
      await audio.play()
    } catch (cause) {
      if (generation !== playbackGeneration) return
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
    // 自增代次，作废仍在等待中的取音频请求。
    playbackGeneration++
    if (audioEl) {
      audioEl.pause()
      audioEl.currentTime = 0
      audioEl.onended = null
      audioEl.onerror = null
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
