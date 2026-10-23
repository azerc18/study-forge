const API_BASE: string = import.meta.env.VITE_API_URL ?? ''

export const ME_PATH = '/api/users/me'

export type Role = 'USER' | 'ADMIN'

export interface User {
  id: string
  email: string
  fullName: string | null
  avatarUrl: string | null
  role: Role
}

export interface Me {
  user: User
  dailyQuota: number
}

interface ApiResponse<T> {
  status: number
  message?: string
  errorCode?: string
  result?: T
  details?: unknown
}

interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  /** Thông tin thêm từ backend, ví dụ QUOTA_EXCEEDED có { dailyQuota, resetsAt } */
  readonly details?: unknown

  constructor(status: number, code: string | undefined, message: string, details?: unknown) {
    super(message)
    this.status = status
    this.code = code
    this.details = details
  }
}

type RequestOptions = Omit<RequestInit, 'headers'> & { headers?: Record<string, string> }

let accessToken: string | null = null
let refreshing: Promise<string | null> | null = null

/** Dùng chung cho fetch và XHR: nhận status + text của body, trả về `result` hoặc ném ApiError. */
function toResult<T>(status: number, statusText: string, text: string): T {
  if (status === 204) return undefined as T // DELETE: không có body

  let body: ApiResponse<T> | null = null
  try {
    body = text ? (JSON.parse(text) as ApiResponse<T>) : null
  } catch {
    body = null
  }
  if (status < 200 || status >= 300 || !body) {
    throw new ApiError(status, body?.errorCode, body?.message ?? statusText, body?.details)
  }
  return body.result as T
}

async function parse<T>(res: Response): Promise<T> {
  return toResult<T>(res.status, res.statusText, await res.text())
}

/** Chỉ cho phép 1 lời gọi /refresh tại một thời điểm (tránh dùng lại refresh token). */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshing) {
    refreshing = fetch(`${API_BASE}/api/auth/refresh`, { method: 'POST', credentials: 'include' })
        .then(async (res) => (res.ok ? (await parse<LoginResult>(res)).accessToken : null))
        .catch(() => null)
        .then((token) => {
          accessToken = token
          return token
        })
        .finally(() => {
          refreshing = null
        })
  }
  return refreshing
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const send = () =>
      fetch(`${API_BASE}${path}`, {
        ...options,
        credentials: 'include',
        headers: {
          // Chỉ gắn JSON khi body là chuỗi. FormData phải để trình duyệt tự đặt boundary
          ...(typeof options.body === 'string' ? { 'Content-Type': 'application/json' } : {}),
          ...options.headers,
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        },
      })

  let res = await send()
  if (res.status === 401 && !path.startsWith('/api/auth/')) {
    const token = await refreshAccessToken()
    if (token) res = await send() // thử lại đúng 1 lần
  }
  return parse<T>(res)
}

/**
 * Upload multipart có báo tiến độ. fetch() chưa đo được tiến độ upload nên dùng XMLHttpRequest.
 * onProgress nhận giá trị 0..1.
 */
export async function apiUpload<T>(
    path: string,
    body: FormData,
    onProgress?: (fraction: number) => void,
): Promise<T> {
  const send = () =>
      new Promise<XMLHttpRequest>((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        xhr.open('POST', `${API_BASE}${path}`)
        xhr.withCredentials = true
        if (accessToken) xhr.setRequestHeader('Authorization', `Bearer ${accessToken}`)
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) onProgress?.(e.loaded / e.total)
        }
        xhr.onload = () => resolve(xhr)
        xhr.onerror = () => reject(new ApiError(0, 'NETWORK_ERROR', 'Network error'))
        xhr.send(body)
      })

  let xhr = await send()
  if (xhr.status === 401) {
    const token = await refreshAccessToken()
    if (token) {
      onProgress?.(0)
      xhr = await send() // access token hết hạn giữa chừng: gửi lại 1 lần
    }
  }
  return toResult<T>(xhr.status, xhr.statusText, xhr.responseText)
}

// ===== Auth =====

export async function loginWithGoogle(idToken: string): Promise<User> {
  const result = await apiFetch<LoginResult>('/api/auth/google', {
    method: 'POST',
    body: JSON.stringify({ idToken }),
  })
  accessToken = result.accessToken
  return result.user
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${API_BASE}/api/auth/logout`, { method: 'POST', credentials: 'include' })
  } finally {
    accessToken = null
  }
}

export function fetchMe(): Promise<Me> {
  return apiFetch<Me>(ME_PATH)
}
