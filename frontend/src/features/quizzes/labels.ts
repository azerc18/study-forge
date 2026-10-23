import { ApiError } from '../../lib/api'
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
      case 'AI_BUDGET_EXCEEDED':
        return "The AI service has reached today's limit. Please try again tomorrow."
    }
  }
  return 'Could not start generating. Please try again.'
}

/** Lỗi khi nộp bài tự luận → câu dễ hiểu cho user. */
export function feedbackErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'FEEDBACK_LIMIT_EXCEEDED': {
        const d = err.details as { dailyLimit?: number; resetsAt?: string } | undefined
        const when = d?.resetsAt ? ` Try again after ${resetFormat.format(new Date(d.resetsAt))}.` : ''
        const limit = d?.dailyLimit ? ` of ${d.dailyLimit}` : ''
        return `You've reached today's limit${limit} AI gradings.${when}`
      }
      case 'FEEDBACK_UNAVAILABLE':
        return 'The AI grader is busy right now. Your answer is still here — please try again in a moment.'
      case 'VALIDATION_ERROR':
        return 'Your answer is empty or longer than 3,000 characters.'
      case 'AI_BUDGET_EXCEEDED':
        return "The AI service has reached today's limit. Please try again tomorrow."
    }
  }
  return 'Could not grade your answer. Please try again.'
}
