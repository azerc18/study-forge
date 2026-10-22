import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../lib/api'
import {
  isProcessing,
  type DocumentItem,
  type GenerationOptions,
  type OutputLanguage,
  type QuestionType,
} from '../features/documents/api'
import DeleteButton from '../features/documents/DeleteButton'
import StatusBadge from '../features/documents/StatusBadge'
import { formatBytes, formatDate, formatNumber } from '../features/documents/format'
import { useDeleteDocument, useDocument, useGenerationOptions } from '../features/documents/queries'

const TYPE_LABELS: Record<QuestionType, string> = {
  MCQ: 'Multiple choice',
  ESSAY: 'Essay',
  MIXED: 'Mixed',
}

const LANGUAGE_LABELS: Record<OutputLanguage, string> = {
  EN: 'English',
  VI: 'Tiếng Việt',
}

/** Nút chọn dạng "viên thuốc", dùng cho cả 3 nhóm lựa chọn. */
function Choice({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={`rounded-full border px-4 py-2 text-sm transition ${
        selected
          ? 'border-brand-ink bg-brand font-medium text-brand-ink'
          : 'border-slate-200 text-slate-600 hover:border-slate-300'
      }`}
    >
      {children}
    </button>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">{label}</p>
      <div className="mt-2 flex flex-wrap gap-2">{children}</div>
    </div>
  )
}

function PracticeSetForm({ options }: { options: GenerationOptions }) {
  const [type, setType] = useState<QuestionType>('MCQ')
  const [count, setCount] = useState<number | null>(null)
  const [language, setLanguage] = useState<OutputLanguage>('EN')

  const counts = options.questionCounts[type]
  // Đổi dạng câu hỏi mà số câu cũ không còn hợp lệ thì tự lấy lựa chọn đầu tiên (tính luôn khi render, không cần effect)
  const selectedCount = count != null && counts.includes(count) ? count : counts[0]

  return (
    <div className="space-y-6">
      <Field label="Question type">
        {(Object.keys(TYPE_LABELS) as QuestionType[]).map((t) => (
          <Choice key={t} selected={t === type} onClick={() => setType(t)}>
            {TYPE_LABELS[t]}
          </Choice>
        ))}
      </Field>

      <Field label="Number of questions">
        {counts.map((n) => (
          <Choice key={n} selected={n === selectedCount} onClick={() => setCount(n)}>
            {n}
          </Choice>
        ))}
      </Field>

      <Field label="Language">
        {options.languages.map((l) => (
          <Choice key={l} selected={l === language} onClick={() => setLanguage(l)}>
            {LANGUAGE_LABELS[l]}
          </Choice>
        ))}
      </Field>

      {options.shortDocument && (
        <p className="rounded-2xl bg-slate-50 px-4 py-3 text-sm text-slate-600">
          This document is short ({formatNumber(options.wordCount)} words), so a set can have at most 10 questions.
        </p>
      )}

      <div className="flex flex-col items-start gap-2 pt-2 sm:flex-row sm:items-center sm:gap-4">
        <button
          type="button"
          disabled
          className="cursor-not-allowed rounded-full bg-brand px-6 py-3 font-medium text-brand-ink opacity-60"
        >
          Generate {selectedCount} questions
        </button>
        <span className="text-xs text-slate-400">Question generation is coming soon.</span>
      </div>
    </div>
  )
}

function ReadyPanel({ doc }: { doc: DocumentItem }) {
  const options = useGenerationOptions(doc.id, true)

  if (options.isPending) {
    return <div className="h-64 animate-pulse rounded-2xl bg-slate-50" />
  }
  if (options.isError) {
    return <p className="text-sm text-red-600">Couldn't load the options for this document.</p>
  }
  return <PracticeSetForm options={options.data} />
}

function StatusPanel({ doc }: { doc: DocumentItem }) {
  if (isProcessing(doc.status)) {
    return (
      <div className="flex flex-col items-center py-10 text-center">
        <div className="size-7 animate-spin rounded-full border-2 border-brand border-t-brand-ink" />
        <p className="mt-4 font-medium">Reading your document…</p>
        <p className="mt-1 text-sm text-slate-500">This usually takes a few seconds. You can leave this page.</p>
      </div>
    )
  }

  if (doc.status === 'FAILED') {
    return (
      <div className="rounded-2xl bg-red-50 px-5 py-4">
        <p className="font-medium text-red-700">We couldn't read this file.</p>
        <p className="mt-1 text-sm text-red-600">{doc.errorMessage ?? 'Please try a different file.'}</p>
      </div>
    )
  }

  return <ReadyPanel doc={doc} />
}

export default function DocumentDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { data: doc, isPending, error } = useDocument(id)
  const remove = useDeleteDocument()

  const backLink = (
    <Link to="/documents" className="text-sm text-slate-500 transition hover:text-slate-900">
      ← All documents
    </Link>
  )

  if (isPending) {
    return (
      <section className="mx-auto w-full max-w-2xl py-10">
        {backLink}
        <div className="mt-6 h-80 animate-pulse rounded-3xl bg-slate-50" />
      </section>
    )
  }

  if (error || !doc) {
    const notFound = error instanceof ApiError && (error.status === 404 || error.status === 400)
    return (
      <section className="mx-auto w-full max-w-2xl py-10">
        {backLink}
        <p className="mt-10 text-center text-slate-500">
          {notFound ? 'This document does not exist or was deleted.' : "Couldn't load this document."}
        </p>
      </section>
    )
  }

  return (
    <section className="mx-auto w-full max-w-2xl py-10">
      {backLink}

      <div className="mt-6 rounded-3xl border border-slate-100 bg-white/80 p-6 shadow-sm sm:p-8">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h1 className="line-clamp-2 text-xl font-semibold tracking-tight break-words sm:truncate">{doc.originalFilename}</h1>
            <p className="mt-1 text-sm text-slate-500">
              {doc.fileType} · {formatBytes(doc.fileSizeBytes)} · {formatDate(doc.createdAt)}
              {doc.pageCount != null && doc.wordCount != null && (
                <>
                  {' '}· {doc.pageCount} {doc.fileType === 'PPTX' ? 'slides' : 'pages'} · {formatNumber(doc.wordCount)}{' '}
                  words
                </>
              )}
            </p>
          </div>
          <StatusBadge status={doc.status} />
        </div>

        <div className="mt-8 border-t border-slate-100 pt-8">
          <StatusPanel doc={doc} />
        </div>
      </div>

      <div className="mt-4 flex justify-end">
        <DeleteButton
          label="Delete document"
          pending={remove.isPending}
          onConfirm={() => remove.mutate(doc.id, { onSuccess: () => navigate('/documents', { replace: true }) })}
        />
      </div>
    </section>
  )
}
