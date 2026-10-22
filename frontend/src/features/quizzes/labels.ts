import { ApiError } from '../../lib/api.ts'
import type { OutputLanguage, QuestionType } from '../documents/api'

export const TYPE_LABELS: Record<QuestionType, string> = {
  MCQ: 'Multiple choice',
  ESSAY: 'Essay',
  MIXED: 'Mixed',
}

export const LANGUAGE_LABELS: Record<OutputLanguage, string> = {
  EN: 'English',
  VI: 'Tiếng Việt',
}

const resetFormat = new Intl.DateTimeFormat('en', { hour: '2-digit', minute: '2-digit', month: 'short', day: 'numeric' })

/** Lỗi khi bấm "Generate" → câu dễ hiểu cho user. */
export function createErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'QUOTA_EXCEEDED': {
        const d = err.details as { dailyQuota?: number; resetsAt?: string } | undefined
        const when = d?.resetsAt ? ` Try again after ${resetFormat.format(new Date(d.resetsAt))}.` : ''
        const count = d?.dailyQuota ? `${d.dailyQuota} ` : ''
        return `You've used all ${count}generations for today.${when}`
      }
      case 'INVALID_QUESTION_COUNT':
        return 'That number of questions is not available for this document.'
      case 'DOCUMENT_NOT_READY':
        return 'This document is still being processed.'
      case 'DOCUMENT_NOT_FOUND':
        return 'This document no longer exists.'
      case 'ACCOUNT_LOCKED':
        return 'Your account has been locked.'
    }
  }
  return 'Could not start generating. Please try again.'
}
