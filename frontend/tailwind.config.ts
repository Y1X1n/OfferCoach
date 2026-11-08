import type { Config } from 'tailwindcss'

export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      screens: {
        room: '1400px',
      },
      colors: {
        bg: 'hsl(var(--bg) / <alpha-value>)',
        surface: {
          DEFAULT: 'hsl(var(--surface) / <alpha-value>)',
          2: 'hsl(var(--surface-2) / <alpha-value>)',
        },
        border: 'hsl(var(--border) / <alpha-value>)',
        text: {
          DEFAULT: 'hsl(var(--text) / <alpha-value>)',
          muted: 'hsl(var(--muted) / <alpha-value>)',
        },
        brand: {
          DEFAULT: 'hsl(var(--brand) / <alpha-value>)',
          foreground: 'hsl(var(--brand-fg) / <alpha-value>)',
        },
        accent: 'hsl(var(--accent) / <alpha-value>)',
        success: 'hsl(var(--success) / <alpha-value>)',
        warning: 'hsl(var(--warning) / <alpha-value>)',
        danger: 'hsl(var(--danger) / <alpha-value>)',
        info: 'hsl(var(--info) / <alpha-value>)',
        overlay: 'hsl(var(--overlay) / <alpha-value>)',
        'success-strong': 'hsl(var(--success-strong) / <alpha-value>)',
        'warning-strong': 'hsl(var(--warning-strong) / <alpha-value>)',
        'accent-strong': 'hsl(var(--accent-strong) / <alpha-value>)',
        'info-strong': 'hsl(var(--info-strong) / <alpha-value>)',
        'danger-strong': 'hsl(var(--danger-strong) / <alpha-value>)',
        'danger-solid': 'hsl(var(--danger-solid) / <alpha-value>)',
      },
      fontFamily: {
        sans: [
          'Inter',
          'Noto Sans SC',
          'PingFang SC',
          'Microsoft YaHei',
          'system-ui',
          'sans-serif',
        ],
        mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      fontSize: {
        '2xs': ['11px', '16px'],
        '3xs': ['10px', '14px'],
      },
      letterSpacing: {
        label: '0.14em',
        badge: '0.16em',
        caption: '0.18em',
        hero: '0.3em',
      },
      scale: {
        '98': '.98',
      },
      borderRadius: {
        sm: '4px',
        md: '8px',
        lg: '12px',
        xl: '16px',
      },
      boxShadow: {
        card: '0 1px 2px rgb(15 23 42 / 0.04)',
        pop: '0 10px 30px rgb(15 23 42 / 0.12)',
        focus: '0 0 0 3px hsl(var(--brand) / 0.2)',
      },
      transitionTimingFunction: {
        out: 'cubic-bezier(.22,1,.36,1)',
        'in-out': 'cubic-bezier(.4,0,.2,1)',
      },
      keyframes: {
        'fade-up': {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        'scale-in': {
          from: { opacity: '0', transform: 'scale(.97)' },
          to: { opacity: '1', transform: 'scale(1)' },
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
        'pulse-soft': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '.55' },
        },
      },
      animation: {
        'fade-up': 'fade-up .32s cubic-bezier(.22,1,.36,1) both',
        'fade-in': 'fade-in .2s ease-out both',
        'scale-in': 'scale-in .2s cubic-bezier(.22,1,.36,1) both',
        shimmer: 'shimmer 1.6s infinite',
        'pulse-soft': 'pulse-soft 1.8s ease-in-out infinite',
      },
      zIndex: {
        sticky: '20',
        dropdown: '40',
        drawer: '50',
        modal: '60',
        toast: '70',
      },
    },
  },
  plugins: [],
} satisfies Config
