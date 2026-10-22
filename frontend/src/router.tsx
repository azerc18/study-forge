import { createBrowserRouter } from 'react-router'
import RootLayout from './layouts/RootLayout'
import RequireAuth from './features/auth/RequireAuth'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import ProfilePage from './pages/ProfilePage'
import NotFoundPage from './pages/NotFoundPage'
import DocumentsPage from './pages/DocumentsPage'
import DocumentDetailPage from './pages/DocumentDetailPage'

export const router = createBrowserRouter([
    {
        path: '/',
        element: <RootLayout />,
        children: [
            { index: true, element: <HomePage /> },
            { path: 'login', element: <LoginPage /> },
            {
                // Các route cần đăng nhập đặt trong nhóm này
                element: <RequireAuth />,
                children: [
                    { path: 'profile', element: <ProfilePage /> },
                    { path: 'documents', element: <DocumentsPage /> },
                    { path: 'documents/:id', element: <DocumentDetailPage /> },
                ],
            },
        ],
    },
    { path: '*', element: <NotFoundPage /> },
])
