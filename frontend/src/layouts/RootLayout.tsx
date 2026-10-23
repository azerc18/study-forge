import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from '../features/auth/auth-context'
import { buttonClass } from '../components/ui/button.ts'

/** Tab đang mở: nền màu brand; tab khác: chữ xám, rê chuột thì có nền nhạt. */
const navClass = ({ isActive }: { isActive: boolean }) =>
    `rounded-full px-3 py-1.5 text-xs font-medium transition sm:px-4 sm:text-sm ${
        isActive ? 'bg-brand text-brand-ink shadow-sm' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
    }`

const YEAR = new Date().getFullYear()

function Logo() {
    return (
        <Link to="/" className="flex items-center gap-2 font-semibold tracking-tight">
            <span className="grid size-7 place-items-center rounded-lg bg-brand text-sm text-brand-ink">S</span>
            <span className="hidden sm:inline">StudyForge</span>
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
        <div className="flex items-center gap-2 whitespace-nowrap sm:gap-3">
            <nav className="flex items-center gap-1 rounded-full bg-white/80 p-1 shadow-sm ring-1 ring-slate-200">
                <NavLink to="/documents" className={navClass}>
                    Documents
                </NavLink>
                <NavLink to="/quiz-sets" className={navClass}>
                    Quiz sets
                </NavLink>
                {user.role === 'ADMIN' && (
                    <NavLink to="/admin" className={navClass}>
                        Admin
                    </NavLink>
                )}
            </nav>
            <Link to="/profile" aria-label="Your profile" className="rounded-full transition hover:ring-2 hover:ring-brand">
                {user.avatarUrl ? (
                    <img src={user.avatarUrl} alt="" referrerPolicy="no-referrer" className="size-8 rounded-full" />
                ) : (
                    <span className="grid size-8 place-items-center rounded-full bg-brand text-sm text-brand-ink">
            {(user.fullName ?? user.email).charAt(0).toUpperCase()}
          </span>
                )}
            </Link>
            {/* Điện thoại: ẩn nút này cho đỡ chật, vẫn đăng xuất được ở trang Profile */}
            <span className="hidden sm:inline-flex">
                <button type="button" onClick={() => void signOut()} className={buttonClass('secondary', 'sm')}>
                    Sign out
                </button>
            </span>
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
