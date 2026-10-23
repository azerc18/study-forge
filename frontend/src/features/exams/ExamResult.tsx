import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useToast } from '../../components/toast/toast-context'
import { buttonClass } from '../../components/ui/button'
import type { Exam, ExamQuestion, ExamOption } from './api.ts'
import { barColor, examErrorMessage, formatDuration, scoreColor } from './format.ts'
import { useStartExam } from './queries.ts'

const WEAK = 0.6
const dateFormat = new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })

function ScoreCard({ exam }: { exam: Exam }) {
  const score = exam.score ?? 0
  return (
    <div className="flex flex-wrap items-end justify-between gap-6">
      <div>
        <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">Your score</p>
        <p className={`mt-1 text-5xl font-semibold tracking-tight tabular-nums ${scoreColor(score)}`}>
          {score.toFixed(1)}
          <span className="text-xl font-normal text-slate-400"> / 10</span>
        </p>
        <p className="mt-2 text-sm text-slate-600">
          <span className="font-medium">{exam.correctCount}</span> of {exam.total} correct
        </p>
      </div>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-1 text-sm">
        <dt className="text-slate-400">Time</dt>
        <dd className="text-right text-slate-700 tabular-nums">
          {exam.durationSeconds != null ? formatDuration(exam.durationSeconds) : '—'}
          {exam.timeLimitSeconds != null && (
            <span className="text-slate-400"> / {Math.round(exam.timeLimitSeconds / 60)}m</span>
          )}
        </dd>
        <dt className="text-slate-400">Finished</dt>
        <dd className="text-right text-slate-700">
          {exam.submittedAt ? dateFormat.format(new Date(exam.submittedAt)) : '—'}
        </dd>
      </dl>
    </div>
  )
}

function Topics({ exam }: { exam: Exam }) {
  if (exam.topics.length === 0) return null
  const weak = exam.topics.filter((t) => t.correct / t.total < WEAK)

  return (
    <div className="mt-8 border-t border-slate-100 pt-6">
      <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">By topic</p>
      {weak.length > 0 && (
        <p className="mt-1 text-sm text-slate-600">
          Review first: <span className="font-medium text-red-700">{weak.map((t) => t.topic).join(', ')}</span>
        </p>
      )}
      <ul className="mt-4 space-y-3">
        {exam.topics.map((t) => {
          const ratio = t.correct / t.total
          return (
            <li key={t.topic} className="grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-1.5">
              <p className="min-w-0 truncate text-sm text-slate-700">
                {t.topic}
                {ratio < WEAK && (
                  <span className="ml-2 rounded-full bg-red-50 px-2 py-0.5 text-[11px] font-medium text-red-700">
                    Needs review
                  </span>
                )}
              </p>
              <p className="text-sm text-slate-500 tabular-nums">
                {t.correct}/{t.total}
              </p>
              <div className="col-span-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                <div
                  className={`h-full rounded-full ${barColor(ratio)}`}
                  style={{ width: `${Math.max(ratio * 100, 2)}%` }}
                />
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

function optionClass(o: ExamOption, q: ExamQuestion): string {
  const base = 'flex items-start gap-3 rounded-2xl border px-4 py-3 text-sm'
  if (o.label === q.correctLabel) return `${base} border-brand-ink/30 bg-brand text-brand-ink`
  if (o.label === q.selectedLabel) return `${base} border-red-200 bg-red-50 text-red-700`
  return `${base} border-slate-100 text-slate-400`
}

function ReviewCard({ q }: { q: ExamQuestion }) {
  const status = q.selectedLabel == null ? 'Skipped' : q.correct ? 'Correct' : 'Wrong'
  const badge =
    status === 'Correct'
      ? 'bg-brand text-brand-ink'
      : status === 'Wrong'
        ? 'bg-red-50 text-red-700'
        : 'bg-slate-100 text-slate-500'

  return (
    <li className="rounded-3xl border border-slate-100 bg-white/80 p-5 shadow-sm sm:p-6">
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-medium text-slate-400">
          Question {q.number}
          {q.topic && <span className="font-normal"> · {q.topic}</span>}
        </p>
        <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${badge}`}>{status}</span>
      </div>
      <p className="mt-2 font-medium whitespace-pre-wrap">{q.content}</p>
      <ul className="mt-4 space-y-2">
        {q.options.map((o) => (
          <li key={o.label} className={optionClass(o, q)}>
            <span className="font-semibold">{o.label}</span>
            <span className="flex-1 whitespace-pre-wrap">{o.content}</span>
            {o.label === q.correctLabel && <span aria-label="Correct answer">✓</span>}
            {o.label === q.selectedLabel && o.label !== q.correctLabel && (
              <span className="text-xs">Your answer</span>
            )}
          </li>
        ))}
      </ul>
      {q.explanation && (
        <p className="mt-3 rounded-2xl bg-slate-50 px-4 py-3 text-sm whitespace-pre-wrap text-slate-600">
          {q.explanation}
        </p>
      )}
    </li>
  )
}

/** Kết quả sau khi nộp: điểm, chủ đề yếu, xem lại từng câu. */
export default function ExamResult({ exam }: { exam: Exam }) {
  const [onlyMistakes, setOnlyMistakes] = useState(false)
  const retake = useStartExam(exam.quizSetId)
  const navigate = useNavigate()
  const toast = useToast()

  const mistakes = exam.questions.filter((q) => !q.correct)
  const shown = onlyMistakes ? mistakes : exam.questions

  function again() {
    retake.mutate(
      { timeLimitMinutes: exam.timeLimitSeconds ? Math.round(exam.timeLimitSeconds / 60) : null, shuffle: true },
      {
        onSuccess: (next) => navigate(`/exams/${next.id}`),
        onError: (err) => toast.show(examErrorMessage(err), 'error'),
      },
    )
  }

  return (
    <>
      <div className="mt-6 rounded-3xl border border-slate-100 bg-white/80 p-6 shadow-sm sm:p-8">
        <ScoreCard exam={exam} />
        <Topics exam={exam} />
        <div className="mt-8 flex flex-wrap gap-2 border-t border-slate-100 pt-6">
          <button type="button" disabled={retake.isPending} onClick={again} className={buttonClass('primary')}>
            {retake.isPending ? 'Starting…' : '↻ Retake exam'}
          </button>
          <Link to={`/quiz-sets/${exam.quizSetId}`} className={buttonClass('secondary')}>
            Practice this set
          </Link>
        </div>
      </div>

      <div className="mt-8 mb-4 flex items-center justify-between gap-3">
        <h2 className="font-semibold">Review</h2>
        <div className="flex rounded-full border border-slate-200 bg-white p-0.5 text-sm">
          {[
            { value: false, label: `All (${exam.questions.length})` },
            { value: true, label: `Mistakes (${mistakes.length})` },
          ].map((tab) => (
            <button
              key={tab.label}
              type="button"
              aria-pressed={onlyMistakes === tab.value}
              onClick={() => setOnlyMistakes(tab.value)}
              className={`rounded-full px-3 py-1 transition ${
                onlyMistakes === tab.value ? 'bg-brand font-medium text-brand-ink' : 'text-slate-500 hover:text-slate-800'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {shown.length === 0 ? (
        <p className="rounded-3xl bg-brand/40 px-6 py-10 text-center text-brand-ink">No mistakes. Well done!</p>
      ) : (
        <ol className="space-y-3">
          {shown.map((q) => (
            <ReviewCard key={q.id} q={q} />
          ))}
        </ol>
      )}
    </>
  )
}
