import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { RoomEvent, type Room } from 'livekit-client'
import { api, errorMessage, type Broadcast, type Donation, type DonationSummary } from '../lib/api'
import { useAuth } from '../lib/auth'
import { requestCardPayment } from '../lib/toss'

const PRESETS = [1000, 5000, 10000, 50000]
const won = (n: number) => `${n.toLocaleString('ko-KR')}원`

export function DonationBox({ broadcast, room }: { broadcast: Broadcast; room: Room | null }) {
  const { me } = useAuth()
  const [summary, setSummary] = useState<DonationSummary | null>(null)
  const [open, setOpen] = useState(false)
  const [amount, setAmount] = useState(5000)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [flash, setFlash] = useState<Donation | null>(null)

  useEffect(() => {
    api.donations(broadcast.id).then(setSummary).catch(() => {})
  }, [broadcast.id])

  // 다른 시청자의 후원이 적립되면 실시간으로 합계를 갱신하고 알림을 띄운다.
  useEffect(() => {
    if (!room) return
    const handler = (payload: Uint8Array, _p: unknown, _k: unknown, topic?: string) => {
      if (topic !== 'donation') return
      try {
        const d = JSON.parse(new TextDecoder().decode(payload)) as Donation
        setSummary(prev => prev && !prev.recent.some(r => r.id === d.id)
          ? { totalAmount: prev.totalAmount + d.amount, count: prev.count + 1, recent: [d, ...prev.recent].slice(0, 20) }
          : prev)
        setFlash(d)
        setTimeout(() => setFlash(cur => (cur?.id === d.id ? null : cur)), 6000)
      } catch { /* 무시 */ }
    }
    room.on(RoomEvent.DataReceived, handler)
    return () => { room.off(RoomEvent.DataReceived, handler) }
  }, [room])

  async function donate(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const order = await api.prepareDonation(broadcast.id, amount, message)
      // 결제창으로 이동한다. 성공하면 /donations/success 로 돌아온다.
      await requestCardPayment(order, broadcast.id)
    } catch (err) {
      // 사용자가 결제창을 닫은 경우도 여기로 온다
      const msg = errorMessage(err)
      setError(msg.includes('취소') ? '' : msg)
    } finally {
      setBusy(false)
    }
  }

  const isOwner = me?.id === broadcast.owner.id
  return (
    <div className="donation">
      {flash && (
        <div className="donation-flash" role="status">
          💰 <b>{flash.donorNickname}</b>님이 <b>{won(flash.amount)}</b> 후원!{flash.message && <> “{flash.message}”</>}
        </div>
      )}
      <div className="donation-head">
        <div>
          <span className="muted small">누적 후원</span>{' '}
          <b className="donation-total">{won(summary?.totalAmount ?? 0)}</b>
          <span className="muted small"> · {summary?.count ?? 0}건</span>
        </div>
        {!isOwner && (
          me
            ? <button className="btn btn-primary btn-sm" onClick={() => setOpen(o => !o)}>💰 후원하기</button>
            : <Link className="btn btn-sm" to={`/login?next=/broadcasts/${broadcast.id}`}>로그인 후 후원</Link>
        )}
      </div>

      {open && !isOwner && me && (
        <form className="donation-form" onSubmit={donate}>
          <div className="presets">
            {PRESETS.map(p => (
              <button type="button" key={p} className={`btn btn-sm ${amount === p ? 'btn-primary' : ''}`} onClick={() => setAmount(p)}>
                {won(p)}
              </button>
            ))}
            <input type="number" min={1000} max={1000000} step={1000} value={amount}
              onChange={e => setAmount(Number(e.target.value))} aria-label="직접 입력" />
          </div>
          <input placeholder="응원 메시지 (선택, 100자)" maxLength={100} value={message} onChange={e => setMessage(e.target.value)} />
          {error && <p className="error small">{error}</p>}
          <button className="btn btn-primary" disabled={busy || amount < 1000}>
            {busy ? '결제창 여는 중…' : `${won(amount)} 결제하기`}
          </button>
          <p className="muted small">토스페이먼츠 <b>테스트 결제</b>로 실제 돈은 나가지 않습니다.</p>
        </form>
      )}

      {summary && summary.recent.length > 0 && (
        <ul className="donation-list">
          {summary.recent.slice(0, 5).map(d => (
            <li key={d.id}><b>{d.donorNickname}</b> {won(d.amount)}{d.message && <span className="muted"> · {d.message}</span>}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
