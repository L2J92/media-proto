import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'

/** 팔로우 버튼. 누르는 즉시 화면에 반영하고 실패하면 되돌린다. */
export function FollowButton({ handle, initial, count, onChange }: {
  handle: string; initial: boolean; count?: number; onChange?: (following: boolean, count: number) => void
}) {
  const { me } = useAuth()
  const navigate = useNavigate()
  const [following, setFollowing] = useState(initial)
  const [busy, setBusy] = useState(false)

  async function toggle() {
    if (!me) { navigate(`/login?next=${encodeURIComponent(location.pathname)}`); return }
    const next = !following
    setFollowing(next)
    setBusy(true)
    try {
      const r = next ? await api.follow(handle) : await api.unfollow(handle)
      setFollowing(r.following)
      onChange?.(r.following, r.followerCount)
    } catch (e) {
      setFollowing(!next)
      alert(errorMessage(e))
    } finally { setBusy(false) }
  }

  return (
    <button className={`btn btn-sm ${following ? '' : 'btn-primary'}`} onClick={toggle} disabled={busy}>
      {following ? '✓ 팔로잉' : '+ 팔로우'}{count != null && <span className="muted"> · {count.toLocaleString()}</span>}
    </button>
  )
}
