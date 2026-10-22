import { Link } from 'react-router'
import DeleteButton from '../documents/DeleteButton'
import { formatDate } from '../documents/format'
import { hasQuestions, type QuizSet } from './api.ts'
import { LANGUAGE_LABELS, TYPE_LABELS } from './labels.ts'
import QuizStatusBadge from './QuizStatusBadge.tsx'

interface Props {
  set: QuizSet
  onDelete?: (id: string) => void
  deleting?: boolean
}

export default function QuizSetRow({ set, onDelete, deleting = false }: Props) {
  const count = hasQuestions(set.status) ? set.generatedCount : set.requestedCount

  return (
    <li className="flex items-center gap-4 py-4">
      <Link to={`/quiz-sets/${set.id}`} className="group min-w-0 flex-1">
        <p className="line-clamp-2 font-medium break-words group-hover:text-brand-ink sm:truncate">{set.title}</p>
        <p className="mt-0.5 text-xs text-slate-500">
          {TYPE_LABELS[set.questionType]} · {count} questions · {LANGUAGE_LABELS[set.language]} ·{' '}
          {formatDate(set.createdAt)}
        </p>
        <span className="mt-2 inline-block sm:hidden">
          <QuizStatusBadge status={set.status} />
        </span>
      </Link>

      <div className="flex shrink-0 items-center gap-2">
        <span className="hidden sm:inline-flex">
          <QuizStatusBadge status={set.status} />
        </span>
        {onDelete && <DeleteButton onConfirm={() => onDelete(set.id)} pending={deleting} />}
      </div>
    </li>
  )
}
