import { useEffect, useState } from 'react'
import type { QuizSet } from './api'

/** Ước lượng thời gian 1 phần (chunk): sau ~TAU ms thanh đi được ~63% của phần đó. */
const TAU_MS = 15_000
/** Không bao giờ tự chạy quá 90% của phần đang làm → khi server báo xong phần, thanh chỉ tiến chứ không lùi. */
const MAX_STEP_FILL = 0.9
const TICK_MS = 250

/**
 * % hiển thị = phần đã xong (từ server) + phần "đang làm" tăng dần theo thời gian (ước lượng phía client).
 * Mỗi khi completedChunks thay đổi thì mốc thời gian được đặt lại.
 */
function useCreepingPercent(completed: number, total: number, active: boolean): number {
  const [now, setNow] = useState(() => Date.now())
  const [anchor, setAnchor] = useState({ completed, at: now })

  useEffect(() => {
    if (!active) return
    const id = setInterval(() => setNow(Date.now()), TICK_MS)
    return () => clearInterval(id)
  }, [active])

  // Server báo thêm phần xong → đặt lại mốc (điều chỉnh state ngay trong render, không cần effect)
  if (anchor.completed !== completed) setAnchor({ completed, at: now })

  if (!active || total <= 0) return 0
  const step = 1 / total
  const elapsed = Math.max(0, now - anchor.at)
  const inStep = step * MAX_STEP_FILL * (1 - Math.exp(-elapsed / TAU_MS))
  return Math.min(99, (completed / total + inStep) * 100)
}

/** Màn hình chờ khi bộ đề đang QUEUED / GENERATING. Dữ liệu tự cập nhật nhờ useQuizSet poll mỗi 2s. */
export default function GenerationProgress({ set }: { set: QuizSet }) {
  const queued = set.status === 'QUEUED' || set.totalChunks === 0
  const percent = useCreepingPercent(set.completedChunks, set.totalChunks, !queued)
  const shown = Math.max(3, percent) // luôn thấy 1 vệt nhỏ ngay khi bắt đầu

  return (
    <div className="flex flex-col items-center py-8 text-center">
      <div className="size-7 animate-spin rounded-full border-2 border-brand border-t-brand-ink" />

      <p className="mt-4 font-medium">{queued ? 'Waiting for a free worker…' : 'Writing your questions…'}</p>

      <div className="mt-5 w-full max-w-xs">
        <div
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={queued ? undefined : Math.round(percent)}
          aria-busy
          className="relative h-1.5 overflow-hidden rounded-full bg-slate-100"
        >
          {queued ? (
            // Chưa có tiến độ thật → vệt chạy qua lại (indeterminate)
            <div className="absolute inset-y-0 w-1/3 animate-indeterminate rounded-full bg-brand-ink/70" />
          ) : (
            <div
              className="relative h-full overflow-hidden rounded-full bg-brand-ink transition-[width] duration-300 ease-linear"
              style={{ width: `${shown}%` }}
            >
              {/* Ánh sáng lướt trên phần đã fill cho cảm giác "đang chạy" */}
              <div className="absolute inset-y-0 w-1/2 animate-shimmer bg-linear-to-r from-transparent via-white/40 to-transparent" />
            </div>
          )}
        </div>
        <p className="mt-2 text-xs text-slate-500">
          {queued
            ? 'Usually starts within a few seconds.'
            : `${Math.round(percent)}% · ${set.completedChunks} of ${set.totalChunks} parts · ${set.generatedCount} questions so far`}
        </p>
      </div>

      <p className="mt-6 text-xs text-slate-400">You can leave this page. We'll keep working in the background.</p>
    </div>
  )
}
