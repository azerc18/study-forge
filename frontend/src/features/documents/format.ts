import { ApiError } from '../../lib/api'

export const MAX_FILE_BYTES = 20 * 1024 * 1024
export const ACCEPT = '.pdf,.pptx,application/pdf,application/vnd.openxmlformats-officedocument.presentationml.presentation'

const dateFormat = new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' })
const numberFormat = new Intl.NumberFormat('en')

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export const formatDate = (iso: string) => dateFormat.format(new Date(iso))
export const formatNumber = (n: number) => numberFormat.format(n)

/** Kiểm tra sớm ở trình duyệt để khỏi upload 20 MB rồi mới bị từ chối. Backend vẫn kiểm tra lại. */
export function validateFile(file: File): string | null {
  const name = file.name.toLowerCase()
  if (name.endsWith('.ppt')) return 'Old .ppt files are not supported. Save it as .pptx and try again.'
  if (!name.endsWith('.pdf') && !name.endsWith('.pptx')) return 'Only PDF and PowerPoint (.pptx) files are supported.'
  if (file.size === 0) return 'This file is empty.'
  if (file.size > MAX_FILE_BYTES) return 'This file is larger than 20 MB.'
  return null
}

const UPLOAD_ERRORS: Record<string, string> = {
  FILE_TOO_LARGE: 'This file is larger than 20 MB.',
  UNSUPPORTED_FILE_TYPE: "This doesn't look like a valid PDF or PowerPoint file.",
  EMPTY_FILE: 'This file is empty.',
  NETWORK_ERROR: 'Network error. Check your connection and try again.',
}

export function uploadErrorMessage(err: unknown): string {
  if (err instanceof ApiError && err.code && UPLOAD_ERRORS[err.code]) return UPLOAD_ERRORS[err.code]
  return 'Upload failed. Please try again.'
}
