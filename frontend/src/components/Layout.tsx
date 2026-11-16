import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'

export function Layout() {
  const { me, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <>
      <header className="header">
        <Link to="/" className="logo">● LIVE</Link>
        <nav>
          <NavLink to="/" end>방송</NavLink>
          <NavLink to="/recordings">다시보기</NavLink>
          {me && <NavLink to="/me/broadcasts">내 방송</NavLink>}
        </nav>
        <div className="header-right">
          {me ? (
            <>
              <Link to="/broadcasts/new" className="btn btn-primary btn-sm">방송하기</Link>
              <span className="muted">{me.nickname}</span>
              <button className="btn btn-sm" onClick={async () => { await logout(); navigate('/') }}>로그아웃</button>
            </>
          ) : (
            <>
              <Link to="/login" className="btn btn-sm">로그인</Link>
              <Link to="/signup" className="btn btn-primary btn-sm">회원가입</Link>
            </>
          )}
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </>
  )
}
