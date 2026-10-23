import { Outlet } from 'react-router'
import NotFoundPage from '../../pages/NotFoundPage.tsx'
import { useAuth } from './auth-context'

/**
 * Đặt BÊN TRONG nhóm RequireAuth (đã chắc đăng nhập). Không phải admin → hiện trang 404,
 * không để lộ là có trang admin. Bảo vệ thật nằm ở backend (403), đây chỉ là giao diện.
 */
export default function RequireAdmin() {
  const { me } = useAuth()
  if (me?.user.role !== 'ADMIN') return <NotFoundPage />
  return <Outlet />
}
