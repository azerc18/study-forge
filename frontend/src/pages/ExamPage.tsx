import { Link, useParams } from 'react-router'
import { buttonClass } from '../components/ui/button'
import ExamResult from '../features/exams/ExamResult'
import ExamTaking from '../features/exams/ExamTaking'
import { useExam } from '../features/exams/queries'
import { ApiError } from '../lib/api'

export default function ExamPage() {
  const { id = '' } = useParams()
  const { data: exam, isPending, error } = useExam(id)

  if (isPending) {
    return (
      <section className="mx-auto w-full max-w-2xl py-10">
        <div className="h-10 w-40 animate-pulse rounded-full bg-slate-50" />
        <div className="mt-6 h-80 animate-pulse rounded-3xl bg-slate-50" />
      </section>
    )
  }

  if (error || !exam) {
    const notFound = error instanceof ApiError && (error.status === 404 || error.status === 400)
    return (
      <section className="mx-auto w-full max-w-2xl py-10">
        <Link to="/quiz-sets" className={buttonClass('secondary', 'sm')}>
          ← All quiz sets
        </Link>
        <p className="mt-10 text-center text-slate-500">
          {notFound ? 'This exam does not exist.' : "Couldn't load this exam."}
        </p>
      </section>
    )
  }

  const inProgress = exam.status === 'IN_PROGRESS'

  return (
    <section className="mx-auto w-full max-w-2xl py-10">
      <div className="flex items-center justify-between gap-3">
        <Link to={`/quiz-sets/${exam.quizSetId}`} className={buttonClass('secondary', 'sm')}>
          ← {inProgress ? 'Leave (answers are kept)' : 'Back to quiz set'}
        </Link>
      </div>
      <h1 className="mt-6 line-clamp-2 text-xl font-semibold tracking-tight break-words">{exam.quizSetTitle}</h1>
      <p className="mt-1 mb-2 text-sm text-slate-500">
        {inProgress ? 'Exam in progress' : 'Exam result'} · {exam.total} questions
      </p>

      {inProgress ? <ExamTaking key={exam.id} exam={exam} /> : <ExamResult exam={exam} />}
    </section>
  )
}
