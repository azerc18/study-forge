import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../lib/api'
import {
  deleteDocument,
  getDocument,
  getGenerationOptions,
  isProcessing,
  listDocuments,
  uploadDocument,
} from './api'

export const PAGE_SIZE = 10
const POLL_MS = 2000

export const documentKeys = {
  all: ['documents'] as const,
  lists: ['documents', 'list'] as const,
  list: (page: number) => ['documents', 'list', page] as const,
  detail: (id: string) => ['documents', 'detail', id] as const,
  options: (id: string) => ['documents', 'options', id] as const,
}

// 404 (không có / của người khác) và 400 (id sai định dạng) thì thử lại cũng vô ích
const retryUnlessNotFound = (count: number, err: Error) =>
  !(err instanceof ApiError && (err.status === 404 || err.status === 400)) && count < 1

/** Danh sách. Tự poll khi còn document đang xử lý, xong hết thì dừng. */
export function useDocuments(page: number) {
  return useQuery({
    queryKey: documentKeys.list(page),
    queryFn: () => listDocuments(page, PAGE_SIZE),
    placeholderData: keepPreviousData, // chuyển trang không bị nháy trắng
    refetchInterval: (q) => (q.state.data?.content.some((d) => isProcessing(d.status)) ? POLL_MS : false),
  })
}

/** Chi tiết. Poll đến khi READY hoặc FAILED. */
export function useDocument(id: string) {
  return useQuery({
    queryKey: documentKeys.detail(id),
    queryFn: () => getDocument(id),
    retry: retryUnlessNotFound,
    refetchInterval: (q) => (q.state.data && isProcessing(q.state.data.status) ? POLL_MS : false),
  })
}

/** Chỉ gọi khi document đã READY (nếu chưa, backend trả 409). */
export function useGenerationOptions(id: string, enabled: boolean) {
  return useQuery({
    queryKey: documentKeys.options(id),
    queryFn: () => getGenerationOptions(id),
    enabled,
    staleTime: Infinity, // document READY thì số từ không đổi nữa
  })
}

export function useUploadDocument() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, onProgress }: { file: File; onProgress?: (f: number) => void }) =>
      uploadDocument(file, onProgress),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: documentKeys.lists }),
  })
}

export function useDeleteDocument() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteDocument,
    // Chỉ làm mới danh sách. Nếu làm mới cả detail thì trang chi tiết đang mở sẽ gọi lại và nháy 404 trước khi chuyển trang
    onSuccess: () => queryClient.invalidateQueries({ queryKey: documentKeys.lists }),
  })
}
