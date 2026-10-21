import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './auth-context'

/** Bọc các route cần đăng nhập. Chưa đăng nhập → chuyển sang /login, nhớ trang đang muốn vào. */
export default function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="flex flex-1 items-center justify-center">
        <div className="size-6 animate-spin rounded-full border-2 border-brand border-t-brand-ink" />
      </div>
    )
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}
