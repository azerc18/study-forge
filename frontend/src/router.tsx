import { createBrowserRouter } from 'react-router'
import RootLayout from './layouts/RootLayout.tsx'
import RequireAuth from './features/auth/RequireAuth'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import ProfilePage from './pages/ProfilePage'
import NotFoundPage from './pages/NotFoundPage'
import DocumentsPage from './pages/DocumentsPage'
import DocumentDetailPage from './pages/DocumentDetailPage.tsx'
import QuizSetsPage from './pages/QuizSetsPage.tsx'
import QuizSetPage from './pages/QuizSetPage.tsx'
import ExamPage from './pages/ExamPage'
import AdminPage from './pages/AdminPage'
import RequireAdmin from './features/auth/RequireAdmin'

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
                    { path: 'quiz-sets/:id', element: <QuizSetPage /> },
                    { path: 'exams/:id', element: <ExamPage /> },
                    { path: 'profile', element: <ProfilePage /> },
                    { path: 'documents', element: <DocumentsPage /> },
                    { path: 'documents/:id', element: <DocumentDetailPage /> },
                    { path: 'quiz-sets', element: <QuizSetsPage /> },
                    { path: 'quiz-sets/:id', element: <QuizSetPage /> },
                    {
                        // Chỉ ADMIN; người khác thấy trang 404
                        element: <RequireAdmin />,
                        children: [{ path: 'admin', element: <AdminPage /> }],
                    },
                ],
            },
        ],
    },
    { path: '*', element: <NotFoundPage /> },
])
