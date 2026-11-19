import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, errorMessage, type Broadcast } from '../lib/api'
import { BroadcastCard } from './HomePage'
import { RerunButton } from '../components/RerunButton'

export function MyBroadcastsPage() {
  const [items, setItems] = useState<Broadcast[] | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { api.myBroadcasts().then(setItems).catch(e => setError(errorMessage(e))) }, [])
  return (
    <>
      <div className="page-head">
        <h1>내 방송</h1>
        <Link to="/broadcasts/new" className="btn btn-primary">새 방송</Link>
      </div>
      {error && <p className="error">{error}</p>}
      {items?.length === 0 && <p className="muted">아직 만든 방송이 없습니다.</p>}
      <div className="grid">
        {items?.map(b => (
          <div key={b.id} className="my-item">
            <BroadcastCard b={b} />
            {b.status === 'ENDED'
              ? <RerunButton broadcast={b} className="btn btn-sm" />
              : <Link className="btn btn-sm btn-primary" to={`/broadcasts/${b.id}/studio`}>{b.status === 'LIVE' ? '송출 화면' : '송출하러 가기'}</Link>}
          </div>
        ))}
      </div>
    </>
  )
}
