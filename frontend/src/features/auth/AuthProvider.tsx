import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchMe, loginWithGoogle, logout, refreshAccessToken, type Me } from '../../lib/api'
import { AuthContext, type AuthStatus } from './auth-context.ts'
import { useQueryClient } from '@tanstack/react-query'

export default function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [me, setMe] = useState<Me | null>(null)

  const loadMe = useCallback(async () => {
    try {
      setMe(await fetchMe())
      setStatus('authenticated')
    } catch {
      setMe(null)
      setStatus('anonymous')
    }
  }, [])

  // Khởi động app / F5: dùng refresh cookie để khôi phục phiên đăng nhập
  useEffect(() => {
    let cancelled = false
    refreshAccessToken().then((token) => {
      if (cancelled) return
      if (token) void loadMe()
      else setStatus('anonymous')
    })
    return () => {
      cancelled = true
    }
  }, [loadMe])

  const signIn = useCallback(
    async (googleIdToken: string) => {
      await loginWithGoogle(googleIdToken)
      await loadMe()
    },
    [loadMe],
  )

  const signOut = useCallback(async () => {
    await logout()
    window.google?.accounts.id.disableAutoSelect()
    setMe(null)
    setStatus('anonymous')
    queryClient.clear()
  }, [])

  const value = useMemo(() => ({ status, me, signIn, signOut }), [status, me, signIn, signOut])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
