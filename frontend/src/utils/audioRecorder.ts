/**
 * 录音工具：用 AudioWorklet 采集原始 PCM，封装为 16kHz / 单声道 / 16-bit WAV。
 *
 * <p>浏览器 MediaRecorder 默认输出 WebM 封装 Opus，DeepSeek ASR 不接受；
 * 因此必须自行采样并封装 WAV，避免服务端转码。</p>
 */

const TARGET_SAMPLE_RATE = 16000
const DEFAULT_MAX_SECONDS = 120

export interface RecordedAudio {
  blob: Blob
  durationMs: number
}

export interface RecorderHandle {
  stop: () => Promise<RecordedAudio>
  cancel: () => void
}

export function isRecordingSupported(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    !!navigator.mediaDevices?.getUserMedia &&
    (typeof AudioWorkletNode !== 'undefined' || typeof ScriptProcessorNode !== 'undefined')
  )
}

/**
 * 开始录音；返回句柄，停止后得到 WAV。
 */
export async function startRecording(
  onTick?: (elapsedMs: number) => void,
  maxSeconds = DEFAULT_MAX_SECONDS,
): Promise<RecorderHandle> {
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: {
      channelCount: 1,
      echoCancellation: true,
      noiseSuppression: true,
    },
  })
  const context = new AudioContext()
  const source = context.createMediaStreamSource(stream)
  const chunks: Float32Array[] = []
  const startedAt = performance.now()
  let stopped = false

  const collect = (input: Float32Array) => {
    chunks.push(new Float32Array(input))
  }

  const sink = context.createGain()
  sink.gain.value = 0

  let workletNode: AudioWorkletNode | null = null
  let processorNode: ScriptProcessorNode | null = null

  if (typeof AudioWorkletNode !== 'undefined' && context.audioWorklet) {
    const processorSource = `
      class OcRecorder extends AudioWorkletProcessor {
        process(inputs) {
          const channel = inputs[0] && inputs[0][0]
          if (channel) { this.port.postMessage(new Float32Array(channel)) }
          return true
        }
      }
      registerProcessor('oc-recorder', OcRecorder)
    `
    const url = URL.createObjectURL(new Blob([processorSource], { type: 'application/javascript' }))
    try {
      await context.audioWorklet.addModule(url)
      workletNode = new AudioWorkletNode(context, 'oc-recorder')
      workletNode.port.onmessage = (event: MessageEvent<Float32Array>) => collect(event.data)
      source.connect(workletNode)
      workletNode.connect(sink)
    } finally {
      URL.revokeObjectURL(url)
    }
  } else {
    processorNode = context.createScriptProcessor(4096, 1, 1)
    processorNode.onaudioprocess = (event) => collect(event.inputBuffer.getChannelData(0))
    source.connect(processorNode)
    processorNode.connect(sink)
  }
  sink.connect(context.destination)

  const tickId = window.setInterval(() => {
    const elapsed = performance.now() - startedAt
    onTick?.(elapsed)
    if (elapsed >= maxSeconds * 1000) {
      void cleanup()
    }
  }, 200)

  const cleanup = async () => {
    if (stopped) return
    stopped = true
    window.clearInterval(tickId)
    workletNode?.port.close()
    if (processorNode) processorNode.onaudioprocess = null
    stream.getTracks().forEach((track) => track.stop())
    await context.close().catch(() => undefined)
  }

  return {
    stop: async () => {
      const durationMs = Math.min(maxSeconds * 1000, Math.round(performance.now() - startedAt))
      await cleanup()
      const samples = mergeChunks(chunks)
      const resampled = resample(samples, context.sampleRate, TARGET_SAMPLE_RATE)
      const wav = encodeWav(resampled, TARGET_SAMPLE_RATE)
      return { blob: wav, durationMs }
    },
    cancel: () => {
      void cleanup()
    },
  }
}

function mergeChunks(chunks: Float32Array[]): Float32Array {
  const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0)
  const merged = new Float32Array(total)
  let offset = 0
  for (const chunk of chunks) {
    merged.set(chunk, offset)
    offset += chunk.length
  }
  return merged
}

/**
 * 线性插值重采样；录音场景足够，避免引入额外依赖。
 */
function resample(input: Float32Array, fromRate: number, toRate: number): Float32Array {
  if (fromRate === toRate) return input
  const ratio = fromRate / toRate
  const length = Math.floor(input.length / ratio)
  const output = new Float32Array(length)
  for (let i = 0; i < length; i += 1) {
    const position = i * ratio
    const lower = Math.floor(position)
    const upper = Math.min(lower + 1, input.length - 1)
    const weight = position - lower
    output[i] = input[lower]! * (1 - weight) + input[upper]! * weight
  }
  return output
}

/**
 * 封装 16-bit 单声道 WAV。
 */
function encodeWav(samples: Float32Array, sampleRate: number): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  writeAscii(view, 0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeAscii(view, 8, 'WAVE')
  writeAscii(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeAscii(view, 36, 'data')
  view.setUint32(40, samples.length * 2, true)
  let offset = 44
  for (let i = 0; i < samples.length; i += 1) {
    const clamped = Math.max(-1, Math.min(1, samples[i]!))
    view.setInt16(offset, clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff, true)
    offset += 2
  }
  return new Blob([buffer], { type: 'audio/wav' })
}

function writeAscii(view: DataView, offset: number, text: string): void {
  for (let i = 0; i < text.length; i += 1) {
    view.setUint8(offset + i, text.charCodeAt(i))
  }
}
