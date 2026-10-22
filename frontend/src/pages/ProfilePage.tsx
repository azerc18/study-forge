import { useAuth } from '../features/auth/auth-context'

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-3">
      <dt className="text-sm text-slate-500">{label}</dt>
      <dd className="text-sm font-medium">{value}</dd>
    </div>
  )
}

export default function ProfilePage() {
  const { me, signOut } = useAuth()
  if (!me) return null // RequireAuth đã đảm bảo có me

  const { user, dailyQuota } = me
  const displayName = user.fullName ?? user.email

  return (
    <section className="flex flex-1 items-center justify-center py-12">
      <div className="w-full max-w-sm rounded-3xl border border-slate-100 bg-white/80 p-8 shadow-sm backdrop-blur">
        <div className="flex flex-col items-center text-center">
          {user.avatarUrl ? (
            <img
              src={user.avatarUrl}
              alt=""
              referrerPolicy="no-referrer"
              className="size-20 rounded-full ring-4 ring-brand"
            />
          ) : (
            <span className="grid size-20 place-items-center rounded-full bg-brand text-2xl font-semibold text-brand-ink">
              {displayName.charAt(0).toUpperCase()}
            </span>
          )}
          <h1 className="mt-4 text-xl font-semibold tracking-tight">{displayName}</h1>
          <p className="mt-1 text-sm text-slate-500">{user.email}</p>
        </div>

        <dl className="mt-8 divide-y divide-slate-100 border-y border-slate-100">
          <Row label="Account" value={user.role === 'ADMIN' ? 'Admin' : 'Student'} />
          <Row label="Daily generations" value={`${dailyQuota} per day`} />
          <Row label="Signed in with" value="Google" />
        </dl>

        <button
          type="button"
          onClick={() => void signOut()}
          className="mt-8 w-full rounded-full border border-slate-200 py-2.5 text-sm font-medium text-slate-600 transition hover:border-slate-300 hover:text-slate-900"
        >
          Sign out
        </button>
      </div>
    </section>
  )
}
