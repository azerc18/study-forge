import { apiFetch, type Role } from '../../lib/api'
import type { PageResponse, QuestionType } from '../documents/api'
import type { QuizSet } from '../quizzes/api'

export interface AdminUserRow {
    id: string
    email: string
    fullName: string | null
    role: Role
    dailyQuota: number
    locked: boolean
    createdAt: string
    lastLoginAt: string | null
    /** Số bộ đề tạo hôm nay (tính cả bộ đã xoá, giống quota) */
    usedToday: number
    quizSetCount: number
}

export interface UpdateUserInput {
    dailyQuota?: number
    locked?: boolean
}

export interface DailyStat {
    date: string // yyyy-MM-dd theo giờ VN
    newUsers: number
    quizSets: number
    failedQuizSets: number
    exams: number
    essayGrades: number
    promptTokens: number
    completionTokens: number
    costUsd: number
}

export interface AdminStats {
    today: DailyStat
    /** Cũ → mới; phần tử cuối là hôm nay */
    daily: DailyStat[]
    totalUsers: number
    budgetUsd: number
    budgetExceeded: boolean
}

export interface FailedQuizSetRow {
    quizSetId: string
    title: string
    userEmail: string
    questionType: QuestionType
    requestedCount: number
    errorMessage: string | null
    lastError: string | null
    createdAt: string
}

export function getAdminStats(days: number): Promise<AdminStats> {
    return apiFetch(`/api/admin/stats?days=${days}`)
}

export function listAdminUsers(q: string, page: number, size: number): Promise<PageResponse<AdminUserRow>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (q.trim()) params.set('q', q.trim())
    return apiFetch(`/api/admin/users?${params}`)
}

export function updateAdminUser(id: string, input: UpdateUserInput): Promise<AdminUserRow> {
    return apiFetch(`/api/admin/users/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function listFailedQuizSets(page: number, size: number): Promise<PageResponse<FailedQuizSetRow>> {
    return apiFetch(`/api/admin/quiz-sets/failed?page=${page}&size=${size}`)
}

export function adminRetryQuizSet(id: string): Promise<QuizSet> {
    return apiFetch(`/api/admin/quiz-sets/${encodeURIComponent(id)}/retry`, { method: 'POST' })
}
