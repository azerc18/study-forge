import { useState, type FormEvent, type ReactNode } from 'react'
import { useToast } from '../../components/toast/toast-context'
import { buttonClass } from '../../components/ui/button'
import { MAX_ESSAY_CHARS, type EssayFeedback, type Question, type Verdict } from './api.ts'
import { feedbackErrorMessage } from './labels.ts'
import { useEssayHistory, useSubmitEssay } from './queries.ts'

const timeFormat = new Intl.DateTimeFormat('en', { hour: '2-digit', minute: '2-digit', month: 'short', day: 'numeric' })
const countWords = (text: string) => (text.trim() ? text.trim().split(/\s+/).length : 0)

const VERDICT: Record<Verdict, { icon: string; label: string; className: string }> = {
  MET: { icon: '✓', label: 'Covered', className: 'bg-brand text-brand-ink' },
  PARTIAL: { icon: '~', label: 'Partly covered', className: 'bg-amber-100 text-amber-800' },
  MISSING: { icon: '✕', label: 'Missing', className: 'bg-red-100 text-red-700' },
}

function scoreColor(score: number): string {
  if (score >= 8) return 'text-brand-ink'
  if (score >= 5) return 'text-amber-700'
  return 'text-red-600'
}

function barColor(score: number): string {
  if (score >= 8) return 'bg-brand-ink'
  if (score >= 5) return 'bg-amber-500'
  return 'bg-red-500'
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">{title}</p>
      <div className="mt-1.5">{children}</div>
    </div>
  )
}

