import { useState } from 'react'
import { buttonClass, type ButtonSize } from '../../components/ui/button.ts'

interface Props {
  onConfirm: () => void
  pending: boolean
  label?: string
  size?: ButtonSize
}

/** Xoá 2 bước: bấm lần đầu hiện "Delete? [Yes, delete] [Cancel]", tránh xoá nhầm mà không cần hộp thoại. */
export default function DeleteButton({ onConfirm, pending, label = 'Delete', size = 'sm' }: Props) {
  const [asking, setAsking] = useState(false)

  if (pending) {
    return (
      <button type="button" disabled className={buttonClass('danger', size)}>
        Deleting…
      </button>
    )
  }

  if (!asking) {
    return (
      <button type="button" onClick={() => setAsking(true)} className={buttonClass('danger', size)}>
        <TrashIcon />
        {label}
      </button>
    )
  }

  return (
    <span className="inline-flex items-center gap-2">
      <span className="hidden text-xs text-slate-500 sm:inline">Are you sure?</span>
      <button type="button" onClick={onConfirm} className={buttonClass('dangerSolid', size)}>
        Yes, delete
      </button>
      <button type="button" onClick={() => setAsking(false)} className={buttonClass('secondary', size)}>
        Cancel
      </button>
    </span>
  )
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-3.5" aria-hidden>
      <path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
