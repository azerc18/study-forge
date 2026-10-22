import { useState } from 'react'
import { buttonClass } from '../components/ui/button.ts'
import DocumentRow from '../features/documents/DocumentRow'
import UploadDropzone from '../features/documents/UploadDropzone'
import { useDeleteDocument, useDocuments } from '../features/documents/queries'

function ListSkeleton() {
  return (
    <ul className="divide-y divide-slate-100">
      {[0, 1, 2].map((i) => (
        <li key={i} className="flex items-center gap-4 py-4">
          <div className="size-10 animate-pulse rounded-xl bg-slate-100" />
          <div className="flex-1 space-y-2">
            <div className="h-3.5 w-1/2 animate-pulse rounded bg-slate-100" />
            <div className="h-3 w-1/3 animate-pulse rounded bg-slate-100" />
          </div>
        </li>
      ))}
    </ul>
  )
}

export default function DocumentsPage() {
  const [page, setPage] = useState(0)
  const { data, isPending, isError, refetch } = useDocuments(page)
  const remove = useDeleteDocument()

  function handleDelete(id: string) {
    remove.mutate(id, {
      // Xoá phần tử cuối cùng của 1 trang (không phải trang đầu) thì lùi về trang trước
      onSuccess: () => {
        if (data && data.content.length === 1 && page > 0) setPage(page - 1)
      },
    })
  }

  return (
    <section className="mx-auto w-full max-w-2xl py-10">
      <h1 className="text-2xl font-semibold tracking-tight">Your documents</h1>
      <p className="mt-1 text-sm text-slate-500">Upload course slides, then turn them into practice sets.</p>

      <div className="mt-8">
        <UploadDropzone />
      </div>

      <div className="mt-8 rounded-3xl border border-slate-100 bg-white/80 px-6 py-2 shadow-sm">
        {isPending && <ListSkeleton />}

        {isError && (
          <div className="py-10 text-center text-sm text-slate-500">
            Couldn't load your documents.{' '}
            <button type="button" onClick={() => void refetch()} className="font-medium text-brand-ink underline">
              Try again
            </button>
          </div>
        )}

        {data && data.totalElements === 0 && (
          <p className="py-10 text-center text-sm text-slate-500">No documents yet. Your uploads will show up here.</p>
        )}

        {data && data.content.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {data.content.map((doc) => (
              <DocumentRow
                key={doc.id}
                doc={doc}
                onDelete={handleDelete}
                deleting={remove.isPending && remove.variables === doc.id}
              />
            ))}
          </ul>
        )}

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-100 py-3 text-sm">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
              className={buttonClass('secondary', 'sm')}
            >
              ← Newer
            </button>
            <span className="text-xs text-slate-400">
              Page {page + 1} of {data.totalPages}
            </span>
            <button
              type="button"
              disabled={!data.hasNext}
              onClick={() => setPage(page + 1)}
              className={buttonClass('secondary', 'sm')}
            >
              Older →
            </button>
          </div>
        )}
      </div>
    </section>
  )
}
