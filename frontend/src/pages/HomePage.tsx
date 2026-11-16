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
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')

  // 첫 페이지는 10초마다 새로고침해 방송 상태·시청자 수를 반영한다.
  useEffect(() => {
    let alive = true
    const load = () => api.broadcasts(status, 0).then(p => {
      if (!alive) return
      setItems(prev => {
        // 이미 더 불러온 항목은 유지하고 첫 페이지만 교체
        const rest = (prev ?? []).slice(p.size).filter(b => !p.items.some(n => n.id === b.id))
        return [...p.items, ...rest]
      })
      setHasMore(p.totalPages > 1)
    }).catch(e => setError(errorMessage(e)))
    load()
    const t = setInterval(load, 10_000)
    return () => { alive = false; clearInterval(t) }
  }, [status])

  async function loadMore() {
    setLoadingMore(true)
    try {
      const next = page + 1
      const p = await api.broadcasts(status, next)
      setItems(prev => [...(prev ?? []), ...p.items.filter(n => !(prev ?? []).some(b => b.id === n.id))])
      setPage(next)
      setHasMore(next + 1 < p.totalPages)
    } catch (e) { setError(errorMessage(e)) } finally { setLoadingMore(false) }
  }

  return (
    <>
      <div className="page-head">
        <h1>방송 목록</h1>
        <div className="segmented">
          {FILTERS.map(f => (
            <button key={f.label} className={status === f.value ? 'on' : ''} onClick={() => { if (status === f.value) return; setItems(null); setPage(0); setStatus(f.value) }}>{f.label}</button>
          ))}
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      {items === null ? <p className="muted"><span className="spinner">불러오는 중…</span></p> : items.length === 0 ? (
        <p className="muted">방송이 없습니다.</p>
      ) : (
        <>
          <div className="grid">
            {items.map(b => <BroadcastCard key={b.id} b={b} />)}
          </div>
          {hasMore && (
            <div className="more">
              <button className="btn" onClick={loadMore} disabled={loadingMore}>{loadingMore ? '불러오는 중…' : '더 보기'}</button>
            </div>
          )}
        </>
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
        <div className="muted small card-meta">
          <span>{b.owner.nickname} · {b.status === 'LIVE' ? `${timeAgo(b.startedAt)} 시작` : timeAgo(b.createdAt)}</span>
          {b.status === 'LIVE' && b.viewerCount != null && <span>👁 {b.viewerCount}</span>}
        </div>
      </div>
    </Link>
  )
}
