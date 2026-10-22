import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from '../features/auth/auth-context'

const YEAR = new Date().getFullYear()

function Logo() {
    return (
        <Link to="/" className="flex items-center gap-2 font-semibold tracking-tight">
            <span className="grid size-7 place-items-center rounded-lg bg-brand text-sm text-brand-ink">S</span>
            StudyForge
        </Link>
    )
}

function UserMenu() {
    const { status, me, signOut } = useAuth()
    const { pathname } = useLocation()

    if (status === 'loading') return <div className="h-8 w-24 animate-pulse rounded-full bg-slate-100" />

    if (status === 'anonymous' || !me) {
        if (pathname === '/login') return null
        return (
            <Link to="/login" className="text-sm font-medium text-slate-600 transition hover:text-slate-900">
                Sign in
            </Link>
        )
    }

    const { user } = me
    return (
        <div className="flex items-center gap-3">
            <NavLink
                to="/documents"
                className={({ isActive }) =>
                    `text-sm transition hover:text-slate-900 ${isActive ? 'font-medium text-slate-900' : 'text-slate-500'}`
                }
            >
                Documents
            </NavLink>
            <Link to="/profile" aria-label="Your profile" className="rounded-full transition hover:ring-2 hover:ring-brand">
                {user.avatarUrl ? (
                    <img src={user.avatarUrl} alt="" referrerPolicy="no-referrer" className="size-8 rounded-full" />
                ) : (
                    <span className="grid size-8 place-items-center rounded-full bg-brand text-sm text-brand-ink">
            {(user.fullName ?? user.email).charAt(0).toUpperCase()}
          </span>
                )}
            </Link>
            <button
                type="button"
                onClick={() => void signOut()}
                className="text-sm text-slate-500 transition hover:text-slate-900"
            >
                Sign out
            </button>
        </div>
    )
}

export default function RootLayout() {
    return (
        <div className="relative isolate flex min-h-screen flex-col">
            {/* Vệt màu brand mờ phía trên, chỉ để trang trí */}
            <div
                aria-hidden
                className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[420px] bg-[radial-gradient(ellipse_at_top,var(--color-brand)_0%,transparent_70%)]"
            />

            <header className="mx-auto flex w-full max-w-5xl items-center justify-between px-6 py-5">
                <Logo />
                <UserMenu />
            </header>

            <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col px-6">
                <Outlet />
            </main>

            <footer className="mx-auto w-full max-w-5xl px-6 py-6 text-center text-xs text-slate-400">
                © {YEAR} StudyForge
            </footer>
        </div>
    )
}
