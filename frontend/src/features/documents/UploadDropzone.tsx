import { useRef, useState, type DragEvent } from 'react'
import { ACCEPT, uploadErrorMessage, validateFile } from './format.ts'
import { useUploadDocument } from './queries.ts'

function UploadIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="size-6" aria-hidden>
      <path d="M12 16V4m0 0-4 4m4-4 4 4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" strokeLinecap="round" />
    </svg>
  )
}

export default function UploadDropzone() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [progress, setProgress] = useState(0)
  const [fileName, setFileName] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [justUploaded, setJustUploaded] = useState(false)
  const upload = useUploadDocument()

  function start(file: File | undefined) {
    if (!file || upload.isPending) return
    setJustUploaded(false)

    const problem = validateFile(file)
    if (problem) {
      setError(problem)
      return
    }

    setError(null)
    setFileName(file.name)
    setProgress(0)
    upload.mutate(
      { file, onProgress: setProgress },
      {
        onSuccess: () => setJustUploaded(true),
        onError: (err) => setError(uploadErrorMessage(err)),
        onSettled: () => setFileName(null),
      },
    )
  }

  function onDrop(e: DragEvent) {
    e.preventDefault()
    setDragging(false)
    start(e.dataTransfer.files[0])
  }

  function onDragLeave(e: DragEvent) {
    // dragleave cũng bắn khi rê qua phần tử con, chỉ tắt khi thật sự rời khỏi vùng thả
    if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false)
  }

  const uploading = upload.isPending && fileName

  return (
    <div>
      <div
        onDragOver={(e) => {
          e.preventDefault()
          if (!upload.isPending) setDragging(true)
        }}
        onDragLeave={onDragLeave}
        onDrop={onDrop}
        className={`flex flex-col items-center rounded-3xl border-2 border-dashed px-6 py-10 text-center transition ${
          dragging ? 'border-brand-ink/40 bg-brand/60' : 'border-slate-200 bg-white/70'
        }`}
      >
        {uploading ? (
          <div className="w-full max-w-xs">
            <p className="truncate text-sm font-medium">{fileName}</p>
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full rounded-full bg-brand-ink transition-[width]"
                style={{ width: `${Math.round(progress * 100)}%` }}
              />
            </div>
            <p className="mt-2 text-xs text-slate-500">
              {progress < 1 ? `Uploading… ${Math.round(progress * 100)}%` : 'Saving…'}
            </p>
          </div>
        ) : (
          <>
            <span className="grid size-12 place-items-center rounded-2xl bg-brand text-brand-ink">
              <UploadIcon />
            </span>
            <p className="mt-4 font-medium">Drop your slides here</p>
            <p className="mt-1 text-sm text-slate-500">
              or{' '}
              <button
                type="button"
                onClick={() => inputRef.current?.click()}
                className="font-medium text-brand-ink underline decoration-brand-strong decoration-2 underline-offset-4 hover:decoration-brand-ink"
              >
                browse your files
              </button>
            </p>
            <p className="mt-4 text-xs text-slate-400">PDF or PPTX · up to 20 MB · 100 pages</p>
          </>
        )}

        <input
          ref={inputRef}
          type="file"
          accept={ACCEPT}
          className="hidden"
          onChange={(e) => {
            start(e.target.files?.[0])
            e.target.value = '' // cho phép chọn lại đúng file đó
          }}
        />
      </div>

      <div aria-live="polite" className="mt-3 min-h-5 text-center text-sm">
        {error && <p className="text-red-600">{error}</p>}
        {!error && justUploaded && <p className="text-slate-500">Uploaded. We're reading it now — this takes a few seconds.</p>}
      </div>
    </div>
  )
}
