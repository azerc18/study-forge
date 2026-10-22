import { apiFetch, apiUpload } from '../../lib/api'

export type DocumentStatus = 'UPLOADED' | 'EXTRACTING' | 'READY' | 'FAILED'
export type FileType = 'PDF' | 'PPTX'
export type QuestionType = 'MCQ' | 'ESSAY' | 'MIXED'
export type OutputLanguage = 'EN' | 'VI'

export interface DocumentItem {
  id: string
  originalFilename: string
  fileType: FileType
  fileSizeBytes: number
  status: DocumentStatus
  pageCount: number | null
  wordCount: number | null
  errorMessage: string | null
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface GenerationOptions {
  documentId: string
  wordCount: number
  shortDocument: boolean
  questionCounts: Record<QuestionType, number[]>
  languages: OutputLanguage[]
}

export const isProcessing = (status: DocumentStatus) => status === 'UPLOADED' || status === 'EXTRACTING'

export function listDocuments(page: number, size: number): Promise<PageResponse<DocumentItem>> {
  return apiFetch(`/api/documents?page=${page}&size=${size}`)
}

export function getDocument(id: string): Promise<DocumentItem> {
  return apiFetch(`/api/documents/${encodeURIComponent(id)}`)
}

export function deleteDocument(id: string): Promise<void> {
  return apiFetch(`/api/documents/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function getGenerationOptions(id: string): Promise<GenerationOptions> {
  return apiFetch(`/api/documents/${encodeURIComponent(id)}/options`)
}

export function uploadDocument(file: File, onProgress?: (fraction: number) => void): Promise<DocumentItem> {
  const form = new FormData()
  form.append('file', file) // tên part phải khớp @RequestParam("file")
  return apiUpload('/api/documents', form, onProgress)
}
