/**
 * 题目朗读的本地偏好：自动朗读开关与音色，仅保存在本机。
 */
export type SpeechVoice = 'stella' | 'tide'

const AUTO_KEY = 'oc_tts_auto_v1'
const VOICE_KEY = 'oc_tts_voice_v1'

export function getAutoReadSpeech(): boolean {
  try {
    return window.localStorage.getItem(AUTO_KEY) === '1'
  } catch {
    return false
  }
}

export function setAutoReadSpeech(enabled: boolean): void {
  try {
    window.localStorage.setItem(AUTO_KEY, enabled ? '1' : '0')
  } catch {
    // 存储不可用时忽略：偏好降级为本次会话默认值
  }
}

export function getSpeechVoice(): SpeechVoice {
  try {
    return window.localStorage.getItem(VOICE_KEY) === 'tide' ? 'tide' : 'stella'
  } catch {
    return 'stella'
  }
}

export function setSpeechVoice(voice: SpeechVoice): void {
  try {
    window.localStorage.setItem(VOICE_KEY, voice)
  } catch {
    // 同上
  }
}