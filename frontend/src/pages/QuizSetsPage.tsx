import { useState } from 'react'
import { buttonClass } from '../components/ui/button.ts'
import { Link } from 'react-router'
import { useToast } from '../components/toast/toast-context'
import QuizSetRow from '../features/quizzes/QuizSetRow'
import { useDeleteQuizSet, useQuizSets } from '../features/quizzes/queries'

export default function QuizSetsPage() {
  const [page, setPage] = useState(0)
  const { data, isPending, isError, refetch } = useQuizSets(page)
  const remove = useDeleteQuizSet()
  const toast = useToast()

  function handleDelete(id: string) {
    remove.mutate(id, {
      onSuccess: () => {
        if (data && data.content.length === 1 && page > 0) setPage(page - 1)
      },
      onError: () => toast.show('Could not delete this quiz set.', 'error'),
    })
  }

  return (
    <section className="mx-auto w-full max-w-2xl py-10">
      <h1 className="text-2xl font-semibold tracking-tight">Your quiz sets</h1>
      <p className="mt-1 text-sm text-slate-500">Every set you've generated, newest first.</p>

      <div className="mt-8 rounded-3xl border border-slate-100 bg-white/80 px-6 py-2 shadow-sm">
        {isPending && (
          <div className="space-y-4 py-4">
            {[0, 1, 2].map((i) => (
              <div key={i} className="h-10 animate-pulse rounded-xl bg-slate-100" />
            ))}
          </div>
        )}

        {isError && (
          <p className="py-10 text-center text-sm text-slate-500">
            Couldn't load your quiz sets.{' '}
            <button type="button" onClick={() => void refetch()} className="font-medium text-brand-ink underline">
              Try again
            </button>
          </p>
        )}

        {data && data.totalElements === 0 && (
          <div className="py-10 text-center text-sm text-slate-500">
            <p>No quiz sets yet.</p>
            <Link to="/documents" className="mt-2 inline-block font-medium text-brand-ink underline">
              Pick a document to start
            </Link>
          </div>
        )}

        {data && data.content.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {data.content.map((set) => (
              <QuizSetRow
                key={set.id}
                set={set}
                onDelete={handleDelete}
                deleting={remove.isPending && remove.variables === set.id}
              />
            ))}
          </ul>
        )}

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-100 py-3 text-sm">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
              className={buttonClass('secondary', 'sm')}
            >
              ← Newer
            </button>
            <span className="text-xs text-slate-400">
              Page {page + 1} of {data.totalPages}
            </span>
            <button
              type="button"
              disabled={!data.hasNext}
              onClick={() => setPage(page + 1)}
              className={buttonClass('secondary', 'sm')}
            >
              Older →
            </button>
          </div>
        )}
      </div>
    </section>
  )
}
