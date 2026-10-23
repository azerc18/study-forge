export interface Bar {
  key: string
  label: string // nhãn đầy đủ trong tooltip / bảng (ví dụ "Oct 23")
  axisLabel?: string // nhãn ngắn dưới trục X (ví dụ "23"); mặc định = label
  value: number
  detail?: string // dòng phụ trong tooltip
}

/**
 * Biểu đồ cột 1 chuỗi (không cần legend — tiêu đề đã nói đây là gì).
 * Rê chuột / chạm vào cột để xem số; vùng bắt chuột là cả cột cao, không chỉ phần tô màu.
 * Có bảng số liệu ẩn trong <details> cho ai không nhìn được biểu đồ.
 */
export default function BarChart({
  title,
  bars,
  format,
  height = 140,
}: {
  title: string
  bars: Bar[]
  format: (v: number) => string
  height?: number
}) {
  const max = Math.max(...bars.map((b) => b.value), 0)
  const top = max > 0 ? max : 1
  const total = bars.reduce((s, b) => s + b.value, 0)
  const labelEvery = Math.max(1, Math.ceil(bars.length / 7)) // tối đa ~7 nhãn trục X

  return (
    <figure className="min-w-0">
      <figcaption className="flex items-baseline justify-between gap-3">
        <span className="text-sm font-medium text-slate-700">{title}</span>
        <span className="text-xs text-slate-500">
          Total <span className="font-medium text-slate-700 tabular-nums">{format(total)}</span>
        </span>
      </figcaption>

      <div className="mt-3 flex gap-2">
        {/* Trục Y: chỉ 2 mốc, chữ nhạt */}
        <div className="flex flex-col justify-between text-right text-[10px] text-slate-400 tabular-nums" style={{ height }}>
          <span>{format(max)}</span>
          <span>0</span>
        </div>

        <div className="relative min-w-0 flex-1">
          {/* Lưới: đường giữa + đáy, rất nhạt */}
          <div aria-hidden className="absolute inset-x-0 top-1/2 border-t border-dashed border-slate-100" />
          <div aria-hidden className="absolute inset-x-0 bottom-0 border-t border-slate-200" />

          <div role="img" aria-label={`${title}: ${bars.length} days, total ${format(total)}`}
            className="relative flex items-end gap-0.5" style={{ height }}>
            {bars.map((b, i) => {
              const pct = (b.value / top) * 100
              // Cột sát mép: canh tooltip vào trong để không tràn khỏi màn hình
              const align = i < 2 ? 'left-0' : i >= bars.length - 2 ? 'right-0' : 'left-1/2 -translate-x-1/2'
              return (
                <div key={b.key} tabIndex={0} className="group relative flex h-full flex-1 items-end outline-none">
                  <div
                    className="w-full rounded-t bg-brand-ink/80 transition group-hover:bg-brand-ink group-focus-visible:bg-brand-ink"
                    style={{ height: b.value > 0 ? `max(${pct}%, 2px)` : 0 }}
                  />
                  {/* Tooltip */}
                  <div
                    role="tooltip"
                    className={`pointer-events-none absolute bottom-full ${align} z-10 mb-1 hidden rounded-lg bg-slate-900 px-2.5 py-1.5 text-xs whitespace-nowrap text-white shadow-lg group-hover:block group-focus-visible:block`}
                  >
                    <p className="text-slate-300">{b.label}</p>
                    <p className="font-semibold tabular-nums">{format(b.value)}</p>
                    {b.detail && <p className="text-slate-300">{b.detail}</p>}
                  </div>
                </div>
              )
            })}
          </div>

          {/* Trục X */}
          <div className="mt-1.5 flex gap-0.5 text-[10px] text-slate-400">
            {bars.map((b, i) => (
              <span key={b.key} className="flex-1 truncate text-center">
                {i % labelEvery === 0 || i === bars.length - 1 ? (b.axisLabel ?? b.label) : ''}
              </span>
            ))}
          </div>
        </div>
      </div>

      <details className="mt-2 text-xs text-slate-500">
        <summary className="cursor-pointer hover:text-slate-700">Show data</summary>
        <table className="mt-2 w-full text-left tabular-nums">
          <tbody>
            {bars.map((b) => (
              <tr key={b.key} className="border-t border-slate-100">
                <td className="py-1">{b.label}</td>
                <td className="py-1 text-right text-slate-700">{format(b.value)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  )
}
