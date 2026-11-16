import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, errorMessage, type Broadcast, type BroadcastStatus } from '../lib/api'
import { StatusBadge } from '../components/StatusBadge'
import { timeAgo } from '../lib/format'

const FILTERS: { label: string; value?: BroadcastStatus }[] = [
  { label: '전체' }, { label: '방송 중', value: 'LIVE' }, { label: '대기', value: 'READY' }, { label: '종료', value: 'ENDED' },
]

export function HomePage() {
  const [status, setStatus] = useState<BroadcastStatus | undefined>()
  const [items, setItems] = useState<Broadcast[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let alive = true
    const load = () => api.broadcasts(status).then(p => alive && setItems(p.items)).catch(e => setError(errorMessage(e)))
    load()
    const t = setInterval(load, 10_000) // 방송 상태 변화 반영
    return () => { alive = false; clearInterval(t) }
  }, [status])

  return (
    <>
      <div className="page-head">
        <h1>방송 목록</h1>
        <div className="segmented">
          {FILTERS.map(f => (
            <button key={f.label} className={status === f.value ? 'on' : ''} onClick={() => setStatus(f.value)}>{f.label}</button>
          ))}
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      {items === null ? <p className="muted">불러오는 중…</p> : items.length === 0 ? (
        <p className="muted">방송이 없습니다.</p>
      ) : (
        <div className="grid">
          {items.map(b => <BroadcastCard key={b.id} b={b} />)}
        </div>
      )}
    </>
  )
}

export function BroadcastCard({ b }: { b: Broadcast }) {
  return (
    <Link to={`/broadcasts/${b.id}`} className={`card ${b.status === 'LIVE' ? 'card-live' : ''}`}>
      <div className="thumb">
        <span className="thumb-icon">{b.source === 'CAMERA' ? '📷' : b.source === 'SCREEN' ? '🖥' : '📺'}</span>
        <StatusBadge status={b.status} />
      </div>
      <div className="card-body">
        <div className="card-title">{b.title}</div>
        <div className="muted small">
          {b.owner.nickname} · {b.status === 'LIVE' ? `${timeAgo(b.startedAt)} 시작` : timeAgo(b.createdAt)}
        </div>
      </div>
    </Link>
  )
}
