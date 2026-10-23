import { useState } from 'react'
import { useToast } from '../../components/toast/toast-context'
import { buttonClass } from '../../components/ui/button'
import { TYPE_LABELS } from '../quizzes/labels'
import type { FailedQuizSetRow } from './api'
import { adminErrorMessage, formatDateTime } from './format.ts'
import { useAdminRetry, useFailedQuizSets } from './queries.ts'

function FailedRow({ row }: { row: FailedQuizSetRow }) {
  const retry = useAdminRetry()
  const toast = useToast()

  return (
    <li className="py-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-medium">{row.title}</p>
          <p className="mt-0.5 text-xs text-slate-500">
            {row.userEmail} · {TYPE_LABELS[row.questionType]} · {row.requestedCount} questions · {formatDateTime(row.createdAt)}
          </p>
        </div>
        <button
          type="button"
          disabled={retry.isPending}
          onClick={() =>
            retry.mutate(row.quizSetId, {
              onSuccess: () => toast.show(`"${row.title}" queued again.`, 'success'),
              onError: (err) => toast.show(adminErrorMessage(err), 'error'),
            })
          }
          className={buttonClass('secondary', 'sm')}
        >
          {retry.isPending ? 'Retrying…' : '↻ Retry'}
        </button>
      </div>
      {row.lastError ? (
        <details className="mt-2">
          <summary className="cursor-pointer text-xs text-red-600 hover:text-red-700">
            {row.lastError.length > 90 ? `${row.lastError.slice(0, 90)}…` : row.lastError}
          </summary>
          <pre className="mt-2 max-h-48 overflow-auto rounded-xl bg-slate-900 p-3 text-[11px] leading-relaxed whitespace-pre-wrap text-slate-100">
            {row.lastError}
          </pre>
        </details>
      ) : (
        <p className="mt-2 text-xs text-slate-400">{row.errorMessage ?? 'No error details'}</p>
      )}
    </li>
  )
}

export default function FailedSetsList() {
  const [page, setPage] = useState(0)
  const { data, isPending, isError } = useFailedQuizSets(page)

  return (
    <section className="rounded-3xl border border-slate-100 bg-white/80 p-5 shadow-sm sm:p-6">
      <h2 className="font-semibold">
        Failed quiz sets {data && <span className="font-normal text-slate-400">· {data.totalElements}</span>}
      </h2>
      <p className="mt-1 text-xs text-slate-500">Retrying does not use the user's daily quota.</p>

      {isPending ? (
        <div className="mt-4 h-24 animate-pulse rounded-xl bg-slate-50" />
      ) : isError ? (
        <p className="py-10 text-center text-sm text-slate-500">Couldn't load failed quiz sets.</p>
      ) : data.content.length === 0 ? (
        <p className="mt-4 rounded-2xl bg-brand/40 px-4 py-8 text-center text-sm text-brand-ink">
          No failed quiz sets. Everything is running smoothly.
        </p>
      ) : (
        <ul className="mt-2 divide-y divide-slate-100">
          {data.content.map((row) => (
            <FailedRow key={row.quizSetId} row={row} />
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)} className={buttonClass('secondary', 'sm')}>
            ← Previous
          </button>
          <span>
            Page {page + 1} of {data.totalPages}
          </span>
          <button type="button" disabled={!data.hasNext} onClick={() => setPage((p) => p + 1)} className={buttonClass('secondary', 'sm')}>
            Next →
          </button>
        </div>
      )}
    </section>
  )
}
