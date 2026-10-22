import { useState } from 'react'

interface Props {
  onConfirm: () => void
  pending: boolean
  label?: string
}

/** Xoá 2 bước: bấm lần đầu hiện "Delete? Yes / No", tránh xoá nhầm mà không cần hộp thoại. */
export default function DeleteButton({ onConfirm, pending, label = 'Delete' }: Props) {
  const [asking, setAsking] = useState(false)

  if (pending) return <span className="text-xs text-slate-400">Deleting…</span>

  if (!asking) {
    return (
      <button
        type="button"
        onClick={() => setAsking(true)}
        className="rounded-full px-2.5 py-1 text-xs text-slate-400 transition hover:bg-red-50 hover:text-red-600"
      >
        {label}
      </button>
    )
  }

  return (
    <span className="inline-flex items-center gap-1 text-xs">
      <span className="text-slate-500">Delete?</span>
      <button
        type="button"
        onClick={onConfirm}
        className="rounded-full px-2 py-1 font-medium text-red-600 transition hover:bg-red-50"
      >
        Yes
      </button>
      <button
        type="button"
        onClick={() => setAsking(false)}
        className="rounded-full px-2 py-1 text-slate-500 transition hover:bg-slate-100"
      >
        No
      </button>
    </span>
  )
}
