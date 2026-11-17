export type BroadcastStatus = 'READY' | 'LIVE' | 'ENDED'
export type BroadcastSource = 'SCREEN' | 'CAMERA'

export interface Me { id: number; email: string; nickname: string }
export interface Broadcast {
  id: number
  title: string
  description: string | null
  status: BroadcastStatus
  source: BroadcastSource | null
  owner: { id: number; nickname: string; channelHandle: string | null }
  createdAt: string
  startedAt: string | null
  endedAt: string | null
  /** 방송 중일 때 시청자 수 (집계 실패 시 null) */
  viewerCount: number | null
}
export interface Page<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface Connection { serverUrl: string; token: string; roomName: string; identity: string; role: 'publisher' | 'viewer' }
export interface Chat {
  id: number; broadcastId: number; authorId: number; authorNickname: string
  content: string; edited: boolean; createdAt: string
  /** 화면에서 먼저 보여주고 서버 응답을 기다리는 중 */
  pending?: boolean
}
export interface ChatEvent { type: 'CREATED' | 'UPDATED' | 'DELETED'; chatId: number; chat: Chat | null }
export interface Recording {
  id: number; broadcastId: number; title: string; ownerNickname: string
  status: 'RECORDING' | 'COMPLETED' | 'FAILED'; durationMillis: number | null; videoUrl: string | null; recordedAt: string
}

export interface ChargeOrder {
  orderId: string; amount: number; orderName: string; clientKey: string; customerKey: string; customerName: string
}
export interface Donation {
  id: number; broadcastId: number; donorNickname: string; streamerNickname: string
  amount: number; message: string | null; createdAt: string
}
export interface ChargeResult { orderId: string; chargedAmount: number; balance: number; donation: Donation | null }
export interface PointTransaction {
  id: number; type: 'CHARGE' | 'DONATION_SENT' | 'DONATION_RECEIVED'
  amount: number; balanceAfter: number; description: string; createdAt: string
}
export interface Wallet { balance: number; transactions: PointTransaction[] }
export interface DonationSummary { totalAmount: number; count: number; recent: Donation[] }

export interface Channel {
  id: number; handle: string; name: string; description: string | null
  ownerId: number; ownerNickname: string; followerCount: number; following: boolean; mine: boolean
  live: Broadcast | null
}
export interface FollowingItem { handle: string; name: string; ownerNickname: string; live: Broadcast | null }
export interface LiveNotification { type: 'LIVE_STARTED'; channelHandle: string; channelName: string; broadcastId: number; title: string }

export interface UserRef { id: number; nickname: string }
export interface BanItem { user: UserRef; until: string | null; reason: string | null }
export interface ModerationInfo {
  ownerId: number; canModerate: boolean; isOwner: boolean; slowModeSeconds: number
  managers: UserRef[]; bans: BanItem[]
}
export interface ModerationEvent { type: 'USER_BANNED' | 'USER_UNBANNED' | 'SLOW_MODE' | 'MANAGER_ADDED' | 'MANAGER_REMOVED'; userId: number | null; until: string | null; slowModeSeconds: number | null }

export type VideoQualityPref = 'auto' | 'high' | 'medium' | 'low'
export interface QoeBucket { at: string; viewers: number; avgBitrateKbps: number; avgPacketLossPct: number; avgRttMs: number; freezes: number }
export interface QoeSummary {
  windowMinutes: number; reportingViewers: number; samples: number
  avgBitrateKbps: number; avgRttMs: number; p95RttMs: number; avgPacketLossPct: number; avgJitterMs: number; avgFps: number
  totalFreezes: number; totalFramesDropped: number; qualityShare: Record<string, number>; typicalHeight: number
  timeline: QoeBucket[]
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>
  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

function csrfToken(): string | null {
  const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)
  return m ? decodeURIComponent(m[1]) : null
}

async function ensureCsrf() {
  // 첫 방문에는 쿠키가 없으므로 아무 GET 요청으로 발급받는다.
  if (!csrfToken()) await fetch('/api/broadcasts?size=1', { credentials: 'same-origin' })
}

async function request<T>(method: string, url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') {
    await ensureCsrf()
    const token = csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(url, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (res.status === 204) return undefined as T
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    throw new ApiError(res.status, data?.code ?? 'UNKNOWN', data?.message ?? '요청에 실패했습니다.', data?.fieldErrors ?? {})
  }
  return data as T
}

