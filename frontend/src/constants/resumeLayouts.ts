import type { ResumeLayout } from '@/types'

export interface ResumeLayoutMeta {
  id: ResumeLayout
  name: string
  tag: string
  description: string
}

/** 简历版式元数据：版式只改变排版，不改变结构化内容。 */
export const resumeLayouts: ResumeLayoutMeta[] = [
  {
    id: 'classic',
    name: '经典单栏',
    tag: '通用 / ATS',
    description:
      '姓名居中、联系方式一行、章节标题带下划线，从上到下按时间线阅读，适合大多数岗位与简历筛选系统。',
  },
  {
    id: 'modern',
    name: '现代双栏',
    tag: '技术 / 产品',
    description:
      '左主栏承载摘要、工作与项目，右侧栏收纳联系方式、技能与教育，信息密度高，适合技术与产品岗位。',
  },
  {
    id: 'compact',
    name: '紧凑商务',
    tag: '3 年以上',
    description: '字号更小、姓名与联系方式同排、章节间距收紧，把 3 年以上的多段经历压缩在一页内。',
  },
  {
    id: 'minimal',
    name: '极简留白',
    tag: '设计 / 应届',
    description:
      '大号姓名、细线章节标签、技能内联展示，留白充足，适合设计类岗位与经历较少的应届生。',
  },
]

export function resumeLayoutName(layout: ResumeLayout): string {
  return resumeLayouts.find((item) => item.id === layout)?.name ?? layout
}