import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, errorMessage, type Broadcast } from '../lib/api'

/**
 * 종료된 방송을 "다시 방송하기": 같은 제목·설명으로 새 회차를 만들고 송출 화면으로 이동한다.
 * 방송 1회 = 1회차로 유지해 지난 회차의 채팅·후원·녹화 기록이 섞이지 않게 한다.
 */
export function RerunButton({ broadcast, className = 'btn btn-primary' }: { broadcast: Broadcast; className?: string }) {
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)

  async function rerun() {
    setBusy(true)
    try {
      const next = await api.createBroadcast(broadcast.title, broadcast.description ?? '')
      navigate(`/broadcasts/${next.id}/studio`)
    } catch (e) {
      alert(errorMessage(e))
      setBusy(false)
    }
  }

  return (
    <button className={className} onClick={rerun} disabled={busy}>
      {busy ? '준비 중…' : '↻ 다시 방송하기'}
    </button>
  )
}
