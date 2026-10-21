// API client: giữ access token trong bộ nhớ, tự gọi /refresh khi gặp 401.

const API_BASE: string = import.meta.env.VITE_API_URL ?? ''

// Đổi thành '/api/user/me' nếu backend giữ đường dẫn số ít
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

  constructor(status: number, code: string | undefined, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

type RequestOptions = Omit<RequestInit, 'headers'> & { headers?: Record<string, string> }

let accessToken: string | null = null
let refreshing: Promise<string | null> | null = null

async function parse<T>(res: Response): Promise<T> {
  const body = (await res.json().catch(() => null)) as ApiResponse<T> | null
  if (!res.ok || !body) {
    throw new ApiError(res.status, body?.errorCode, body?.message ?? res.statusText)
  }
  return body.result as T
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
        'Content-Type': 'application/json',
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
