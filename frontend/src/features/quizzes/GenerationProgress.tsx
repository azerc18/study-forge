import type { QuizSet } from './api.ts'

/** Màn hình chờ khi bộ đề đang QUEUED / GENERATING. Dữ liệu tự cập nhật nhờ useQuizSet poll mỗi 2s. */
export default function GenerationProgress({ set }: { set: QuizSet }) {
  const queued = set.status === 'QUEUED' || set.totalChunks === 0
  const percent = queued ? 0 : Math.round((set.completedChunks / set.totalChunks) * 100)

  return (
    <div className="flex flex-col items-center py-8 text-center">
      <div className="size-7 animate-spin rounded-full border-2 border-brand border-t-brand-ink" />

      <p className="mt-4 font-medium">{queued ? 'Waiting for a free worker…' : 'Writing your questions…'}</p>

      <div className="mt-5 w-full max-w-xs">
        <div
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
          className="h-1.5 overflow-hidden rounded-full bg-slate-100"
        >
          <div className="h-full rounded-full bg-brand-ink transition-[width] duration-500" style={{ width: `${percent}%` }} />
        </div>
        <p className="mt-2 text-xs text-slate-500">
          {queued
            ? 'Usually starts within a few seconds.'
            : `${set.completedChunks} of ${set.totalChunks} parts · ${set.generatedCount} questions so far`}
        </p>
      </div>

      <p className="mt-6 text-xs text-slate-400">You can leave this page. We'll keep working in the background.</p>
    </div>
  )
}
