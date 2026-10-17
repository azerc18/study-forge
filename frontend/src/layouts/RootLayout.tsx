import { Link, Outlet } from 'react-router'

export default function RootLayout() {
    return (
        <div className="min-h-screen bg-slate-50 text-slate-900">
            <header className="border-b bg-white">
                <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
                    <Link to="/" className="text-lg font-bold">StudyForge</Link>
                </div>
            </header>
            <main className="mx-auto max-w-5xl px-4 py-6">
                <Outlet />
            </main>
        </div>
    )
}