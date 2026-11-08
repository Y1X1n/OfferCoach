import { describe, expect, it } from 'vitest'
import { buildReportFileName } from './reportExport'

describe('buildReportFileName', () => {
  it('按 公司-岗位-YYMMDD 生成文件名', () => {
    expect(buildReportFileName('云帆科技', '高级后端工程师', '2026-11-07T10:01:20Z')).toBe(
      '云帆科技-高级后端工程师-261107',
    )
  })

  it('缺少公司/岗位时使用默认值', () => {
    expect(buildReportFileName(null, ' ', null, new Date(2026, 10, 7))).toBe(
      '目标公司-模拟面试-261107',
    )
  })

  it('清理路径分隔符与非法字符', () => {
    expect(buildReportFileName('云帆/科技', '后端:*工程师?', '2026-01-02')).toBe(
      '云帆科技-后端工程师-260102',
    )
  })
})