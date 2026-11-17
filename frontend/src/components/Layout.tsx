import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { useBroadcastSession } from '../lib/broadcastSession'
import { useWallet } from '../lib/wallet'
import { points } from '../lib/points'
import { LiveSessionBar } from './LiveSessionBar'
import { NotificationToaster } from './NotificationToaster'

export function Layout() {
  const { me, logout } = useAuth()
  const live = useBroadcastSession()
  const wallet = useWallet()
  const navigate = useNavigate()
  return (
    <>
      <header className="header">
        <Link to="/" className="logo">● LIVE</Link>
        <nav>
          <NavLink to="/" end>방송</NavLink>
          <NavLink to="/recordings">다시보기</NavLink>
          {me && <NavLink to="/following">팔로잉</NavLink>}
          {me && <NavLink to="/me/broadcasts">내 방송</NavLink>}
        </nav>
        <div className="header-right">
          {me ? (
            <>
              <Link to="/broadcasts/new" className="btn btn-primary btn-sm">방송하기</Link>
              <Link to="/wallet" className="wallet-chip" title="내 지갑">💎 {points(wallet.balance ?? 0)}</Link>
              <span className="muted">{me.nickname}</span>
              <button className="btn btn-sm" onClick={async () => {
                if (live.session) {
                  if (!confirm('송출 중인 방송을 종료하고 로그아웃할까요?')) return
                  await live.end()
                }
                await logout(); navigate('/')
              }}>로그아웃</button>
            </>
          ) : (
            <>
              <Link to="/login" className="btn btn-sm">로그인</Link>
              <Link to="/signup" className="btn btn-primary btn-sm">회원가입</Link>
            </>
          )}
        </div>
      </header>
      <LiveSessionBar />
      <NotificationToaster />
      <main className="container">
        <Outlet />
      </main>
    </>
  )
}
