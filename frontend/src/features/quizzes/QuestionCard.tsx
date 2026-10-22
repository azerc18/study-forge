import { useState } from 'react'
import type { Question, QuestionOption } from './api.ts'
import EssayPractice from './EssayPractice.tsx'

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

/** 1 câu hỏi. MCQ: bấm đáp án để tự kiểm tra; Essay: viết câu trả lời và nhận nhận xét của AI. */
export default function QuestionCard({ question, revealAll }: { question: Question; revealAll: boolean }) {
  return (
    <li className="rounded-3xl border border-slate-100 bg-white/80 p-5 shadow-sm sm:p-6">
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-medium text-slate-400">
          {question.orderIndex}. {question.type === 'MCQ' ? 'Multiple choice' : 'Essay'}
          {question.topic && <span className="font-normal"> · {question.topic}</span>}
        </p>
        <SourcePages pages={question.sourcePages} />
      </div>
      <p className="mt-2 font-medium whitespace-pre-wrap">{question.content}</p>

      {question.type === 'MCQ' ? (
        <McqBody question={question} revealAll={revealAll} />
      ) : (
        <EssayPractice question={question} revealAll={revealAll} />
      )}
    </li>
  )
}
