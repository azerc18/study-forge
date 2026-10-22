/**
 * Kiểu nút dùng chung cho cả app. Dùng cho <button>, <Link>, <NavLink>:
 *   <button className={buttonClass('danger', 'sm')}>Delete</button>
 */
export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'dangerSolid'
export type ButtonSize = 'sm' | 'md'

const BASE =
  'inline-flex shrink-0 items-center justify-center gap-1.5 rounded-full font-medium whitespace-nowrap transition ' +
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-ink/40 ' +
  'disabled:cursor-not-allowed disabled:opacity-50'

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-brand text-brand-ink shadow-sm hover:bg-brand-strong',
  secondary: 'border border-slate-200 bg-white text-slate-700 shadow-sm hover:border-slate-300 hover:bg-slate-50',
  danger: 'border border-red-200 bg-white text-red-600 shadow-sm hover:border-red-300 hover:bg-red-50',
  dangerSolid: 'bg-red-600 text-white shadow-sm hover:bg-red-700',
}

const SIZES: Record<ButtonSize, string> = {
  sm: 'px-3.5 py-1.5 text-xs',
  md: 'px-4 py-2 text-sm',
}

export function buttonClass(variant: ButtonVariant = 'secondary', size: ButtonSize = 'md'): string {
  return `${BASE} ${VARIANTS[variant]} ${SIZES[size]}`
}
