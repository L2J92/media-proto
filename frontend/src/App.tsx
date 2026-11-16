import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './lib/auth'
import { Layout } from './components/Layout'
import { RequireAuth } from './components/RequireAuth'
import { HomePage } from './pages/HomePage'
import { LoginPage, SignupPage } from './pages/AuthPages'
import { BroadcastFormPage } from './pages/BroadcastFormPage'
import { BroadcastDetailPage } from './pages/BroadcastDetailPage'
import { StudioPage } from './pages/StudioPage'
import { MyBroadcastsPage } from './pages/MyBroadcastsPage'
import { RecordingDetailPage, RecordingsPage } from './pages/RecordingPages'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/signup" element={<SignupPage />} />
            <Route path="/broadcasts/new" element={<RequireAuth><BroadcastFormPage /></RequireAuth>} />
            <Route path="/broadcasts/:id" element={<BroadcastDetailPage />} />
            <Route path="/broadcasts/:id/edit" element={<RequireAuth><BroadcastFormPage /></RequireAuth>} />
            <Route path="/broadcasts/:id/studio" element={<RequireAuth><StudioPage /></RequireAuth>} />
            <Route path="/me/broadcasts" element={<RequireAuth><MyBroadcastsPage /></RequireAuth>} />
            <Route path="/recordings" element={<RecordingsPage />} />
            <Route path="/recordings/:id" element={<RecordingDetailPage />} />
            <Route path="*" element={<p className="muted">페이지를 찾을 수 없습니다.</p>} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
