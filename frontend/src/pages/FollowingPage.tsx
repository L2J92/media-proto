import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, errorMessage, type FollowingItem } from '../lib/api'

export function FollowingPage() {
  const [items, setItems] = useState<FollowingItem[] | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    const load = () => api.following().then(setItems).catch(e => setError(errorMessage(e)))
    load()
    const t = setInterval(load, 15_000)
    return () => clearInterval(t)
  }, [])
  return (
    <>
      <div className="page-head"><h1>팔로잉</h1></div>
      {error && <p className="error">{error}</p>}
      {items?.length === 0 && <p className="muted">팔로우한 채널이 없어요. 방송 화면이나 채널에서 팔로우해 보세요.</p>}
      <ul className="following-list">
        {items?.map(i => (
          <li key={i.handle}>
            <Link to={`/c/${i.handle}`} className="following-channel">
              <span className="avatar sm">{i.name.slice(0, 1)}</span>
              <span><b>{i.name}</b><span className="muted small"> @{i.handle}</span></span>
            </Link>
            {i.live ? (
              <Link to={`/broadcasts/${i.live.id}`} className="following-live">
                <span className="badge badge-live">LIVE</span> {i.live.title}
                {i.live.viewerCount != null && <span className="muted small"> · 👁 {i.live.viewerCount}</span>}
              </Link>
            ) : <span className="muted small">오프라인</span>}
          </li>
        ))}
      </ul>
    </>
  )
}
