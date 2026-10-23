import { useCallback, useEffect, useRef, useState } from 'react'
import { useToast } from '../../components/toast/toast-context'
import { buttonClass } from '../../components/ui/button'
import type { Answers, Exam, OptionLabel } from './api.ts'
import { loadDraft, saveDraft } from './draft.ts'
import { examErrorMessage, formatClock } from './format.ts'
import { useSubmitExam } from './queries.ts'

/** Số giây còn lại, cập nhật mỗi giây. null = không giới hạn. */
function useRemaining(deadline: string | null): number | null {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!deadline) return
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [deadline])
  return deadline ? Math.max(0, (new Date(deadline).getTime() - now) / 1000) : null
}

function Timer({ remaining }: { remaining: number | null }) {
  if (remaining === null) return <span className="text-sm text-slate-400">No time limit</span>
  const urgent = remaining <= 60
  return (
    <span
      role="timer"
      aria-label="Time left"
      className={`rounded-full px-3 py-1 text-sm font-semibold tabular-nums ${
        urgent ? 'animate-pulse bg-red-50 text-red-600' : 'bg-slate-100 text-slate-700'
      }`}
    >
      {formatClock(remaining)}
    </span>
  )
}

function ConfirmSubmit({
  unanswered,
  pending,
  onConfirm,
  onCancel,
}: {
  unanswered: number
  pending: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  return (
    <div className="fixed inset-0 z-30 flex items-end justify-center bg-slate-900/30 p-4 sm:items-center">
      <div role="dialog" aria-modal="true" className="w-full max-w-sm rounded-3xl bg-white p-6 shadow-xl">
        <p className="text-lg font-semibold">Submit your exam?</p>
        <p className="mt-2 text-sm text-slate-600">
          {unanswered > 0
            ? `${unanswered} ${unanswered === 1 ? 'question is' : 'questions are'} still unanswered and will count as wrong.`
            : 'You have answered every question.'}{' '}
          You can't change your answers after submitting.
        </p>
        <div className="mt-6 flex justify-end gap-2">
          <button type="button" disabled={pending} onClick={onCancel} className={buttonClass('secondary')}>
            Keep working
          </button>
          <button type="button" disabled={pending} onClick={onConfirm} className={buttonClass('primary')}>
            {pending ? 'Submitting…' : 'Submit'}
          </button>
        </div>
      </div>
    </div>
  )
}

/** Màn làm bài: chọn đáp án (lưu tạm trong trình duyệt), đồng hồ đếm ngược, hết giờ tự nộp. */
export default function ExamTaking({ exam }: { exam: Exam }) {
  const [answers, setAnswers] = useState<Answers>(() => loadDraft(exam.id))
  const [confirming, setConfirming] = useState(false)
  const submit = useSubmitExam(exam)
  const toast = useToast()
  const remaining = useRemaining(exam.deadline)
  const autoSubmitted = useRef(false)

  const answered = exam.questions.filter((q) => answers[q.id]).length
  const unanswered = exam.total - answered

  const send = useCallback(
    (current: Answers) => {
      submit.mutate(current, {
        onError: (err) => {
          autoSubmitted.current = false // cho phép thử lại
          toast.show(examErrorMessage(err), 'error')
        },
      })
    },
    [submit, toast],
  )

  // Hết giờ → tự nộp đúng 1 lần (kể cả khi mở lại trang sau khi đã quá hạn)
  useEffect(() => {
    if (remaining === 0 && !autoSubmitted.current && !submit.isPending) {
      autoSubmitted.current = true
      toast.show("Time's up! Submitting your answers…", 'info')
      send(answers)
    }
  }, [remaining, answers, send, submit.isPending, toast])

  function choose(questionId: string, label: OptionLabel) {
    setAnswers((prev) => {
      const next = { ...prev }
      if (next[questionId] === label) delete next[questionId] // bấm lại để bỏ chọn
      else next[questionId] = label
      saveDraft(exam.id, next)
      return next
    })
  }

  function jumpTo(number: number) {
    document.getElementById(`q-${number}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  return (
    <>
      {/* Thanh trên cùng: luôn hiện khi cuộn */}
      <div className="sticky top-0 z-20 -mx-4 border-b border-slate-100 bg-white/90 px-4 py-3 backdrop-blur sm:mx-0 sm:rounded-b-2xl">
        <div className="flex items-center gap-3">
          <Timer remaining={remaining} />
          <p className="text-sm text-slate-500">
            <span className="font-medium text-slate-800">{answered}</span>/{exam.total} answered
          </p>
          <button
            type="button"
            disabled={submit.isPending}
            onClick={() => setConfirming(true)}
            className={`ml-auto ${buttonClass('primary')}`}
          >
            {submit.isPending ? 'Submitting…' : 'Submit'}
          </button>
        </div>
        <div className="mt-2 h-1 overflow-hidden rounded-full bg-slate-100">
          <div
            className="h-full rounded-full bg-brand-ink transition-[width] duration-300"
            style={{ width: `${(answered / exam.total) * 100}%` }}
          />
        </div>
      </div>

      {/* Lưới số câu: bấm để nhảy tới câu đó */}
      <nav aria-label="Questions" className="mt-5 flex flex-wrap gap-1.5">
        {exam.questions.map((q) => (
          <button
            key={q.id}
            type="button"
            onClick={() => jumpTo(q.number)}
            aria-label={`Question ${q.number}${answers[q.id] ? ', answered' : ''}`}
            className={`size-8 rounded-lg text-xs font-medium tabular-nums transition ${
              answers[q.id]
                ? 'bg-brand-ink text-white'
                : 'border border-slate-200 bg-white text-slate-500 hover:border-slate-300'
            }`}
          >
            {q.number}
          </button>
        ))}
      </nav>

      <ol className="mt-5 space-y-3">
        {exam.questions.map((q) => (
          <li
            key={q.id}
            id={`q-${q.number}`}
            className="scroll-mt-28 rounded-3xl border border-slate-100 bg-white/80 p-5 shadow-sm sm:p-6"
          >
            <p className="text-xs font-medium text-slate-400">Question {q.number}</p>
            <p className="mt-2 font-medium whitespace-pre-wrap">{q.content}</p>
            <ul className="mt-4 space-y-2">
              {q.options.map((o) => {
                const picked = answers[q.id] === o.label
                return (
                  <li key={o.label}>
                    <button
                      type="button"
                      aria-pressed={picked}
                      disabled={submit.isPending}
                      onClick={() => choose(q.id, o.label)}
                      className={`flex w-full items-start gap-3 rounded-2xl border px-4 py-3 text-left text-sm transition ${
                        picked
                          ? 'border-brand-ink bg-brand text-brand-ink'
                          : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50'
                      }`}
                    >
                      <span
                        className={`grid size-6 shrink-0 place-items-center rounded-full text-xs font-semibold ${
                          picked ? 'bg-brand-ink text-white' : 'bg-slate-100 text-slate-500'
                        }`}
                      >
                        {o.label}
                      </span>
                      <span className="flex-1 pt-0.5 whitespace-pre-wrap">{o.content}</span>
                    </button>
                  </li>
                )
              })}
            </ul>
          </li>
        ))}
      </ol>

      <div className="mt-6 flex flex-col items-center gap-2 text-center">
        <button
          type="button"
          disabled={submit.isPending}
          onClick={() => setConfirming(true)}
          className="rounded-full bg-brand px-8 py-3 font-medium text-brand-ink transition hover:bg-brand-strong disabled:opacity-60"
        >
          Submit exam
        </button>
        <p className="text-xs text-slate-400">Your answers are saved on this device until you submit.</p>
      </div>

      {confirming && (
        <ConfirmSubmit
          unanswered={unanswered}
          pending={submit.isPending}
          onCancel={() => setConfirming(false)}
          onConfirm={() => send(answers)}
        />
      )}
    </>
  )
}
