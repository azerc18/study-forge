import { apiFetch } from '../../lib/api'

export type ExamStatus = 'IN_PROGRESS' | 'SUBMITTED'
export type OptionLabel = 'A' | 'B' | 'C' | 'D'

export interface ExamOption {
  label: OptionLabel
  content: string
}

/** Khi đang làm: selectedLabel/correctLabel/correct/explanation = null (server không gửi đáp án). */
export interface ExamQuestion {
  id: string
  number: number
  content: string
  options: ExamOption[]
  topic: string | null
  sourcePages: number[]
  selectedLabel: OptionLabel | null
  correctLabel: OptionLabel | null
  correct: boolean | null
  explanation: string | null
}

export interface TopicResult {
  topic: string
  correct: number
  total: number
}

export interface Exam {
  id: string
  quizSetId: string
  quizSetTitle: string
  status: ExamStatus
  total: number
  timeLimitSeconds: number | null
  startedAt: string
  deadline: string | null
  submittedAt: string | null
  correctCount: number | null
  score: number | null
  durationSeconds: number | null
  questions: ExamQuestion[]
  /** Chủ đề yếu nhất trước. Rỗng khi đang làm. */
  topics: TopicResult[]
}

export interface ExamSummary {
  id: string
  status: ExamStatus
  total: number
  correctCount: number | null
  score: number | null
  timeLimitSeconds: number | null
  startedAt: string
  submittedAt: string | null
  durationSeconds: number | null
}

export interface StartExamInput {
  timeLimitMinutes: number | null
  shuffle: boolean
}

export type Answers = Record<string, OptionLabel>

export function startExam(quizSetId: string, input: StartExamInput): Promise<Exam> {
  return apiFetch(`/api/quiz-sets/${encodeURIComponent(quizSetId)}/exams`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function listExams(quizSetId: string): Promise<ExamSummary[]> {
  return apiFetch(`/api/quiz-sets/${encodeURIComponent(quizSetId)}/exams`)
}

export function getExam(id: string): Promise<Exam> {
  return apiFetch(`/api/exams/${encodeURIComponent(id)}`)
}

/** Gửi đủ mọi câu: câu chưa chọn gửi label null (= bỏ trống). */
export function submitExam(exam: Exam, answers: Answers): Promise<Exam> {
  const body = { answers: exam.questions.map((q) => ({ questionId: q.id, label: answers[q.id] ?? null })) }
  return apiFetch(`/api/exams/${encodeURIComponent(exam.id)}/submit`, { method: 'POST', body: JSON.stringify(body) })
}
