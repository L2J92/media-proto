import { useCallback, useEffect, useState } from 'react'
import { api, errorMessage, type Recording } from '../lib/api'
import { duration } from '../lib/format'

/** 송출 스튜디오: 방송자가 녹화를 시작·중지한다. 중지하면 저장이 끝나는 대로 다시보기에 올라간다. */
export function RecordingControl({ broadcastId }: { broadcastId: number }) {
  const [recs, setRecs] = useState<Recording[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [since, setSince] = useState(0)

  const load = useCallback(() => api.broadcastRecordings(broadcastId).then(setRecs).catch(() => {}), [broadcastId])
  useEffect(() => {
    load()
    const t = setInterval(load, 10_000)
    return () => clearInterval(t)
  }, [load])

  const active = recs.find(r => r.status === 'RECORDING' && !r.stopRequested)
  const saving = recs.filter(r => r.status === 'RECORDING' && r.stopRequested).length
  const done = recs.filter(r => r.status === 'COMPLETED').length

  // 녹화 경과 시간 표시
  useEffect(() => {
    if (!active) return
    const start = new Date(active.recordedAt).getTime()
    const tick = () => setSince(Date.now() - start)
    tick()
    const t = setInterval(tick, 1000)
    return () => clearInterval(t)
  }, [active])

  async function toggle() {
    setBusy(true); setError('')
    try {
      if (active) await api.stopRecording(broadcastId)
      else await api.startRecording(broadcastId)
      await load()
    } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <div className="rec-control">
      {active ? <span className="rec-dot">● REC {duration(since) || '0:00'}</span> : <span className="muted small">녹화 꺼짐</span>}
      <button className={`btn btn-sm ${active ? 'btn-danger' : ''}`} onClick={toggle} disabled={busy}>
        {busy ? '처리 중…' : active ? '■ 녹화 중지' : '● 녹화 시작'}
      </button>
      {saving > 0 && <span className="muted small">저장 중 {saving}건…</span>}
      {done > 0 && <span className="muted small">다시보기 {done}건 저장됨</span>}
      {error && <span className="error small">{error}</span>}
    </div>
  )
}
