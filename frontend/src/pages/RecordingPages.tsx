import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, errorMessage, type Recording } from '../lib/api'
import { duration, timeAgo } from '../lib/format'

export function RecordingsPage() {
  const [items, setItems] = useState<Recording[] | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { api.recordings().then(setItems).catch(e => setError(errorMessage(e))) }, [])
  return (
    <>
      <div className="page-head"><h1>다시보기</h1></div>
      {error && <p className="error">{error}</p>}
      {items?.length === 0 && <p className="muted">녹화된 영상이 없습니다.</p>}
      <div className="grid">
        {items?.map(r => (
          <Link key={r.id} to={`/recordings/${r.id}`} className="card">
            <div className="thumb"><span className="thumb-icon">▶</span><span className="badge">{duration(r.durationMillis)}</span></div>
            <div className="card-body">
              <div className="card-title">{r.title}</div>
              <div className="muted small">{r.ownerNickname} · {timeAgo(r.recordedAt)}</div>
            </div>
          </Link>
        ))}
      </div>
    </>
  )
}

export function RecordingDetailPage() {
  const { id } = useParams()
  const [r, setR] = useState<Recording | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { api.recording(Number(id)).then(setR).catch(e => setError(errorMessage(e))) }, [id])
  if (!r) return <p className={error ? 'error' : 'muted'}>{error || '불러오는 중…'}</p>
  return (
    <div className="watch-main">
      <div className="player">
        {r.videoUrl ? <video src={r.videoUrl} controls playsInline /> : <div className="player-overlay">영상을 준비 중입니다.</div>}
      </div>
      <div className="info">
        <h1>{r.title}</h1>
        <p className="muted small">{r.ownerNickname} · {new Date(r.recordedAt).toLocaleString('ko-KR')} · {duration(r.durationMillis)}</p>
        <Link to={`/broadcasts/${r.broadcastId}`} className="small">원본 방송 정보 →</Link>
      </div>
    </div>
  )
}
