import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './lib/auth'
import { BroadcastSessionProvider } from './lib/broadcastSession'
import { Layout } from './components/Layout'
import { RequireAuth } from './components/RequireAuth'
import { HomePage } from './pages/HomePage'
import { LoginPage, SignupPage } from './pages/AuthPages'
import { BroadcastFormPage } from './pages/BroadcastFormPage'
import { MyBroadcastsPage } from './pages/MyBroadcastsPage'
import { RecordingDetailPage, RecordingsPage } from './pages/RecordingPages'
import { DonationFailPage, DonationSuccessPage } from './pages/DonationResultPages'

// WebRTC(livekit-client)가 필요한 화면은 따로 불러와 첫 화면 로딩을 가볍게 한다.
const BroadcastDetailPage = lazy(() => import('./pages/BroadcastDetailPage').then(m => ({ default: m.BroadcastDetailPage })))
const StudioPage = lazy(() => import('./pages/StudioPage').then(m => ({ default: m.StudioPage })))

export default function App() {
  return (
    <AuthProvider>
      <BroadcastSessionProvider>
      <BrowserRouter>
        <Suspense fallback={<p className="container muted"><span className="spinner">불러오는 중…</span></p>}>
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
            <Route path="/donations/success" element={<RequireAuth><DonationSuccessPage /></RequireAuth>} />
            <Route path="/donations/fail" element={<DonationFailPage />} />
            <Route path="/recordings" element={<RecordingsPage />} />
            <Route path="/recordings/:id" element={<RecordingDetailPage />} />
            <Route path="*" element={<p className="muted">페이지를 찾을 수 없습니다.</p>} />
          </Route>
        </Routes>
        </Suspense>
      </BrowserRouter>
      </BroadcastSessionProvider>
    </AuthProvider>
  )
}
