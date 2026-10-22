// Kiểu tối thiểu cho Google Identity Services (script tải từ accounts.google.com/gsi/client)
export {}

declare global {
  interface GoogleCredentialResponse {
    credential: string
  }

  interface GoogleIdConfiguration {
    client_id: string
    callback: (response: GoogleCredentialResponse) => void
    ux_mode?: 'popup' | 'redirect'
    auto_select?: boolean
  }

  interface GoogleButtonOptions {
    theme?: 'outline' | 'filled_blue' | 'filled_black'
    size?: 'large' | 'medium' | 'small'
    shape?: 'rectangular' | 'pill' | 'circle' | 'square'
    text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin'
    width?: number
    locale?: string
  }

  interface Window {
    google?: {
      accounts: {
        id: {
          initialize(config: GoogleIdConfiguration): void
          renderButton(parent: HTMLElement, options: GoogleButtonOptions): void
          disableAutoSelect(): void
        }
      }
    }
  }
}
