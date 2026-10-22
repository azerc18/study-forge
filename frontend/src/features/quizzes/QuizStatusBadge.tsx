import type { QuizSetStatus } from './api.ts'

const STYLES: Record<QuizSetStatus, { label: string; className: string }> = {
  QUEUED: { label: 'Queued', className: 'bg-slate-100 text-slate-600' },
  GENERATING: { label: 'Generating', className: 'bg-slate-100 text-slate-600' },
  READY: { label: 'Ready', className: 'bg-brand text-brand-ink' },
  PARTIAL: { label: 'Partial', className: 'bg-amber-50 text-amber-700' },
  FAILED: { label: 'Failed', className: 'bg-red-50 text-red-700' },
}

export default function QuizStatusBadge({ status }: { status: QuizSetStatus }) {
  const { label, className } = STYLES[status]
  const busy = status === 'QUEUED' || status === 'GENERATING'

  return (
    <span className={`inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${className}`}>
      {busy && <span className="size-1.5 animate-pulse rounded-full bg-current" />}
      {label}
    </span>
  )
}
