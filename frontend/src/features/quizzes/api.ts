import { apiFetch } from '../../lib/api'
import type { OutputLanguage, PageResponse, QuestionType } from '../documents/api'

export type QuizSetStatus = 'QUEUED' | 'GENERATING' | 'READY' | 'PARTIAL' | 'FAILED'

export interface QuizSet {
  id: string
  documentId: string
  title: string
  questionType: QuestionType
  requestedCount: number
  language: OutputLanguage
  status: QuizSetStatus
  generatedCount: number
  totalChunks: number
  completedChunks: number
  errorMessage?: string | null
  createdAt: string
}

export interface QuestionOption {
  label: string
  content: string
  correct: boolean
}

export interface Question {
  id: string
  orderIndex: number
  type: 'MCQ' | 'ESSAY'
  content: string
  options: QuestionOption[]
  explanation?: string | null
  modelAnswer?: string | null
  rubric: string[]
  sourcePages: number[]
  topic?: string | null
}

export type Verdict = 'MET' | 'PARTIAL' | 'MISSING'

export interface RubricResult {
  point: number
  text: string
  verdict: Verdict
  comment?: string | null
}

export interface EssayFeedback {
  id: string
  questionId: string
  answer: string
  score: number
  rubric: RubricResult[]
  summary: string
  corrections: string[]
  suggestions: string[]
  improvedAnswer?: string | null
  createdAt: string
}

export const MAX_ESSAY_CHARS = 3000

/** Lượt tạo bộ đề trong ngày (GET /api/quiz-sets/quota). */
export interface QuotaInfo {
  dailyQuota: number
  usedToday: number
  remaining: number
  resetsAt: string
}

export interface CreateQuizSetInput {
  documentId: string
  questionType: QuestionType
  count: number
  language: OutputLanguage
  title?: string
}

export const isGenerating = (status: QuizSetStatus) => status === 'QUEUED' || status === 'GENERATING'
export const hasQuestions = (status: QuizSetStatus) => status === 'READY' || status === 'PARTIAL'

const path = (id: string) => `/api/quiz-sets/${encodeURIComponent(id)}`

export function createQuizSet(input: CreateQuizSetInput): Promise<QuizSet> {
  return apiFetch('/api/quiz-sets', { method: 'POST', body: JSON.stringify(input) })
}

export function getQuota(): Promise<QuotaInfo> {
  return apiFetch('/api/quiz-sets/quota')
}

export function listQuizSets(page: number, size: number, documentId?: string): Promise<PageResponse<QuizSet>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (documentId) params.set('documentId', documentId)
  return apiFetch(`/api/quiz-sets?${params}`)
}

export function getQuizSet(id: string): Promise<QuizSet> {
  return apiFetch(path(id))
}

export function getQuestions(id: string): Promise<Question[]> {
  return apiFetch(`${path(id)}/questions`)
}

export function renameQuizSet(id: string, title: string): Promise<QuizSet> {
  return apiFetch(path(id), { method: 'PATCH', body: JSON.stringify({ title }) })
}

export function deleteQuizSet(id: string): Promise<void> {
  return apiFetch(path(id), { method: 'DELETE' })
}

export function retryQuizSet(id: string): Promise<QuizSet> {
  return apiFetch(`${path(id)}/retry`, { method: 'POST' })
}

const feedbackPath = (questionId: string) => `/api/questions/${encodeURIComponent(questionId)}/essay-feedback`

export function submitEssay(questionId: string, answer: string): Promise<EssayFeedback> {
  return apiFetch(feedbackPath(questionId), { method: 'POST', body: JSON.stringify({ answer }) })
}

export function getEssayHistory(questionId: string): Promise<EssayFeedback[]> {
  return apiFetch(feedbackPath(questionId))
}
