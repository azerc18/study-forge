import { useEffect, useState, type FormEvent } from 'react'
import { useToast } from '../../components/toast/toast-context'
import { buttonClass } from '../../components/ui/button'
import { useAuth } from '../auth/auth-context'
import type { AdminUserRow } from './api'
import { adminErrorMessage, formatDateTime } from './format.ts'
import { ADMIN_PAGE_SIZE, useAdminUsers, useUpdateAdminUser } from './queries.ts'

/** Giá trị chỉ cập nhật sau khi ngừng gõ `ms` mili giây → không gọi API mỗi lần gõ phím. */
function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), ms)
    return () => clearTimeout(id)
  }, [value, ms])
  return debounced
}

function QuotaEditor({ user }: { user: AdminUserRow }) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(String(user.dailyQuota))
  const update = useUpdateAdminUser()
  const toast = useToast()

  function save(e: FormEvent) {
    e.preventDefault()
    const quota = Number(value)
    if (!Number.isInteger(quota) || quota < 0 || quota > 1000) {
      toast.show('Daily quota must be a whole number between 0 and 1000.', 'error')
      return
    }
    update.mutate(
      { id: user.id, input: { dailyQuota: quota } },
      {
        onSuccess: () => {
          setEditing(false)
          toast.show(`Quota for ${user.email} set to ${quota}/day.`, 'success')
        },
        onError: (err) => toast.show(adminErrorMessage(err), 'error'),
      },
    )
  }

  if (!editing) {
    return (
      <button
        type="button"
        onClick={() => {
          setValue(String(user.dailyQuota))
          setEditing(true)
        }}
        title="Change daily quota"
        className="rounded-lg px-1.5 py-0.5 text-sm tabular-nums hover:bg-slate-100"
      >
        <span className={user.usedToday >= user.dailyQuota ? 'font-semibold text-red-600' : 'text-slate-800'}>
          {user.usedToday}
        </span>
        <span className="text-slate-400"> / {user.dailyQuota}</span> <span aria-hidden className="text-slate-300">✎</span>
      </button>
    )
  }

  return (
    <form onSubmit={save} className="flex items-center gap-1">
      <input
        type="number"
        min={0}
        max={1000}
        value={value}
        autoFocus
        onChange={(e) => setValue(e.target.value)}
        aria-label={`Daily quota for ${user.email}`}
        className="w-16 rounded-lg border border-slate-200 px-2 py-1 text-sm tabular-nums outline-none focus:border-brand-ink/40 focus:ring-2 focus:ring-brand"
      />
      <button type="submit" disabled={update.isPending} className={buttonClass('primary', 'sm')}>
        Save
      </button>
      <button type="button" onClick={() => setEditing(false)} className={buttonClass('secondary', 'sm')}>
        ✕
      </button>
    </form>
  )
}

function LockButton({ user }: { user: AdminUserRow }) {
  const [confirming, setConfirming] = useState(false)
  const update = useUpdateAdminUser()
  const toast = useToast()

  function apply(locked: boolean) {
    update.mutate(
      { id: user.id, input: { locked } },
      {
        onSuccess: () => {
          setConfirming(false)
          toast.show(`${user.email} ${locked ? 'locked' : 'unlocked'}.`, 'success')
        },
        onError: (err) => toast.show(adminErrorMessage(err), 'error'),
      },
    )
  }

  if (user.locked) {
    return (
      <button type="button" disabled={update.isPending} onClick={() => apply(false)} className={buttonClass('secondary', 'sm')}>
        Unlock
      </button>
    )
  }
  if (confirming) {
    return (
      <span className="inline-flex items-center gap-1">
        <button type="button" disabled={update.isPending} onClick={() => apply(true)} className={buttonClass('dangerSolid', 'sm')}>
          Yes, lock
        </button>
        <button type="button" onClick={() => setConfirming(false)} className={buttonClass('secondary', 'sm')}>
          Cancel
        </button>
      </span>
    )
  }
  return (
    <button type="button" onClick={() => setConfirming(true)} className={buttonClass('danger', 'sm')}>
      Lock
    </button>
  )
}

