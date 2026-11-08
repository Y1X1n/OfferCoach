import type { Component } from 'vue'
import {
  BarChart3,
  CreditCard,
  FileText,
  LayoutDashboard,
  Mic,
  Settings,
  ShieldCheck,
} from 'lucide-vue-next'

export interface NavItem {
  label: string
  to: string
  icon: Component
  exact?: boolean
}

export const navItems: NavItem[] = [
  { label: '工作台', to: '/', icon: LayoutDashboard, exact: true },
  { label: '简历', to: '/resumes', icon: FileText },
  { label: '模拟面试', to: '/interview/new', icon: Mic },
  { label: '面试报告', to: '/reports', icon: BarChart3 },
  { label: '套餐', to: '/plans', icon: CreditCard },
  { label: '设置', to: '/settings', icon: Settings },
]

/** 仅 ADMIN 可见；桌面侧栏、移动抽屉与底部导航共用。 */
export const adminNavItem: NavItem = { label: '管理', to: '/admin', icon: ShieldCheck }

export const bottomNavItems = navItems.slice(0, 5)
