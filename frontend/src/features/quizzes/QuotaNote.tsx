import { useQuota } from './queries.ts'

const timeFormat = new Intl.DateTimeFormat('en', { hour: '2-digit', minute: '2-digit' })

/** "3 of 5 generations left today" — hiện cạnh nút tạo bộ đề. */
export default function QuotaNote({ className = '' }: { className?: string }) {
  const { data } = useQuota()
  if (!data) return null

  if (data.remaining <= 0) {
    return (
      <p className={`text-xs text-red-600 ${className}`}>
        No generations left today. More at {timeFormat.format(new Date(data.resetsAt))}.
      </p>
    )
  }
  return (
    <p className={`text-xs text-slate-500 ${className}`}>
      {data.remaining} of {data.dailyQuota} generations left today
    </p>
  )
}