export const api = {
  me: () => request<Me>('GET', '/api/auth/me'),
  signup: (email: string, password: string, nickname: string) =>
    request<Me>('POST', '/api/auth/signup', { email, password, nickname }),
  login: (email: string, password: string) => request<Me>('POST', '/api/auth/login', { email, password }),
  demoLogin: (account: 'STREAMER' | 'STREAMER2' | 'VIEWER') =>
    request<Me>('POST', `/api/auth/demo-login?account=${account}`),
  logout: () => request<void>('POST', '/api/auth/logout'),

  broadcasts: (status?: BroadcastStatus, page = 0) =>
    request<Page<Broadcast>>('GET', `/api/broadcasts?page=${page}${status ? `&status=${status}` : ''}`),
  myBroadcasts: () => request<Broadcast[]>('GET', '/api/broadcasts/mine'),
  broadcast: (id: number) => request<Broadcast>('GET', `/api/broadcasts/${id}`),
  createBroadcast: (title: string, description: string) =>
    request<Broadcast>('POST', '/api/broadcasts', { title, description }),
  updateBroadcast: (id: number, title: string, description: string) =>
    request<Broadcast>('PATCH', `/api/broadcasts/${id}`, { title, description }),
  deleteBroadcast: (id: number) => request<void>('DELETE', `/api/broadcasts/${id}`),
  start: (id: number, source: BroadcastSource) => request<Connection>('POST', `/api/broadcasts/${id}/start`, { source }),
  publisherToken: (id: number) => request<Connection>('POST', `/api/broadcasts/${id}/publisher-token`),
  end: (id: number) => request<Broadcast>('POST', `/api/broadcasts/${id}/end`),
  viewerToken: (id: number) => request<Connection>('GET', `/api/broadcasts/${id}/viewer-token`),

  chats: (broadcastId: number) => request<Chat[]>('GET', `/api/broadcasts/${broadcastId}/chats`),
  sendChat: (broadcastId: number, content: string) =>
    request<Chat>('POST', `/api/broadcasts/${broadcastId}/chats`, { content }),
  editChat: (chatId: number, content: string) => request<Chat>('PATCH', `/api/chats/${chatId}`, { content }),
  deleteChat: (chatId: number) => request<void>('DELETE', `/api/chats/${chatId}`),

  wallet: () => request<Wallet>('GET', '/api/wallet'),
  prepareCharge: (amount: number, donation?: { broadcastId: number; message: string }) =>
    request<ChargeOrder>('POST', '/api/wallet/charges', { amount, donation }),
  confirmCharge: (paymentKey: string, orderId: string, amount: number) =>
    request<ChargeResult>('POST', '/api/wallet/charges/confirm', { paymentKey, orderId, amount }),
  donate: (broadcastId: number, amount: number, message: string, requestId: string) =>
    request<{ donation: Donation; balance: number }>('POST', `/api/broadcasts/${broadcastId}/donations`, { amount, message, requestId }),
  donations: (broadcastId: number) => request<DonationSummary>('GET', `/api/broadcasts/${broadcastId}/donations`),

  reportQoe: (broadcastId: number, sample: Record<string, number | string>) =>
    request<void>('POST', `/api/broadcasts/${broadcastId}/qoe`, sample),
  qoe: (broadcastId: number, minutes = 5) => request<QoeSummary>('GET', `/api/broadcasts/${broadcastId}/qoe?minutes=${minutes}`),

  channel: (handle: string) => request<Channel>('GET', `/api/channels/${encodeURIComponent(handle)}`),
  channelBroadcasts: (handle: string, page = 0) =>
    request<Page<Broadcast>>('GET', `/api/channels/${encodeURIComponent(handle)}/broadcasts?page=${page}`),
  myChannel: () => request<Channel>('GET', '/api/me/channel'),
  updateMyChannel: (handle: string, name: string, description: string) =>
    request<Channel>('PATCH', '/api/me/channel', { handle, name, description }),
  follow: (handle: string) => request<{ following: boolean; followerCount: number }>('POST', `/api/channels/${encodeURIComponent(handle)}/follow`),
  unfollow: (handle: string) => request<{ following: boolean; followerCount: number }>('DELETE', `/api/channels/${encodeURIComponent(handle)}/follow`),
  following: () => request<FollowingItem[]>('GET', '/api/me/following'),

  moderation: (broadcastId: number) => request<ModerationInfo>('GET', `/api/broadcasts/${broadcastId}/moderation`),
  ban: (broadcastId: number, userId: number, minutes: number | null, reason?: string) =>
    request<BanItem>('POST', `/api/broadcasts/${broadcastId}/moderation/bans`, { userId, minutes, reason }),
  unban: (broadcastId: number, userId: number) => request<void>('DELETE', `/api/broadcasts/${broadcastId}/moderation/bans/${userId}`),
  setSlowMode: (broadcastId: number, seconds: number) =>
    request<{ slowModeSeconds: number }>('PUT', `/api/broadcasts/${broadcastId}/moderation/slow-mode`, { seconds }),
  setManager: (broadcastId: number, userId: number, manager: boolean) =>
    request<void>(manager ? 'PUT' : 'DELETE', `/api/broadcasts/${broadcastId}/moderation/managers/${userId}`),

  recordings: () => request<Recording[]>('GET', '/api/recordings'),
  recording: (id: number) => request<Recording>('GET', `/api/recordings/${id}`),
  broadcastRecordings: (broadcastId: number) => request<Recording[]>('GET', `/api/broadcasts/${broadcastId}/recordings`),
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    const fields = Object.values(e.fieldErrors)
    return fields.length ? fields.join(' ') : e.message
  }
  if (e instanceof Error) return e.message
  return '알 수 없는 오류가 발생했습니다.'
}
