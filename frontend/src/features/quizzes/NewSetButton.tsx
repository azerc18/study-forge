import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useToast } from '../../components/toast/toast-context'
import { buttonClass } from '../../components/ui/button'
import type { QuizSet } from './api.ts'
import { createErrorMessage } from './labels'
import { useCreateQuizSet, useQuota } from './queries.ts'

/**
 * E4 — "Tạo bộ khác": tạo bộ mới cùng tài liệu, cùng dạng/số câu/ngôn ngữ.
 * Backend tự tránh lặp câu của các bộ cũ trên tài liệu này. Tốn 1 lượt/ngày nên phải bấm xác nhận.
 */
export default function NewSetButton({ set }: { set: QuizSet }) {
  const [confirming, setConfirming] = useState(false)
  const create = useCreateQuizSet()
  const quota = useQuota()
  const navigate = useNavigate()
  const toast = useToast()

  const noneLeft = quota.data != null && quota.data.remaining <= 0

  function generate() {
    create.mutate(
      {
        documentId: set.documentId,
        questionType: set.questionType,
        count: set.requestedCount,
        language: set.language,
      },
      {
        onSuccess: (created) => {
          setConfirming(false)
          navigate(`/quiz-sets/${created.id}`)
        },
        onError: (err) => toast.show(createErrorMessage(err), 'error'),
      },
    )
  }

  if (!confirming) {
    return (
      <button
        type="button"
        disabled={noneLeft}
        title={noneLeft ? 'No generations left today' : undefined}
        onClick={() => setConfirming(true)}
        className={buttonClass('secondary')}
      >
        ↻ Generate a different set
      </button>
    )
  }

  return (
    <div className="flex basis-full flex-wrap items-center gap-3 rounded-2xl border border-slate-100 bg-slate-50 px-4 py-3">
      <p className="min-w-48 flex-1 text-sm text-slate-600">
        New questions from the same document.{' '}
        {quota.data && (
          <span className="text-slate-500">Uses 1 of your {quota.data.remaining} generations left today.</span>
        )}
      </p>
      <div className="flex shrink-0 items-center gap-2">
        <button
          type="button"
          disabled={create.isPending}
          onClick={() => setConfirming(false)}
          className={buttonClass('secondary', 'sm')}
        >
          Cancel
        </button>
        <button type="button" disabled={create.isPending} onClick={generate} className={buttonClass('primary', 'sm')}>
          {create.isPending ? 'Starting…' : 'Generate'}
        </button>
      </div>
    </div>
  )
}
