import { useEffect, useRef } from 'react'

const GSI_SRC = 'https://accounts.google.com/gsi/client'
let gsiLoading: Promise<void> | null = null

function loadGoogleScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve()
  if (!gsiLoading) {
    gsiLoading = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = GSI_SRC
      script.async = true
      script.defer = true
      script.onload = () => resolve()
      script.onerror = () => {
        gsiLoading = null
        reject(new Error('Failed to load Google Sign-In'))
      }
      document.head.appendChild(script)
    })
  }
  return gsiLoading
}

interface Props {
  onCredential: (idToken: string) => void
  onError?: (message: string) => void
}

export default function GoogleSignInButton({ onCredential, onError }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  // Giữ callback mới nhất mà không phải khởi tạo lại Google mỗi lần render
  const onCredentialRef = useRef(onCredential)
  const onErrorRef = useRef(onError)

  useEffect(() => {
    onCredentialRef.current = onCredential
    onErrorRef.current = onError
  }, [onCredential, onError])

  useEffect(() => {
    const clientId: string | undefined = import.meta.env.VITE_GOOGLE_CLIENT_ID
    if (!clientId) {
      onErrorRef.current?.(
          import.meta.env.DEV
              ? 'Missing VITE_GOOGLE_CLIENT_ID in .env.local'
              : 'Sign-in is temporarily unavailable.',
      )
      return
    }

    let active = true
    loadGoogleScript()
      .then(() => {
        const google = window.google
        if (!active || !containerRef.current || !google) return
        google.accounts.id.initialize({
          client_id: clientId,
          callback: (response) => onCredentialRef.current(response.credential),
        })
        google.accounts.id.renderButton(containerRef.current, {
          theme: 'outline',
          size: 'large',
          shape: 'pill',
          text: 'continue_with',
          width: 280,
        })
      })
      .catch(() => onErrorRef.current?.('Could not load Google Sign-In. Check your connection.'))

    return () => {
      active = false
    }
  }, [])

  return <div ref={containerRef} className="flex min-h-11 justify-center" />
}
