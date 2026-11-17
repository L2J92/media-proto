import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { LiveNotification } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useServerEvent } from '../lib/realtime'

/** 로그인 중이면 내 알림(SSE)을 구독해, 팔로우한 채널이 방송을 시작하면 토스트로 알린다. */
export function NotificationToaster() {
  const { me } = useAuth()
  const [toasts, setToasts] = useState<(LiveNotification & { key: number })[]>([])

  useServerEvent<LiveNotification>(me ? '/api/realtime/me' : null, 'notification', n => {
    const key = Date.now() + Math.random()
    setToasts(t => [...t.slice(-2), { ...n, key }])
    setTimeout(() => setToasts(t => t.filter(x => x.key !== key)), 8000)
  })

  if (toasts.length === 0) return null
  return (
    <div className="toasts" role="status">
      {toasts.map(t => (
        <Link key={t.key} to={`/broadcasts/${t.broadcastId}`} className="toast" onClick={() => setToasts(x => x.filter(y => y.key !== t.key))}>
          <span className="badge badge-live">LIVE</span>
          <span><b>{t.channelName}</b>님이 방송을 시작했어요<br /><span className="muted small">{t.title}</span></span>
        </Link>
      ))}
    </div>
  )
}
