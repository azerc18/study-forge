import { Link } from 'react-router'

export default function NotFoundPage() {
    return (
        <div className="grid min-h-screen place-items-center text-center">
            <div>
                <p className="text-5xl font-bold">404</p>
                <Link to="/" className="mt-4 inline-block text-blue-600 underline">Back to home</Link>
            </div>
        </div>
    )
}