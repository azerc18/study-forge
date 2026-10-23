import { ApiError } from '../../lib/api'
const usd = new Intl.NumberFormat('en', { style: 'currency', currency: 'USD', maximumFractionDigits: 4 })
const compact = new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 })
const dayLabel = new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', timeZone: 'UTC' })
const dateTime = new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })

/** 0.0123 → "$0.0123", 1.5 → "$1.50" */
export const formatUsd = (v: number) => (v >= 1 ? usd.format(Math.round(v * 100) / 100) : usd.format(v))
export const formatCompact = (v: number) => compact.format(v)
/** "2026-10-23" → "Oct 23" (ngày đã là giờ VN từ server; parse như UTC để không lệch ngày) */
export const formatDay = (iso: string) => dayLabel.format(new Date(`${iso}T00:00:00Z`))
export const formatDateTime = (iso: string | null) => (iso ? dateTime.format(new Date(iso)) : '—')

export function adminErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'CANNOT_MODIFY_SELF':
        return 'You cannot lock your own account or another admin.'
      case 'USER_NOT_FOUND':
        return 'This user no longer exists.'
      case 'QUIZ_SET_NOT_RETRYABLE':
        return 'This quiz set is no longer in a failed state.'
      case 'QUIZ_SET_NOT_FOUND':
        return 'This quiz set no longer exists.'
      case 'VALIDATION_ERROR':
        return 'Daily quota must be between 0 and 1000.'
      case 'FORBIDDEN':
        return 'You need admin rights for this.'
    }
  }
  return 'Something went wrong. Please try again.'
}
