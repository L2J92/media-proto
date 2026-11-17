import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, errorMessage, type Broadcast, type Channel } from '../lib/api'
import { FollowButton } from '../components/FollowButton'
import { BroadcastCard } from './HomePage'

export function ChannelPage() {
  const { handle = '' } = useParams()
  const navigate = useNavigate()
  const [channel, setChannel] = useState<Channel | null>(null)
  const [broadcasts, setBroadcasts] = useState<Broadcast[]>([])
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState({ handle: '', name: '', description: '' })
  const [count, setCount] = useState(0)

  const load = useCallback(() => {
    api.channel(handle).then(c => { setChannel(c); setCount(c.followerCount); setError('') }).catch(e => setError(errorMessage(e)))
    api.channelBroadcasts(handle).then(p => setBroadcasts(p.items)).catch(() => {})
  }, [handle])
  useEffect(() => { load() }, [load])

  async function save(e: React.FormEvent) {
    e.preventDefault()
    try {
      const c = await api.updateMyChannel(form.handle, form.name, form.description)
      setEditing(false)
      if (c.handle !== handle) navigate(`/c/${c.handle}`, { replace: true })
      else setChannel(c)
    } catch (err) { setError(errorMessage(err)) }
  }

  if (!channel) return <p className={error ? 'error' : 'muted'}>{error || '불러오는 중…'}</p>
  const past = broadcasts.filter(b => b.status !== 'LIVE')
  return (
    <div className="channel">
      <header className="channel-head">
        <div className="avatar">{channel.name.slice(0, 1)}</div>
        <div className="channel-meta">
          <h1>{channel.name} {channel.live && <span className="badge badge-live">LIVE</span>}</h1>
          <p className="muted small">@{channel.handle} · 팔로워 {count.toLocaleString()}명</p>
          {channel.description && <p className="desc">{channel.description}</p>}
        </div>
        <div className="channel-actions">
          {channel.mine
            ? <button className="btn btn-sm" onClick={() => { setForm({ handle: channel.handle, name: channel.name, description: channel.description ?? '' }); setEditing(e => !e) }}>채널 편집</button>
            : <FollowButton handle={channel.handle} initial={channel.following} onChange={(_, c) => setCount(c)} />}
        </div>
      </header>

      {editing && (
        <form className="form channel-edit" onSubmit={save}>
          <label>채널 주소 (영문 소문자·숫자·_)<input value={form.handle} onChange={e => setForm({ ...form, handle: e.target.value.toLowerCase() })} /></label>
          <label>채널 이름<input value={form.name} maxLength={50} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
          <label>소개<textarea rows={3} maxLength={500} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></label>
          {error && <p className="error small">{error}</p>}
          <button className="btn btn-primary">저장</button>
        </form>
      )}

      {channel.live && (
        <Link to={`/broadcasts/${channel.live.id}`} className="live-now">
          <span className="badge badge-live">LIVE</span>
          <b>{channel.live.title}</b>
          {channel.live.viewerCount != null && <span className="muted small">👁 {channel.live.viewerCount}</span>}
          <span className="muted small">지금 시청하기 →</span>
        </Link>
      )}

      <h3>지난 방송</h3>
      {past.length === 0 ? <p className="muted small">아직 지난 방송이 없어요.</p> : (
        <div className="grid">{past.map(b => <BroadcastCard key={b.id} b={b} />)}</div>
      )}
    </div>
  )
}
