import { Link } from 'react-router'
import type { DocumentItem } from './api'
import DeleteButton from './DeleteButton.tsx'
import StatusBadge from './StatusBadge.tsx'
import { formatBytes, formatDate, formatNumber } from './format.ts'

interface Props {
  doc: DocumentItem
  onDelete: (id: string) => void
  deleting: boolean
}

export default function DocumentRow({ doc, onDelete, deleting }: Props) {
  const ready = doc.status === 'READY'

  return (
    <li className="flex items-center gap-4 py-4">
      <span className="hidden size-10 shrink-0 place-items-center sm:grid rounded-xl bg-slate-50 text-[10px] font-semibold tracking-wide text-slate-500">
        {doc.fileType}
      </span>

      <Link to={`/documents/${doc.id}`} className="group min-w-0 flex-1">
        <p className="line-clamp-2 font-medium break-words group-hover:text-brand-ink sm:truncate">{doc.originalFilename}</p>
        <p className="mt-0.5 text-xs text-slate-500 sm:truncate">
          {formatBytes(doc.fileSizeBytes)} · {formatDate(doc.createdAt)}
          {ready && doc.pageCount != null && doc.wordCount != null && (
            <>
              {' '}· {doc.pageCount} {doc.fileType === 'PPTX' ? 'slides' : 'pages'} · {formatNumber(doc.wordCount)} words
            </>
          )}
        </p>
        {doc.status === 'FAILED' && doc.errorMessage && (
          <p className="mt-1 text-xs text-red-600 sm:truncate">{doc.errorMessage}</p>
        )}
        {/* Màn hình hẹp: badge nằm dưới tên file để tên có đủ chỗ */}
        <span className="mt-2 inline-block sm:hidden">
          <StatusBadge status={doc.status} />
        </span>
      </Link>

      <div className="flex shrink-0 items-center gap-2">
        <span className="hidden sm:inline-flex">
          <StatusBadge status={doc.status} />
        </span>
        <DeleteButton onConfirm={() => onDelete(doc.id)} pending={deleting} />
      </div>
    </li>
  )
}
