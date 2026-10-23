import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../lib/api'
import { clearDraft } from './draft.ts'
import { getExam, listExams, startExam, submitExam, type Answers, type Exam, type StartExamInput } from './api.ts'

export const examKeys = {
  detail: (id: string) => ['exams', 'detail', id] as const,
  history: (quizSetId: string) => ['exams', 'history', quizSetId] as const,
}

const retryUnlessNotFound = (count: number, err: Error) =>
  !(err instanceof ApiError && (err.status === 404 || err.status === 400)) && count < 1

export function useExam(id: string) {
  return useQuery({
    queryKey: examKeys.detail(id),
    queryFn: () => getExam(id),
    retry: retryUnlessNotFound,
    staleTime: Infinity, // bài thi chỉ đổi khi chính mình nộp → cập nhật bằng setQueryData
  })
}

export function useExamHistory(quizSetId: string, enabled = true) {
  return useQuery({
    queryKey: examKeys.history(quizSetId),
    queryFn: () => listExams(quizSetId),
    enabled,
  })
}

export function useStartExam(quizSetId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: StartExamInput) => startExam(quizSetId, input),
    onSuccess: (exam) => {
      queryClient.setQueryData(examKeys.detail(exam.id), exam)
      return queryClient.invalidateQueries({ queryKey: examKeys.history(quizSetId) })
    },
  })
}

export function useSubmitExam(exam: Exam) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (answers: Answers) => submitExam(exam, answers),
    onSuccess: (result) => {
      clearDraft(result.id) // làm ở đây (không phải ở mutate) vì màn làm bài sẽ unmount ngay khi có kết quả
      queryClient.setQueryData(examKeys.detail(result.id), result)
      return queryClient.invalidateQueries({ queryKey: examKeys.history(result.quizSetId) })
    },
  })
}
