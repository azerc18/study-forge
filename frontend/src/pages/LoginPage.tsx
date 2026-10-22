import { useCallback, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../features/auth/auth-context'
import GoogleSignInButton from '../features/auth/GoogleSignInButton'
import { ApiError } from '../lib/api'

function errorMessage(err: unknown): string {
    if (err instanceof ApiError) {
        if (err.code === 'ACCOUNT_LOCKED') return 'Your account has been locked.'
        if (err.code === 'GOOGLE_AUTH_UNAVAILABLE') return 'Google sign-in is temporarily unavailable. Please try again.'
    }
    return 'Sign-in failed. Please try again.'
}

export default function LoginPage() {
    const { status, signIn } = useAuth()
    const navigate = useNavigate()
    const location = useLocation()
    // Trang người dùng muốn vào trước khi bị chuyển sang /login (do RequireAuth gửi kèm)
    const from = (location.state as { from?: string } | null)?.from ?? '/'
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const handleCredential = useCallback(
        async (idToken: string) => {
            setSubmitting(true)
            setError(null)
            try {
                await signIn(idToken)
                navigate(from, { replace: true })
            } catch (err) {
                setError(errorMessage(err))
            } finally {
                setSubmitting(false)
            }
        },
        [signIn, navigate, from],
    )

    if (status === 'authenticated') return <Navigate to={from} replace />

    return (
        <section className="flex flex-1 items-center justify-center py-12">
            <div className="w-full max-w-sm rounded-3xl border border-slate-100 bg-white/80 p-8 text-center shadow-sm backdrop-blur">
        <span className="mx-auto grid size-11 place-items-center rounded-2xl bg-brand text-lg font-semibold text-brand-ink">
          S
        </span>

                <h1 className="mt-5 text-2xl font-semibold tracking-tight">Welcome to StudyForge</h1>
                <p className="mt-2 text-sm text-slate-500">Sign in to create and practice your question sets.</p>

                <div className="mt-8">
                    {submitting ? (
                        <p className="py-2.5 text-sm text-slate-500">Signing you in…</p>
                    ) : (
                        <GoogleSignInButton onCredential={handleCredential} onError={setError} />
                    )}
                </div>

                {error && (
                    <p role="alert" className="mt-4 text-sm text-red-600">
                        {error}
                    </p>
                )}

                <p className="mt-8 text-xs text-slate-400">We only use your name, email and profile picture.</p>
            </div>
        </section>
    )
}