/** Đáp án mẫu + ý chính. Chỉ hiện sau khi đã nộp ít nhất 1 lần, hoặc khi bật "Show all answers". */
export function ModelAnswer({ question }: { question: Question }) {
  return (
    <div className="space-y-3 text-sm">
      {question.modelAnswer && (
        <div className="rounded-2xl bg-slate-50 px-4 py-3">
          <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">Model answer</p>
          <p className="mt-1 whitespace-pre-wrap text-slate-700">{question.modelAnswer}</p>
        </div>
      )}
      {question.rubric.length > 0 && (
        <div className="px-1">
          <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">Key points</p>
          <ul className="mt-1 list-disc space-y-1 pl-5 text-slate-600">
            {question.rubric.map((point, i) => (
              <li key={i}>{point}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function AnswerForm({
  initial,
  pending,
  onSubmit,
  onCancel,
}: {
  initial: string
  pending: boolean
  onSubmit: (answer: string) => void
  onCancel?: () => void
}) {
  const [text, setText] = useState(initial)
  const words = countWords(text)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (text.trim()) onSubmit(text.trim())
  }

  return (
    <form onSubmit={submit} className="mt-4 space-y-3">
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        disabled={pending}
        maxLength={MAX_ESSAY_CHARS}
        rows={6}
        placeholder="Write your answer here. Aim for about 150–250 words."
        aria-label="Your answer"
        className="w-full resize-y rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm leading-relaxed outline-none placeholder:text-slate-400 focus:border-brand-ink/40 focus:ring-2 focus:ring-brand disabled:bg-slate-50 disabled:text-slate-500"
      />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-slate-400">
          {words} words · {text.length.toLocaleString('en')}/{MAX_ESSAY_CHARS.toLocaleString('en')} characters
        </p>
        <div className="flex items-center gap-2">
          {onCancel && !pending && (
            <button type="button" onClick={onCancel} className={buttonClass('secondary')}>
              Cancel
            </button>
          )}
          <button type="submit" disabled={pending || !text.trim()} className={buttonClass('primary')}>
            {pending ? 'Grading…' : 'Get AI feedback'}
          </button>
        </div>
      </div>

      {pending && (
        <p className="flex items-center gap-2 text-xs text-slate-500">
          <span className="size-3 animate-spin rounded-full border-2 border-brand border-t-brand-ink" />
          The AI is comparing your answer with the key points from the material. This usually takes 5–15 seconds.
        </p>
      )}
    </form>
  )
}

function FeedbackView({ feedback, attempt }: { feedback: EssayFeedback; attempt: number }) {
  return (
    <div className="mt-4 space-y-5 rounded-2xl border border-slate-100 bg-slate-50/60 p-4 sm:p-5">
      <div className="flex items-end justify-between gap-4">
        <div>
          <p className={`text-3xl font-semibold tracking-tight ${scoreColor(feedback.score)}`}>
            {feedback.score.toFixed(1)}
            <span className="text-base font-normal text-slate-400"> / 10</span>
          </p>
          <div className="mt-2 h-1.5 w-40 overflow-hidden rounded-full bg-slate-200">
            <div className={`h-full rounded-full ${barColor(feedback.score)}`} style={{ width: `${feedback.score * 10}%` }} />
          </div>
        </div>
        <p className="text-right text-xs text-slate-400">
          Attempt {attempt}
          <br />
          {timeFormat.format(new Date(feedback.createdAt))}
        </p>
      </div>

      {feedback.summary && <p className="text-sm text-slate-700">{feedback.summary}</p>}

      <Section title="Key points">
        <ul className="space-y-2">
          {feedback.rubric.map((r) => {
            const v = VERDICT[r.verdict]
            return (
              <li key={r.point} className="flex gap-3 text-sm">
                <span
                  title={v.label}
                  aria-label={v.label}
                  className={`mt-0.5 grid size-5 shrink-0 place-items-center rounded-full text-[11px] font-bold ${v.className}`}
                >
                  {v.icon}
                </span>
                <div>
                  <p className="font-medium text-slate-800">{r.text}</p>
                  {r.comment && <p className="mt-0.5 text-slate-500">{r.comment}</p>}
                </div>
              </li>
            )
          })}
        </ul>
      </Section>

      {feedback.corrections.length > 0 && (
        <Section title="Needs correcting">
          <ul className="space-y-1.5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-800">
            {feedback.corrections.map((c, i) => (
              <li key={i}>{c}</li>
            ))}
          </ul>
        </Section>
      )}

      {feedback.suggestions.length > 0 && (
        <Section title="How to improve">
          <ul className="list-disc space-y-1 pl-5 text-sm text-slate-700">
            {feedback.suggestions.map((s, i) => (
              <li key={i}>{s}</li>
            ))}
          </ul>
        </Section>
      )}

      <details className="group text-sm">
        <summary className="cursor-pointer font-medium text-slate-600 hover:text-slate-900">Your answer</summary>
        <p className="mt-2 whitespace-pre-wrap text-slate-600">{feedback.answer}</p>
      </details>

      {feedback.improvedAnswer && (
        <details className="group text-sm">
          <summary className="cursor-pointer font-medium text-slate-600 hover:text-slate-900">
            Suggested rewrite of your answer
          </summary>
          <p className="mt-2 rounded-xl bg-white px-4 py-3 whitespace-pre-wrap text-slate-700">{feedback.improvedAnswer}</p>
        </details>
      )}
    </div>
  )
}

/**
 * Câu tự luận: viết câu trả lời → AI chấm theo ý chính → xem nhận xét → sửa và nộp lại.
 * Đáp án mẫu ẩn cho tới lần nộp đầu tiên (hoặc khi bật "Show all answers").
 */
export default function EssayPractice({ question, revealAll }: { question: Question; revealAll: boolean }) {
  const history = useEssayHistory(question.id)
  const submit = useSubmitEssay(question.id)
  const toast = useToast()

  const [selectedId, setSelectedId] = useState<string | null>(null) // null = lần nộp mới nhất
  const [editing, setEditing] = useState<{ initial: string } | null>(null)
  const [showModel, setShowModel] = useState(false)

  if (history.isPending) {
    return <div className="mt-4 h-32 animate-pulse rounded-2xl bg-slate-50" />
  }

  const attempts = history.data ?? []
  const index = Math.max(0, attempts.findIndex((f) => f.id === selectedId))
  const current = attempts[index]
  const showForm = editing != null || attempts.length === 0

  function handleSubmit(answer: string) {
    submit.mutate(answer, {
      onSuccess: (fb) => {
        setEditing(null)
        setSelectedId(fb.id)
      },
      onError: (err) => toast.show(feedbackErrorMessage(err), 'error'),
    })
  }

  return (
    <div>
      {showForm ? (
        <AnswerForm
          key={editing?.initial ?? 'new'}
          initial={editing?.initial ?? ''}
          pending={submit.isPending}
          onSubmit={handleSubmit}
          onCancel={attempts.length > 0 ? () => setEditing(null) : undefined}
        />
      ) : (
        current && (
          <>
            <FeedbackView feedback={current} attempt={attempts.length - index} />

            <div className="mt-4 flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => setEditing({ initial: current.answer })}
                className={buttonClass('primary')}
              >
                Edit and resubmit
              </button>
              <button type="button" onClick={() => setShowModel((v) => !v)} className={buttonClass('secondary')}>
                {showModel ? 'Hide model answer' : 'Show model answer'}
              </button>
            </div>

            {attempts.length > 1 && (
              <div className="mt-4">
                <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">Your attempts</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {attempts.map((f, i) => (
                    <button
                      key={f.id}
                      type="button"
                      aria-pressed={i === index}
                      onClick={() => setSelectedId(f.id)}
                      className={`rounded-full border px-3 py-1 text-xs transition ${
                        i === index
                          ? 'border-brand-ink bg-brand font-medium text-brand-ink'
                          : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300'
                      }`}
                    >
                      #{attempts.length - i} · {f.score.toFixed(1)}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </>
        )
      )}

      {(revealAll || (showModel && attempts.length > 0)) && (
        <div className="mt-4">
          <ModelAnswer question={question} />
        </div>
      )}
    </div>
  )
}
