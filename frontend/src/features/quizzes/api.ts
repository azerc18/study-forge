import { apiFetch } from '../../lib/api.ts'
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
