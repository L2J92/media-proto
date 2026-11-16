import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import type { Room } from 'livekit-client'
import { api, errorMessage, type Broadcast, type Recording } from '../lib/api'
import { useAuth } from '../lib/auth'
import { StatusBadge } from '../components/StatusBadge'
import { LivePlayer } from '../components/LivePlayer'
import { ChatPanel } from '../components/ChatPanel'
import { DonationBox } from '../components/DonationBox'
import { duration, timeAgo } from '../lib/format'

export function BroadcastDetailPage() {
  const { id } = useParams()
  const broadcastId = Number(id)
  const { me } = useAuth()
  const navigate = useNavigate()
  const [b, setB] = useState<Broadcast | null>(null)
  const [recordings, setRecordings] = useState<Recording[]>([])
  const [room, setRoom] = useState<Room | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api.broadcast(broadcastId).then(setB).catch(e => setError(errorMessage(e)))
    api.broadcastRecordings(broadcastId).then(setRecordings).catch(() => {})
  }, [broadcastId])

  useEffect(() => { load() }, [load])

  // 대기 중인 방송은 시작되면 자동으로 플레이어를 띄운다.
  useEffect(() => {
    if (b?.status !== 'READY') return
    const t = setInterval(load, 5000)
    return () => clearInterval(t)
  }, [b?.status, load])

  if (!b) return <p className={error ? 'error' : 'muted'}>{error || '불러오는 중…'}</p>
  const isOwner = me?.id === b.owner.id

  async function remove() {
    if (!confirm(b!.status === 'LIVE' ? '방송 중입니다. 종료하고 삭제할까요?' : '방송을 삭제할까요?')) return
    try { await api.deleteBroadcast(broadcastId); navigate('/') } catch (e) { setError(errorMessage(e)) }
  }

  return (
    <div className="watch">
      <section className="watch-main">
        {b.status === 'LIVE' ? (
          <LivePlayer broadcastId={broadcastId} onRoom={setRoom} onEnded={load} />
        ) : (
          <div className="player"><div className="player-overlay">
            {b.status === 'READY' ? '방송 시작 전입니다. 시작되면 자동으로 재생됩니다.' : '종료된 방송입니다.'}
          </div></div>
        )}
        <div className="info">
          <h1>{b.title} <StatusBadge status={b.status} /></h1>
          <p className="muted small">
            {b.owner.nickname} · {b.status === 'LIVE' ? `${timeAgo(b.startedAt)} 시작` : b.status === 'ENDED' ? `${timeAgo(b.endedAt)} 종료` : `${timeAgo(b.createdAt)} 등록`}
            {b.source && ` · ${b.source === 'SCREEN' ? 'PC 화면' : '카메라'} 방송`}
          </p>
          {b.description && <p className="desc">{b.description}</p>}
          <DonationBox broadcast={b} room={room} />
          {error && <p className="error">{error}</p>}
          {isOwner && (
            <div className="owner-actions">
              {b.status !== 'ENDED' && <Link className="btn btn-primary" to={`/broadcasts/${b.id}/studio`}>{b.status === 'LIVE' ? '송출 화면' : '송출하러 가기'}</Link>}
              <Link className="btn" to={`/broadcasts/${b.id}/edit`}>정보 수정</Link>
              <button className="btn btn-danger" onClick={remove}>삭제</button>
            </div>
          )}
          {recordings.length > 0 && (
            <div className="recordings-inline">
              <h3>다시보기</h3>
              {recordings.map(r => (
                <div key={r.id}>
                  {r.status === 'COMPLETED'
                    ? <Link to={`/recordings/${r.id}`}>▶ 녹화 영상 {duration(r.durationMillis)}</Link>
                    : <span className="muted small">{r.status === 'RECORDING' ? '녹화 중 / 처리 중…' : '녹화 실패'}</span>}
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
      <ChatPanel broadcastId={broadcastId} room={room} />
    </div>
  )
}
