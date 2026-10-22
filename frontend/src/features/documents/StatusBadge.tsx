import type { DocumentStatus } from './api'

const STYLES: Record<DocumentStatus, { label: string; className: string }> = {
  UPLOADED: { label: 'Processing', className: 'bg-slate-100 text-slate-600' },
  EXTRACTING: { label: 'Processing', className: 'bg-slate-100 text-slate-600' },
  READY: { label: 'Ready', className: 'bg-brand text-brand-ink' },
  FAILED: { label: 'Failed', className: 'bg-red-50 text-red-700' },
}

export default function StatusBadge({ status }: { status: DocumentStatus }) {
  const { label, className } = STYLES[status]
  const busy = status === 'UPLOADED' || status === 'EXTRACTING'

  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${className}`}>
      {busy && <span className="size-1.5 animate-pulse rounded-full bg-current" />}
      {label}
    </span>
  )
}
