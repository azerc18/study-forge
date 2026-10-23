import { useSearchParams } from 'react-router'
import FailedSetsList from '../features/admin/FailedSetsList'
import StatsOverview from '../features/admin/StatsOverview'
import UsersTable from '../features/admin/UsersTable'

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'users', label: 'Users' },
  { id: 'failed', label: 'Failed sets' },
] as const

type TabId = (typeof TABS)[number]['id']

export default function AdminPage() {
  const [params, setParams] = useSearchParams()
  const tab: TabId = TABS.some((t) => t.id === params.get('tab')) ? (params.get('tab') as TabId) : 'overview'

  return (
    <section className="mx-auto w-full max-w-4xl py-10">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Admin</h1>
          <p className="mt-1 text-sm text-slate-500">Usage, AI cost and users. Days follow Vietnam time.</p>
        </div>
        {/* Tab lưu trên URL (?tab=users) → F5 hay gửi link vẫn mở đúng tab */}
        <div role="tablist" className="flex rounded-full border border-slate-200 bg-white p-1 text-sm shadow-sm">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              role="tab"
              aria-selected={t.id === tab}
              onClick={() => setParams(t.id === 'overview' ? {} : { tab: t.id }, { replace: true })}
              className={`rounded-full px-4 py-1.5 transition ${
                t.id === tab ? 'bg-brand font-medium text-brand-ink' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-6">
        {tab === 'overview' && <StatsOverview />}
        {tab === 'users' && <UsersTable />}
        {tab === 'failed' && <FailedSetsList />}
      </div>
    </section>
  )
}
