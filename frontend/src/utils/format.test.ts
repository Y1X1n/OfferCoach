import { describe, expect, it } from 'vitest'
import { clamp, formatCents, formatScore, mean, round1, scoreTone } from './format'

describe('formatCents', () => {
  it('以整数元展示时省略小数', () => {
    expect(formatCents(19900)).toBe('¥199')
  })

  it('保留非整数元的小数', () => {
    expect(formatCents(19950)).toBe('¥199.50')
  })
})

describe('scoreTone', () => {
  it('按分数段返回语义色', () => {
    expect(scoreTone(85)).toBe('success')
    expect(scoreTone(70)).toBe('accent')
    expect(scoreTone(60)).toBe('warning')
    expect(scoreTone(59.9)).toBe('danger')
  })
})

describe('数值工具', () => {
  it('clamp 限制在闭区间内', () => {
    expect(clamp(-1, 0, 10)).toBe(0)
    expect(clamp(11, 0, 10)).toBe(10)
  })

  it('round1 保留一位小数', () => {
    expect(round1(7.26)).toBe(7.3)
  })

  it('mean 对空数组返回 0', () => {
    expect(mean([])).toBe(0)
  })

  it('formatScore 区分整数与小数', () => {
    expect(formatScore(88)).toBe('88')
    expect(formatScore(88.25)).toBe('88.3')
  })
})
