import { useState, type FormEvent } from 'react'
import { useToast } from '../../components/toast/toast-context'
import { useRenameQuizSet } from './queries'
import { buttonClass } from '../../components/ui/button.ts'

/** Tiêu đề bộ đề; bấm "Rename" để sửa ngay tại chỗ. Enter = lưu, Esc = huỷ. */
export default function EditableTitle({ id, title }: { id: string; title: string }) {
  const [draft, setDraft] = useState<string | null>(null)
  const rename = useRenameQuizSet()
  const toast = useToast()

  function submit(e: FormEvent) {
    e.preventDefault()
    const value = draft?.trim() ?? ''
    if (!value || value === title) {
      setDraft(null)
      return
    }
    rename.mutate(
      { id, title: value },
      {
        onSuccess: () => setDraft(null),
        onError: () => toast.show('Could not rename this quiz set.', 'error'),
      },
    )
  }

  if (draft == null) {
    return (
      <div className="flex min-w-0 items-center gap-3">
        <h1 className="line-clamp-2 min-w-0 text-xl font-semibold tracking-tight break-words">{title}</h1>
        <button
          type="button"
          onClick={() => setDraft(title)}
          className={buttonClass('secondary', 'sm')}
        >
          Rename
        </button>
      </div>
    )
  }

  return (
    <form onSubmit={submit} className="flex flex-wrap items-center gap-2">
      <input
        autoFocus
        value={draft}
        maxLength={200}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => e.key === 'Escape' && setDraft(null)}
        aria-label="Quiz set title"
        className="min-w-0 flex-1 rounded-xl border border-slate-200 px-3 py-2 text-base font-medium outline-none focus:border-brand-ink/40 focus:ring-2 focus:ring-brand"
      />
      <button
        type="submit"
        disabled={rename.isPending}
        className="rounded-full bg-brand px-4 py-2 text-sm font-medium text-brand-ink transition hover:bg-brand-strong disabled:opacity-60"
      >
        {rename.isPending ? 'Saving…' : 'Save'}
      </button>
      <button
        type="button"
        onClick={() => setDraft(null)}
        className="rounded-full px-3 py-2 text-sm text-slate-500 transition hover:bg-slate-100"
      >
        Cancel
      </button>
    </form>
  )
}
