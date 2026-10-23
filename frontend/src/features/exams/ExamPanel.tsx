import { useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { useToast } from '../../components/toast/toast-context'
import { buttonClass } from '../../components/ui/button'
import type { QuizSet } from '../quizzes/api'
import { useQuestions } from '../quizzes/queries'
import { examErrorMessage, formatDuration, scoreColor } from './format.ts'
import { useExamHistory, useStartExam } from './queries.ts'

const dateFormat = new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })

function Choice({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={`rounded-full border px-3.5 py-1.5 text-sm transition ${
        selected
          ? 'border-brand-ink bg-brand font-medium text-brand-ink'
          : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300'
      }`}
    >
      {children}
    </button>
  )
}

function History({ quizSetId }: { quizSetId: string }) {
  const { data } = useExamHistory(quizSetId)
  const finished = data?.filter((e) => e.status === 'SUBMITTED') ?? []
  if (finished.length === 0) return null

  const best = Math.max(...finished.map((e) => e.score ?? 0))

  return (
    <div className="mt-6 border-t border-slate-100 pt-5">
      <div className="flex items-baseline justify-between">
        <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">Past attempts</p>
        <p className="text-xs text-slate-500">
          Best <span className="font-semibold text-slate-700">{best.toFixed(1)}</span>
        </p>
      </div>
      <ul className="mt-2 divide-y divide-slate-100">
        {finished.slice(0, 5).map((e) => (
          <li key={e.id}>
            <Link
              to={`/exams/${e.id}`}
              className="-mx-2 flex items-center gap-3 rounded-xl px-2 py-2.5 text-sm transition hover:bg-slate-50"
            >
              <span className={`w-10 font-semibold tabular-nums ${scoreColor(e.score ?? 0)}`}>
                {(e.score ?? 0).toFixed(1)}
              </span>
              <span className="text-slate-600">
                {e.correctCount}/{e.total} correct
              </span>
              <span className="ml-auto text-xs text-slate-400">
                {e.durationSeconds != null && `${formatDuration(e.durationSeconds)} · `}
                {dateFormat.format(new Date(e.startedAt))}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Khung "Take an exam" trên trang bộ đề: chọn thời gian, trộn câu, bắt đầu / làm tiếp; kèm lịch sử. */
export default function ExamPanel({ set }: { set: QuizSet }) {
  const questions = useQuestions(set.id, true) // dùng chung cache với danh sách câu hỏi bên dưới
  const history = useExamHistory(set.id)
  const start = useStartExam(set.id)
  const navigate = useNavigate()
  const toast = useToast()

  const mcqCount = questions.data?.filter((q) => q.type === 'MCQ').length ?? 0
  const limits = [null, mcqCount, Math.ceil(mcqCount * 1.5)].filter(
    (v, i, all) => v === null || (v > 0 && v <= 180 && all.indexOf(v) === i),
  )
  const [limit, setLimit] = useState<number | null | undefined>(undefined) // undefined = chưa chọn → mặc định
  const [shuffle, setShuffle] = useState(true)
  const selectedLimit = limit === undefined ? (limits[1] ?? null) : limit

  const open = history.data?.find((e) => e.status === 'IN_PROGRESS')

  if (questions.isPending || mcqCount === 0) return null

  function begin() {
    start.mutate(
      { timeLimitMinutes: selectedLimit, shuffle },
      {
        onSuccess: (exam) => navigate(`/exams/${exam.id}`),
        onError: (err) => toast.show(examErrorMessage(err), 'error'),
      },
    )
  }

  return (
    <section className="mt-6 rounded-3xl border border-slate-100 bg-white/80 p-6 shadow-sm sm:p-8">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold tracking-tight">Exam mode</h2>
          <p className="mt-1 text-sm text-slate-500">
            {mcqCount} multiple-choice questions · answers are hidden until you submit
            {set.questionType === 'MIXED' && ' · essay questions are not included'}
          </p>
        </div>
      </div>

      {open ? (
        <div className="mt-5 flex flex-wrap items-center gap-3 rounded-2xl bg-brand/40 px-4 py-3">
          <p className="mr-auto text-sm text-brand-ink">
            You have an exam in progress, started {dateFormat.format(new Date(open.startedAt))}.
          </p>
          <Link to={`/exams/${open.id}`} className={buttonClass('primary')}>
            Continue exam
          </Link>
        </div>
      ) : (
        <div className="mt-5 space-y-4">
          <div>
            <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">Time limit</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {limits.map((v) => (
                <Choice key={v ?? 'none'} selected={v === selectedLimit} onClick={() => setLimit(v)}>
                  {v === null ? 'No limit' : `${v} min`}
                </Choice>
              ))}
            </div>
          </div>

          <label className="flex w-fit cursor-pointer items-center gap-2 text-sm text-slate-600">
            <input
              type="checkbox"
              checked={shuffle}
              onChange={(e) => setShuffle(e.target.checked)}
              className="size-4 accent-[var(--color-brand-ink)]"
            />
            Shuffle question order
          </label>

          <button
            type="button"
            disabled={start.isPending}
            onClick={begin}
            className="rounded-full bg-brand px-6 py-3 font-medium text-brand-ink transition hover:bg-brand-strong disabled:cursor-wait disabled:opacity-60"
          >
            {start.isPending ? 'Starting…' : 'Start exam'}
          </button>
        </div>
      )}

      <History quizSetId={set.id} />
    </section>
  )
}
