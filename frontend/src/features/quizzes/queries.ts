import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../lib/api.ts'
import {
  createQuizSet,
  deleteQuizSet,
  getQuestions,
  getQuizSet,
  isGenerating,
  listQuizSets,
  renameQuizSet,
  retryQuizSet,
  type QuizSet,
} from './api.ts'

export const QUIZ_PAGE_SIZE = 10
const POLL_MS = 2000

export const quizKeys = {
  lists: ['quiz-sets', 'list'] as const,
  list: (page: number, documentId?: string) => ['quiz-sets', 'list', documentId ?? 'all', page] as const,
  detail: (id: string) => ['quiz-sets', 'detail', id] as const,
  questions: (id: string) => ['quiz-sets', 'questions', id] as const,
}

const retryUnlessNotFound = (count: number, err: Error) =>
  !(err instanceof ApiError && (err.status === 404 || err.status === 400)) && count < 1

/** Danh sách bộ đề; tự cập nhật khi còn bộ đang sinh. */
export function useQuizSets(page: number, documentId?: string) {
  return useQuery({
    queryKey: quizKeys.list(page, documentId),
    queryFn: () => listQuizSets(page, QUIZ_PAGE_SIZE, documentId),
    placeholderData: keepPreviousData,
    refetchInterval: (q) => (q.state.data?.content.some((s) => isGenerating(s.status)) ? POLL_MS : false),
  })
}

/** Chi tiết 1 bộ đề; poll mỗi 2s khi đang QUEUED/GENERATING để cập nhật tiến độ. */
export function useQuizSet(id: string) {
  return useQuery({
    queryKey: quizKeys.detail(id),
    queryFn: () => getQuizSet(id),
    retry: retryUnlessNotFound,
    refetchInterval: (q) => (q.state.data && isGenerating(q.state.data.status) ? POLL_MS : false),
  })
}

/** Chỉ gọi khi bộ đề đã READY/PARTIAL (nếu chưa, backend trả 409). Câu hỏi không đổi nữa nên giữ cache lâu. */
export function useQuestions(id: string, enabled: boolean) {
  return useQuery({
    queryKey: quizKeys.questions(id),
    queryFn: () => getQuestions(id),
    enabled,
    staleTime: 5 * 60_000,
  })
}

export function useCreateQuizSet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createQuizSet,
    onSuccess: (set) => {
      queryClient.setQueryData(quizKeys.detail(set.id), set) // trang tiến độ hiện ngay, không phải chờ tải
      return queryClient.invalidateQueries({ queryKey: quizKeys.lists })
    },
  })
}

export function useRenameQuizSet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) => renameQuizSet(id, title),
    onSuccess: (set) => {
      queryClient.setQueryData<QuizSet>(quizKeys.detail(set.id), set)
      return queryClient.invalidateQueries({ queryKey: quizKeys.lists })
    },
  })
}

export function useDeleteQuizSet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteQuizSet,
    // Chỉ làm mới danh sách; làm mới cả detail thì trang đang mở sẽ gọi lại và nháy 404 trước khi chuyển trang
    onSuccess: () => queryClient.invalidateQueries({ queryKey: quizKeys.lists }),
  })
}

export function useRetryQuizSet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: retryQuizSet,
    onSuccess: (set) => {
      queryClient.setQueryData(quizKeys.detail(set.id), set) // status QUEUED → useQuizSet tự poll lại
      return queryClient.invalidateQueries({ queryKey: quizKeys.lists })
    },
  })
}
