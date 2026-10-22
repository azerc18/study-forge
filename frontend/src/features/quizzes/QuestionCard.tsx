import { useState } from 'react'
import type { Question, QuestionOption } from './api'
import { buttonClass } from '../../components/ui/button.ts'

function SourcePages({ pages }: { pages: number[] }) {
  if (pages.length === 0) return null
  return (
    <span className="shrink-0 rounded-full bg-slate-50 px-2 py-0.5 text-[11px] text-slate-500" title="Where this comes from">
      {pages.length === 1 ? 'Page' : 'Pages'} {pages.join(', ')}
    </span>
  )
}

function optionClass(option: QuestionOption, revealed: boolean, picked: string | null): string {
  const base = 'flex w-full items-start gap-3 rounded-2xl border px-4 py-3 text-left text-sm transition'
  if (!revealed) return `${base} border-slate-200 hover:border-slate-300 hover:bg-slate-50`
  if (option.correct) return `${base} border-brand-ink/30 bg-brand text-brand-ink`
  if (picked === option.label) return `${base} border-red-200 bg-red-50 text-red-700`
  return `${base} border-slate-100 text-slate-400`
}

function McqBody({ question, revealAll }: { question: Question; revealAll: boolean }) {
  const [picked, setPicked] = useState<string | null>(null)
  const revealed = revealAll || picked != null
  const pickedOption = question.options.find((o) => o.label === picked)

  return (
    <>
      <ul className="mt-4 space-y-2">
        {question.options.map((o) => (
          <li key={o.label}>
            <button
              type="button"
              disabled={revealed}
              onClick={() => setPicked(o.label)}
              aria-pressed={picked === o.label}
              className={`${optionClass(o, revealed, picked)} disabled:cursor-default`}
            >
              <span className="font-semibold">{o.label}</span>
              <span className="flex-1 whitespace-pre-wrap">{o.content}</span>
              {revealed && o.correct && <span aria-label="Correct answer">✓</span>}
            </button>
          </li>
        ))}
      </ul>

      {revealed && (
        <div className="mt-3 rounded-2xl bg-slate-50 px-4 py-3 text-sm text-slate-600">
          {pickedOption && (
            <p className={`mb-1 font-medium ${pickedOption.correct ? 'text-brand-ink' : 'text-red-700'}`}>
              {pickedOption.correct ? 'Correct!' : 'Not quite.'}
            </p>
          )}
          {question.explanation && <p className="whitespace-pre-wrap">{question.explanation}</p>}
        </div>
      )}
    </>
  )
}

function EssayBody({ question, revealAll }: { question: Question; revealAll: boolean }) {
  const [shown, setShown] = useState(false)
  const revealed = revealAll || shown

  if (!revealed) {
    return (
      <button
        type="button"
        onClick={() => setShown(true)}
        className={`mt-4 ${buttonClass('secondary')}`}
      >
        Show model answer
      </button>
    )
  }

  return (
    <div className="mt-4 space-y-3 text-sm">
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

/** 1 câu hỏi. MCQ: bấm đáp án để tự kiểm tra; Essay: bấm để xem đáp án mẫu. */
export default function QuestionCard({ question, revealAll }: { question: Question; revealAll: boolean }) {
  return (
    <li className="rounded-3xl border border-slate-100 bg-white/80 p-5 shadow-sm sm:p-6">
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-medium text-slate-400">
          {question.orderIndex}. {question.type === 'MCQ' ? 'Multiple choice' : 'Essay'}
        </p>
        <SourcePages pages={question.sourcePages} />
      </div>
      <p className="mt-2 font-medium whitespace-pre-wrap">{question.content}</p>

      {question.type === 'MCQ' ? (
        <McqBody question={question} revealAll={revealAll} />
      ) : (
        <EssayBody question={question} revealAll={revealAll} />
      )}
    </li>
  )
}