function UserRow({ user, isSelf }: { user: AdminUserRow; isSelf: boolean }) {
  const name = user.fullName ?? user.email
  return (
    <li className="grid grid-cols-[auto_1fr_auto] items-center gap-x-3 gap-y-2 py-3 sm:grid-cols-[auto_1fr_8rem_4rem_8rem_7rem]">
      <span className="grid size-9 place-items-center rounded-full bg-brand text-sm font-medium text-brand-ink">
        {name.charAt(0).toUpperCase()}
      </span>

      <div className="min-w-0">
        <p className="flex items-center gap-1.5 truncate text-sm font-medium">
          <span className="truncate">{name}</span>
          {user.role === 'ADMIN' && (
            <span className="rounded-full bg-slate-900 px-1.5 py-0.5 text-[10px] font-semibold text-white">ADMIN</span>
          )}
          {user.locked && (
            <span className="rounded-full bg-red-50 px-1.5 py-0.5 text-[10px] font-semibold text-red-700">🔒 LOCKED</span>
          )}
          {isSelf && <span className="text-xs font-normal text-slate-400">(you)</span>}
        </p>
        <p className="truncate text-xs text-slate-500">{user.email}</p>
      </div>

      {/* Điện thoại: các cột dưới đây xuống dòng, gọn trong 1 hàng phụ */}
      <div className="col-span-3 col-start-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500 sm:contents">
        <div className="sm:text-right">
          <span className="mr-1 sm:hidden">Today:</span>
          <QuotaEditor user={user} />
        </div>
        <div className="tabular-nums sm:text-right sm:text-sm sm:text-slate-700">
          <span className="sm:hidden">Sets: </span>
          {user.quizSetCount}
        </div>
        <div className="sm:text-right">
          <span className="sm:hidden">Last login: </span>
          {formatDateTime(user.lastLoginAt)}
        </div>
        <div className="sm:text-right">
          {!isSelf && user.role !== 'ADMIN' && <LockButton user={user} />}
        </div>
      </div>
    </li>
  )
}

export default function UsersTable() {
  const { me } = useAuth()
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const q = useDebounced(search.trim(), 300)
  const { data, isPending, isError, isFetching } = useAdminUsers(q, page)

  return (
    <section className="rounded-3xl border border-slate-100 bg-white/80 p-5 shadow-sm sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-semibold">
          Users {data && <span className="font-normal text-slate-400">· {data.totalElements}</span>}
        </h2>
        <input
          type="search"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value)
            setPage(0)
          }}
          placeholder="Search email or name"
          aria-label="Search users"
          className="w-full rounded-full border border-slate-200 px-4 py-2 text-sm outline-none placeholder:text-slate-400 focus:border-brand-ink/40 focus:ring-2 focus:ring-brand sm:w-64"
        />
      </div>

      <div className="mt-4 hidden grid-cols-[auto_1fr_8rem_4rem_8rem_7rem] gap-x-3 border-b border-slate-100 pb-2 text-xs font-medium tracking-wide text-slate-400 uppercase sm:grid">
        <span className="w-9" />
        <span>User</span>
        <span className="text-right">Today / quota</span>
        <span className="text-right">Sets</span>
        <span className="text-right">Last login</span>
        <span />
      </div>

      {isPending ? (
        <div className="mt-4 space-y-3">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-12 animate-pulse rounded-xl bg-slate-50" />
          ))}
        </div>
      ) : isError ? (
        <p className="py-10 text-center text-sm text-slate-500">Couldn't load users.</p>
      ) : data.content.length === 0 ? (
        <p className="py-10 text-center text-sm text-slate-500">No users match "{q}".</p>
      ) : (
        <ul className={`divide-y divide-slate-100 transition-opacity ${isFetching ? 'opacity-60' : ''}`}>
          {data.content.map((u) => (
            <UserRow key={u.id} user={u} isSelf={u.id === me?.user.id} />
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)} className={buttonClass('secondary', 'sm')}>
            ← Previous
          </button>
          <span>
            Page {page + 1} of {data.totalPages}
          </span>
          <button type="button" disabled={!data.hasNext} onClick={() => setPage((p) => p + 1)} className={buttonClass('secondary', 'sm')}>
            Next →
          </button>
        </div>
      )}
      <p className="sr-only">{ADMIN_PAGE_SIZE} users per page</p>
    </section>
  )
}
