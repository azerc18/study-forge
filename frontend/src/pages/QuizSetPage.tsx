import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useToast } from '../components/toast/toast-context'
import { ApiError } from '../lib/api'
import { buttonClass } from '../components/ui/button'
import DeleteButton from '../features/documents/DeleteButton'
import { formatDate } from '../features/documents/format'
import { hasQuestions, isGenerating, type QuizSet } from '../features/quizzes/api.ts'
import EditableTitle from '../features/quizzes/EditableTitle'
import GenerationProgress from '../features/quizzes/GenerationProgress'
import NewSetButton from '../features/quizzes/NewSetButton.tsx'
import { LANGUAGE_LABELS, TYPE_LABELS } from '../features/quizzes/labels'
import QuestionCard from '../features/quizzes/QuestionCard'
import QuizStatusBadge from '../features/quizzes/QuizStatusBadge'
import { useDeleteQuizSet, useQuestions, useQuizSet, useRetryQuizSet } from '../features/quizzes/queries.ts'

function FailedPanel({ set }: { set: QuizSet }) {
  const retry = useRetryQuizSet()
  const toast = useToast()

  return (
    <div className="rounded-2xl bg-red-50 px-5 py-4">
      <p className="font-medium text-red-700">Generation failed.</p>
      <p className="mt-1 text-sm text-red-600">{set.errorMessage ?? 'Something went wrong. Please try again.'}</p>
      <button
        type="button"
        disabled={retry.isPending}
        onClick={() => retry.mutate(set.id, { onError: () => toast.show('Could not retry right now.', 'error') })}
        className="mt-4 rounded-full bg-white px-4 py-2 text-sm font-medium text-red-700 shadow-sm transition hover:bg-red-100 disabled:opacity-60"
      >
        {retry.isPending ? 'Retrying…' : 'Try again'}
      </button>
      <p className="mt-2 text-xs text-red-500">Retrying doesn't use your daily quota.</p>
    </div>
  )
}

function QuestionList({ set }: { set: QuizSet }) {
  const [revealAll, setRevealAll] = useState(false)
  const { data: questions, isPending, isError, refetch } = useQuestions(set.id, true)

  if (isPending) {
    return (
      <div className="space-y-3">
        {[0, 1, 2].map((i) => (
          <div key={i} className="h-40 animate-pulse rounded-3xl bg-slate-50" />
        ))}
      </div>
    )
  }
  if (isError) {
    return (
      <p className="py-10 text-center text-sm text-slate-500">
        Couldn't load the questions.{' '}
        <button type="button" onClick={() => void refetch()} className="font-medium text-brand-ink underline">
          Try again
        </button>
      </p>
    )
  }

  return (
    <>
      {set.status === 'PARTIAL' && (
        <p className="mb-4 rounded-2xl bg-amber-50 px-4 py-3 text-sm text-amber-800">
          We could only generate {set.generatedCount} of {set.requestedCount} questions from this document.
        </p>
      )}

      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-slate-500">{questions.length} questions</p>
        <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-600">
          <input
            type="checkbox"
            checked={revealAll}
            onChange={(e) => setRevealAll(e.target.checked)}
            className="size-4 accent-[var(--color-brand-ink)]"
          />
          Show all answers
        </label>
      </div>

      <ol className="space-y-3">
        {questions.map((q) => (
          <QuestionCard key={q.id} question={q} revealAll={revealAll} />
        ))}
      </ol>
    </>
  )
}

export default function QuizSetPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { data: set, isPending, error } = useQuizSet(id)
  const remove = useDeleteQuizSet()
  const toast = useToast()

  const backLink = (
    <Link to="/quiz-sets" className={buttonClass('secondary', 'sm')}>
      ← All quiz sets
    </Link>
  )

  if (isPending) {
    return (
      <section className="mx-auto w-full max-w-2xl py-10">
        {backLink}
        <div className="mt-6 h-72 animate-pulse rounded-3xl bg-slate-50" />
      </section>
    )
  }

  if (error || !set) {
    const notFound = error instanceof ApiError && (error.status === 404 || error.status === 400)
    return (
      <section className="mx-auto w-full max-w-2xl py-10">
        {backLink}
        <p className="mt-10 text-center text-slate-500">
          {notFound ? 'This quiz set does not exist or was deleted.' : "Couldn't load this quiz set."}
        </p>
      </section>
    )
  }

  return (
    <section className="mx-auto w-full max-w-2xl py-10">
      {backLink}

      <div className="mt-6 rounded-3xl border border-slate-100 bg-white/80 p-6 shadow-sm sm:p-8">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <EditableTitle id={set.id} title={set.title} />
            <p className="mt-1 text-sm text-slate-500">
              {TYPE_LABELS[set.questionType]} · {hasQuestions(set.status) ? set.generatedCount : set.requestedCount}{' '}
              questions · {LANGUAGE_LABELS[set.language]} ·{' '}
              {formatDate(set.createdAt)} ·{' '}
              <Link to={`/documents/${set.documentId}`} className="underline decoration-slate-300 hover:text-slate-900">
                source document
              </Link>
            </p>
          </div>
          <QuizStatusBadge status={set.status} />
        </div>

        {(isGenerating(set.status) || set.status === 'FAILED') && (
          <div className="mt-8 border-t border-slate-100 pt-8">
            {set.status === 'FAILED' ? <FailedPanel set={set} /> : <GenerationProgress set={set} />}
          </div>
        )}
      </div>

      {hasQuestions(set.status) && (
        <div className="mt-6">
          <QuestionList set={set} />
        </div>
      )}

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        {hasQuestions(set.status) && <NewSetButton set={set} />}
        <div className="ml-auto">
          <DeleteButton
            label="Delete quiz set"
            size="md"
            pending={remove.isPending}
            onConfirm={() =>
              remove.mutate(set.id, {
                onSuccess: () => navigate('/quiz-sets', { replace: true }),
                onError: () => toast.show('Could not delete this quiz set.', 'error'),
              })
            }
          />
        </div>
      </div>
    </section>
  )
}
