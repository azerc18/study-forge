import { ApiError } from '../../lib/api'

/** 75 → "1:15", 3700 → "1:01:40" */
export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = String(s % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`
}

/** 45 → "45s", 312 → "5m 12s", 3720 → "1h 2m" */
export function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (h > 0) return `${h}h ${m}m`
  return `${m}m ${seconds % 60}s`
}

export function scoreColor(score: number): string {
  if (score >= 8) return 'text-brand-ink'
  if (score >= 5) return 'text-amber-700'
  return 'text-red-600'
}

export function barColor(ratio: number): string {
  if (ratio >= 0.8) return 'bg-brand-ink'
  if (ratio >= 0.5) return 'bg-amber-500'
  return 'bg-red-500'
}

export function examErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'NO_MCQ_QUESTIONS':
        return 'This set has no multiple-choice questions to take as an exam.'
      case 'QUIZ_SET_NOT_READY':
        return 'This quiz set is not ready yet.'
      case 'QUIZ_SET_NOT_FOUND':
      case 'EXAM_NOT_FOUND':
        return 'This exam no longer exists.'
      case 'INVALID_EXAM_ANSWER':
        return 'Some answers do not belong to this exam. Please reload the page.'
    }
  }
  return 'Something went wrong. Please try again.'
}
