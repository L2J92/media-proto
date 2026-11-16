import type { BroadcastStatus } from '../lib/api'

const LABEL: Record<BroadcastStatus, string> = { READY: '대기', LIVE: 'LIVE', ENDED: '종료' }

export function StatusBadge({ status }: { status: BroadcastStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{LABEL[status]}</span>
}
