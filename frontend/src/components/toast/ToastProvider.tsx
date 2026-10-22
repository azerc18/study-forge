import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { ToastContext, type ToastKind } from './toast-context.ts'

interface Toast {
  id: number
  message: string
  kind: ToastKind
}

const DURATION_MS = 5000

const STYLES: Record<ToastKind, string> = {
  error: 'border-red-100 bg-red-50 text-red-700',
  success: 'border-brand-strong bg-brand text-brand-ink',
  info: 'border-slate-200 bg-white text-slate-700',
}

let nextId = 1

/** Thông báo nhỏ ở cuối màn hình, tự ẩn sau 5 giây. Dùng cho lỗi của các thao tác (tạo, xoá, đổi tên…). */
export default function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const dismiss = useCallback((id: number) => {
    setToasts((list) => list.filter((t) => t.id !== id))
  }, [])

  const show = useCallback(
    (message: string, kind: ToastKind = 'info') => {
      const id = nextId++
      setToasts((list) => [...list.slice(-2), { id, message, kind }]) // tối đa 3 cái cùng lúc
      window.setTimeout(() => dismiss(id), DURATION_MS)
    },
    [dismiss],
  )

  const api = useMemo(() => ({ show }), [show])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex flex-col items-center gap-2 px-4"
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            role={t.kind === 'error' ? 'alert' : 'status'}
            className={`pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl border px-4 py-3 text-sm shadow-sm ${STYLES[t.kind]}`}
          >
            <p className="flex-1">{t.message}</p>
            <button
              type="button"
              onClick={() => dismiss(t.id)}
              aria-label="Dismiss"
              className="opacity-60 transition hover:opacity-100"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
