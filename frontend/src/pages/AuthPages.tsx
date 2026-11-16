import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'

function useAfterLogin() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const next = params.get('next')
  return () => navigate(next && next.startsWith('/') ? next : '/', { replace: true })
}

export function LoginPage() {
  const { setMe } = useAuth()
  const done = useAfterLogin()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    try { setMe(await api.login(email, password)); done() } catch (err) { setError(errorMessage(err)) }
  }

  async function demo(account: 'STREAMER' | 'STREAMER2' | 'VIEWER') {
    try { setMe(await api.demoLogin(account)); done() } catch (err) { setError(errorMessage(err)) }
  }

  return (
    <div className="auth">
      <h1>로그인</h1>
      <form onSubmit={submit} className="form">
        <label>이메일<input type="email" value={email} onChange={e => setEmail(e.target.value)} required /></label>
        <label>비밀번호<input type="password" value={password} onChange={e => setPassword(e.target.value)} required /></label>
        {error && <p className="error">{error}</p>}
        <button className="btn btn-primary">로그인</button>
      </form>
      <div className="demo">
        <p className="muted small">평가용 테스트 계정으로 바로 로그인</p>
        <div className="demo-buttons">
          <button className="btn" onClick={() => demo('STREAMER')}>방송자 계정</button>
          <button className="btn" onClick={() => demo('STREAMER2')}>방송자2 계정</button>
          <button className="btn" onClick={() => demo('VIEWER')}>시청자 계정</button>
        </div>
      </div>
      <p className="muted small">계정이 없나요? <Link to="/signup">회원가입</Link></p>
    </div>
  )
}

export function SignupPage() {
  const { setMe } = useAuth()
  const done = useAfterLogin()
  const [form, setForm] = useState({ email: '', password: '', nickname: '' })
  const [error, setError] = useState('')

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    try { setMe(await api.signup(form.email, form.password, form.nickname)); done() } catch (err) { setError(errorMessage(err)) }
  }

  return (
    <div className="auth">
      <h1>회원가입</h1>
      <form onSubmit={submit} className="form">
        <label>이메일<input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} required /></label>
        <label>비밀번호 (8자 이상)<input type="password" minLength={8} value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} required /></label>
        <label>닉네임<input value={form.nickname} minLength={2} maxLength={30} onChange={e => setForm({ ...form, nickname: e.target.value })} required /></label>
        {error && <p className="error">{error}</p>}
        <button className="btn btn-primary">가입하기</button>
      </form>
      <p className="muted small">이미 계정이 있나요? <Link to="/login">로그인</Link></p>
    </div>
  )
}
