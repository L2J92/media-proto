import { Link, useLocation } from 'react-router-dom'
import { useBroadcastSession } from '../lib/broadcastSession'

/** 송출 중에 다른 페이지에 있으면 상단에 방송 상태와 돌아가기·종료 버튼을 보여준다. */
export function LiveSessionBar() {
  const { session, end } = useBroadcastSession()
  const { pathname } = useLocation()
  if (!session) return null
  const studioPath = `/broadcasts/${session.broadcast.id}/studio`
  if (pathname === studioPath) return null
  return (
    <div className="live-bar" role="status">
      <span className="live-dot">● 송출 중</span>
      <span className="live-title">{session.broadcast.title}</span>
      <span className="muted small">👁 {session.viewers}{session.micMuted ? ' · 🔇 마이크 꺼짐' : ''}{session.reconnecting ? ' · 재연결 중…' : ''}</span>
      <span className="spacer" />
      <Link className="btn btn-sm" to={studioPath}>스튜디오로</Link>
      <button className="btn btn-sm btn-danger" onClick={() => { if (confirm('방송을 종료할까요?')) end() }}>종료</button>
    </div>
  )
}
