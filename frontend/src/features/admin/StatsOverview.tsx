import { useState, type ReactNode } from 'react'
import BarChart from './BarChart.tsx'
import { formatCompact, formatDay, formatUsd } from './format.ts'
import { useAdminStats } from './queries.ts'

const RANGES = [7, 14, 30] as const

function StatTile({ label, value, sub }: { label: string; value: ReactNode; sub?: ReactNode }) {
  return (
    <div className="rounded-2xl border border-slate-100 bg-white/80 p-4 shadow-sm">
      <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">{label}</p>
      <p className="mt-1 text-2xl font-semibold tracking-tight tabular-nums">{value}</p>
      {sub && <p className="mt-0.5 text-xs text-slate-500">{sub}</p>}
    </div>
  )
}

/** Thanh chi phí hôm nay so với ngân sách. Trạng thái có cả chữ, không chỉ dựa vào màu. */
function BudgetBar({ spent, budget, exceeded }: { spent: number; budget: number; exceeded: boolean }) {
  if (budget <= 0) return <p className="text-xs text-slate-500">Budget limit is off</p>
  const ratio = Math.min(spent / budget, 1)
  const tone = exceeded ? 'bg-red-500' : ratio >= 0.8 ? 'bg-amber-500' : 'bg-brand-ink'
  return (
    <div>
      <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
        <div className={`h-full rounded-full ${tone}`} style={{ width: `${Math.max(ratio * 100, 2)}%` }} />
      </div>
      <p className={`mt-1 text-xs ${exceeded ? 'font-medium text-red-600' : 'text-slate-500'}`}>
        {exceeded ? '⛔ Limit reached — AI paused until tomorrow' : `${Math.round(ratio * 100)}% of ${formatUsd(budget)} daily budget`}
      </p>
    </div>
  )
}

export default function StatsOverview() {
  const [days, setDays] = useState<(typeof RANGES)[number]>(14)
  const { data, isPending, isError, isFetching } = useAdminStats(days)

  if (isPending) {
    return (
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-24 animate-pulse rounded-2xl bg-slate-50" />
        ))}
      </div>
    )
  }
  if (isError) return <p className="py-10 text-center text-sm text-slate-500">Couldn't load statistics.</p>

  const { today, daily } = data

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatTile label="Users" value={data.totalUsers} sub={`+${today.newUsers} today`} />
        <StatTile
          label="Quiz sets today"
          value={today.quizSets}
          sub={today.failedQuizSets > 0 ? <span className="text-red-600">{today.failedQuizSets} failed</span> : 'none failed'}
        />
        <StatTile label="Exams / gradings" value={`${today.exams} / ${today.essayGrades}`} sub="submitted today" />
        <div className="rounded-2xl border border-slate-100 bg-white/80 p-4 shadow-sm">
          <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">AI cost today</p>
          <p className="mt-1 text-2xl font-semibold tracking-tight tabular-nums">{formatUsd(today.costUsd)}</p>
          <div className="mt-2">
            <BudgetBar spent={today.costUsd} budget={data.budgetUsd} exceeded={data.budgetExceeded} />
          </div>
        </div>
      </div>

      <section className="rounded-3xl border border-slate-100 bg-white/80 p-5 shadow-sm sm:p-6">
        <div className="flex items-center justify-between gap-3">
          <h2 className="font-semibold">Last {days} days</h2>
          <div className="flex items-center gap-2">
            {isFetching && <span className="size-3 animate-spin rounded-full border-2 border-brand border-t-brand-ink" />}
            <div className="flex rounded-full border border-slate-200 bg-white p-0.5 text-xs">
              {RANGES.map((r) => (
                <button
                  key={r}
                  type="button"
                  aria-pressed={r === days}
                  onClick={() => setDays(r)}
                  className={`rounded-full px-2.5 py-1 transition ${
                    r === days ? 'bg-brand font-medium text-brand-ink' : 'text-slate-500 hover:text-slate-800'
                  }`}
                >
                  {r}d
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-5 grid gap-8 md:grid-cols-2">
          <BarChart
            title="AI cost per day"
            format={formatUsd}
            bars={daily.map((d) => ({
              key: d.date,
              label: formatDay(d.date),
              axisLabel: String(Number(d.date.slice(8))),
              value: d.costUsd,
              detail: `${formatCompact(d.promptTokens)} in · ${formatCompact(d.completionTokens)} out tokens`,
            }))}
          />
          <BarChart
            title="Quiz sets per day"
            format={(v) => String(Math.round(v))}
            bars={daily.map((d) => ({
              key: d.date,
              label: formatDay(d.date),
              axisLabel: String(Number(d.date.slice(8))),
              value: d.quizSets,
              detail: `${d.exams} exams · ${d.essayGrades} gradings${d.failedQuizSets ? ` · ${d.failedQuizSets} failed` : ''}`,
            }))}
          />
        </div>
      </section>
    </div>
  )
}
