import { Link } from 'react-router'

export default function NotFoundPage() {
    return (
        <div className="grid min-h-screen place-items-center px-6 text-center">
            <div>
                <p className="text-6xl font-semibold tracking-tight text-brand-ink">404</p>
                <p className="mt-2 text-slate-500">This page doesn't exist.</p>
                <Link
                    to="/"
                    className="mt-6 inline-block rounded-full bg-brand px-5 py-2 text-sm font-medium text-brand-ink transition hover:bg-brand-strong"
                >
                    Back to home
                </Link>
            </div>
        </div>
    )
}
