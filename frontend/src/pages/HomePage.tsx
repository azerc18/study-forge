import { Link } from 'react-router'
import { useAuth } from '../features/auth/auth-context'

const FEATURES = [
    { title: 'Upload slides', text: 'PDF or PowerPoint from any course.' },
    { title: 'Choose your format', text: 'Multiple choice or essay, in English or Vietnamese.' },
    { title: 'Check every answer', text: 'Each question cites the page it came from.' },
]

function CallToAction() {
    const { status, me } = useAuth()

    if (status === 'loading') {
        return <div className="h-12 w-44 animate-pulse rounded-full bg-slate-100" />
    }

    if (status === 'authenticated' && me) {
        const firstName = me.user.fullName?.split(' ').at(-1) ?? 'there'
        return (
            <div className="flex flex-col items-center gap-3">
                <p className="text-slate-600">Welcome back, {firstName}.</p>
                <button
                    type="button"
                    disabled
                    className="cursor-not-allowed rounded-full bg-brand px-6 py-3 font-medium text-brand-ink opacity-60"
                >
                    Upload a document · coming soon
                </button>
            </div>
        )
    }

    return (
        <Link
            to="/login"
            className="rounded-full bg-brand px-7 py-3 font-medium text-brand-ink transition hover:bg-brand-strong"
        >
            Get started — it's free
        </Link>
    )
}

export default function HomePage() {
    return (
        <section className="flex flex-1 flex-col items-center justify-center py-12 text-center">
      <span className="rounded-full border border-brand-strong bg-white/60 px-3 py-1 text-xs font-medium text-brand-ink">
        PDF · PPTX → Practice exam
      </span>

            <h1 className="mt-6 max-w-2xl text-4xl font-semibold tracking-tight sm:text-5xl">
                Turn your lecture slides into practice exams.
            </h1>

            <p className="mt-4 max-w-md text-slate-600">
                Upload your course material and get questions you can trust — every answer points back to its source.
            </p>

            <div className="mt-9">
                <CallToAction />
            </div>

            <ul className="mt-16 grid w-full max-w-3xl gap-8 text-left sm:grid-cols-3">
                {FEATURES.map((f, i) => (
                    <li key={f.title} className="flex gap-3">
            <span className="grid size-6 shrink-0 place-items-center rounded-full bg-brand text-xs font-semibold text-brand-ink">
              {i + 1}
            </span>
                        <div>
                            <p className="font-medium">{f.title}</p>
                            <p className="mt-1 text-sm text-slate-500">{f.text}</p>
                        </div>
                    </li>
                ))}
            </ul>
        </section>
    )
}
