import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  adminRetryQuizSet,
  getAdminStats,
  listAdminUsers,
  listFailedQuizSets,
  updateAdminUser,
  type UpdateUserInput,
} from './api'

export const ADMIN_PAGE_SIZE = 20

export const adminKeys = {
  all: ['admin'] as const,
  stats: (days: number) => ['admin', 'stats', days] as const,
  users: (q: string, page: number) => ['admin', 'users', q, page] as const,
  failed: (page: number) => ['admin', 'failed', page] as const,
}

export function useAdminStats(days: number) {
  return useQuery({
    queryKey: adminKeys.stats(days),
    queryFn: () => getAdminStats(days),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000, // trang admin để mở lâu → tự làm mới mỗi phút
  })
}

export function useAdminUsers(q: string, page: number) {
  return useQuery({
    queryKey: adminKeys.users(q, page),
    queryFn: () => listAdminUsers(q, page, ADMIN_PAGE_SIZE),
    placeholderData: keepPreviousData,
  })
}

export function useUpdateAdminUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateUserInput }) => updateAdminUser(id, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })
}

export function useFailedQuizSets(page: number) {
  return useQuery({
    queryKey: adminKeys.failed(page),
    queryFn: () => listFailedQuizSets(page, ADMIN_PAGE_SIZE),
    placeholderData: keepPreviousData,
  })
}

export function useAdminRetry() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: adminRetryQuizSet,
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'failed'] }),
        queryClient.invalidateQueries({ queryKey: ['admin', 'stats'] }),
      ]),
  })
}
