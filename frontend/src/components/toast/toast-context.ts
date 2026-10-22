import { createContext, useContext } from 'react'

export type ToastKind = 'error' | 'success' | 'info'

export interface ToastApi {
  show: (message: string, kind?: ToastKind) => void
}

export const ToastContext = createContext<ToastApi | null>(null)

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>')
  return ctx
}
