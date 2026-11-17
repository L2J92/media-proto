import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError, errorMessage, type Chat, type ChatEvent, type ModerationEvent, type ModerationInfo } from '../lib/api'
import { useAuth } from '../lib/auth'
import { broadcastEvents, useServerEvent } from '../lib/realtime'

interface Props { broadcastId: number }

const SLOW_OPTIONS = [0, 3, 10, 30, 60]

export function ChatPanel({ broadcastId }: Props) {
  const { me } = useAuth()
  const [chats, setChats] = useState<Chat[]>([])
  const [text, setText] = useState('')
  const [editing, setEditing] = useState<{ id: number; content: string } | null>(null)
  const [menuFor, setMenuFor] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [mod, setMod] = useState<ModerationInfo | null>(null)
  const [showModPanel, setShowModPanel] = useState(false)
  const [bannedUntil, setBannedUntil] = useState<string | null | undefined>(undefined) // undefined=제재 없음, null=영구
  const [cooldown, setCooldown] = useState(0)
  const listRef = useRef<HTMLDivElement>(null)

  const applyEvent = useCallback((ev: ChatEvent) => {
    setChats(prev => {
      if (ev.type === 'DELETED') return prev.filter(c => c.id !== ev.chatId)
      if (!ev.chat) return prev
      const idx = prev.findIndex(c => c.id === ev.chatId)
      if (idx === -1) return ev.type === 'CREATED' ? [...prev, ev.chat] : prev
      const next = [...prev]
      next[idx] = ev.chat
      return next
    })
  }, [])

  const loadMod = useCallback(() => { api.moderation(broadcastId).then(setMod).catch(() => {}) }, [broadcastId])

  useEffect(() => {
    api.chats(broadcastId).then(setChats).catch(e => setError(errorMessage(e)))
    loadMod()
  }, [broadcastId, loadMod, me?.id])

  // 채팅·관리 이벤트(SSE). 방송 중이 아니어도 동작한다.
  useServerEvent<ChatEvent>(broadcastEvents(broadcastId), 'chat', applyEvent)
  useServerEvent<ModerationEvent>(broadcastEvents(broadcastId), 'moderation', ev => {
    if (ev.type === 'SLOW_MODE') setMod(m => (m ? { ...m, slowModeSeconds: ev.slowModeSeconds ?? 0 } : m))
    if (ev.type === 'USER_BANNED' && ev.userId != null) {
      setChats(prev => prev.filter(c => c.authorId !== ev.userId)) // 제재된 사용자의 메시지는 숨김
      if (ev.userId === me?.id) setBannedUntil(ev.until)
    }
    if (ev.type === 'USER_UNBANNED' && ev.userId === me?.id) setBannedUntil(undefined)
    if (mod?.canModerate || ev.type.startsWith('MANAGER')) loadMod()
  })

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [chats.length])

  useEffect(() => {
    if (cooldown <= 0) return
    const t = setTimeout(() => setCooldown(c => c - 1), 1000)
    return () => clearTimeout(t)
  }, [cooldown])

  // 전송 즉시 입력창을 비우고 내 채팅을 먼저 보여준다. 실패하면 되돌리고 입력 내용을 복구한다.
  async function send(e: React.FormEvent) {
    e.preventDefault()
    const content = text.trim()
    if (!content || !me || cooldown > 0) return
    setText('')
    setError('')
    const tempId = -Date.now()
    const temp: Chat = {
      id: tempId, broadcastId, authorId: me.id, authorNickname: me.nickname,
      content, edited: false, createdAt: new Date().toISOString(), pending: true,
    }
    setChats(prev => [...prev, temp])
    try {
      const chat = await api.sendChat(broadcastId, content)
      setChats(prev => {
        const without = prev.filter(c => c.id !== tempId)
        return without.some(c => c.id === chat.id) ? without : [...without, chat]
      })
      if (mod && mod.slowModeSeconds > 0 && !mod.canModerate) setCooldown(mod.slowModeSeconds)
    } catch (err) {
      setChats(prev => prev.filter(c => c.id !== tempId))
      setText(cur => cur || content)
      if (err instanceof ApiError && err.code === 'CHAT_BANNED') setBannedUntil(null)
      setError(errorMessage(err))
    }
  }

  async function saveEdit() {
    if (!editing) return
    const target = chats.find(c => c.id === editing.id)
    const content = editing.content.trim()
    if (!target || !content) return
    setEditing(null)
    applyEvent({ type: 'UPDATED', chatId: target.id, chat: { ...target, content, edited: true } })
    try {
      const chat = await api.editChat(target.id, content)
      applyEvent({ type: 'UPDATED', chatId: chat.id, chat })
    } catch (err) {
      applyEvent({ type: 'UPDATED', chatId: target.id, chat: target })
      setError(errorMessage(err))
    }
  }

  async function remove(id: number) {
    setMenuFor(null)
    if (!confirm('채팅을 삭제할까요?')) return
    const index = chats.findIndex(c => c.id === id)
    const target = chats[index]
    applyEvent({ type: 'DELETED', chatId: id, chat: null })
    try {
      await api.deleteChat(id)
    } catch (err) {
      if (target) setChats(prev => prev.some(c => c.id === id) ? prev : [...prev.slice(0, index), target, ...prev.slice(index)])
      setError(errorMessage(err))
    }
  }

  async function moderate(action: () => Promise<unknown>, done?: string) {
    setMenuFor(null)
    try { await action(); if (done) setError(''); loadMod() } catch (err) { setError(errorMessage(err)) }
  }

  const isManager = (userId: number) => !!mod?.managers.some(m => m.id === userId)
  const badge = (userId: number) => (mod && userId === mod.ownerId ? '👑 ' : isManager(userId) ? '🛡 ' : '')

  return (
    <aside className="chat">
      <div className="chat-title">
        채팅
        {mod && mod.slowModeSeconds > 0 && <span className="chat-chip" title="슬로우 모드">🐢 {mod.slowModeSeconds}초</span>}
        {mod?.canModerate && (
          <button className="link chat-mod-toggle" onClick={() => setShowModPanel(v => !v)}>⚙ 관리</button>
        )}
      </div>

      {showModPanel && mod?.canModerate && (
        <div className="mod-panel">
          <label className="small">슬로우 모드
            <select value={mod.slowModeSeconds} onChange={e => moderate(() => api.setSlowMode(broadcastId, Number(e.target.value)))}>
              {SLOW_OPTIONS.map(s => <option key={s} value={s}>{s === 0 ? '끔' : `${s}초`}</option>)}
            </select>
          </label>
          <div className="small">
            <b>매니저</b>
            {mod.managers.length === 0 ? <span className="muted"> 없음 (채팅의 ⋯ 메뉴에서 지정)</span> : mod.managers.map(m => (
              <span key={m.id} className="mod-tag">🛡 {m.nickname}
                {mod.isOwner && <button className="link" onClick={() => moderate(() => api.setManager(broadcastId, m.id, false))}>해제</button>}
              </span>
            ))}
          </div>
          <div className="small">
            <b>제재 중</b>
            {mod.bans.length === 0 ? <span className="muted"> 없음</span> : mod.bans.map(b => (
              <span key={b.user.id} className="mod-tag">{b.user.nickname}
                <span className="muted"> {b.until ? `~${new Date(b.until).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}` : '영구'}</span>
                <button className="link" onClick={() => moderate(() => api.unban(broadcastId, b.user.id))}>해제</button>
              </span>
            ))}
          </div>
        </div>
      )}

      <div className="chat-list" ref={listRef}>
        {chats.length === 0 && <p className="muted small">첫 채팅을 남겨보세요.</p>}
        {chats.map(c => {
          const mine = me?.id === c.authorId
          const canMod = !!mod?.canModerate && !mine && c.authorId !== mod.ownerId && !(isManager(c.authorId) && !mod.isOwner)
          return (
            <div key={c.id} className={`chat-item${c.pending ? ' pending' : ''}`}>
              <span className="chat-author">{badge(c.authorId)}{c.authorNickname}</span>
              {editing?.id === c.id ? (
                <span className="chat-edit">
                  <input value={editing.content} maxLength={300}
                    onChange={e => setEditing({ id: c.id, content: e.target.value })}
                    onKeyDown={e => e.key === 'Enter' && saveEdit()} autoFocus />
                  <button className="link" onClick={saveEdit}>저장</button>
                  <button className="link" onClick={() => setEditing(null)}>취소</button>
                </span>
              ) : (
                <>
                  <span className="chat-content">{c.content}</span>
                  {c.edited && <span className="muted small"> (수정됨)</span>}
                  {mine && !c.pending && (
                    <span className="chat-actions">
                      <button className="link" onClick={() => setEditing({ id: c.id, content: c.content })}>수정</button>
                      <button className="link" onClick={() => remove(c.id)}>삭제</button>
                    </span>
                  )}
                  {canMod && !c.pending && (
                    <span className="chat-actions">
                      <button className="link" onClick={() => setMenuFor(menuFor === c.id ? null : c.id)} aria-label="관리 메뉴">⋯</button>
                    </span>
                  )}
                  {menuFor === c.id && canMod && (
                    <div className="chat-menu">
                      <button onClick={() => remove(c.id)}>메시지 삭제</button>
                      <button onClick={() => moderate(() => api.ban(broadcastId, c.authorId, 1))}>1분 채팅 금지</button>
                      <button onClick={() => moderate(() => api.ban(broadcastId, c.authorId, 10))}>10분 채팅 금지</button>
                      <button className="danger" onClick={() => { if (confirm(`${c.authorNickname}님을 이 채널에서 차단할까요?`)) moderate(() => api.ban(broadcastId, c.authorId, null)) }}>영구 차단</button>
                      {mod?.isOwner && (isManager(c.authorId)
                        ? <button onClick={() => moderate(() => api.setManager(broadcastId, c.authorId, false))}>매니저 해제</button>
                        : <button onClick={() => moderate(() => api.setManager(broadcastId, c.authorId, true))}>매니저 지정</button>)}
                    </div>
                  )}
                </>
              )}
            </div>
          )
        })}
      </div>
      {error && <p className="error small">{error}</p>}
      {me ? (
        bannedUntil !== undefined ? (
          <p className="muted small chat-login">
            🚫 이 채널에서 채팅이 제한되었어요{bannedUntil ? ` (${new Date(bannedUntil).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}까지)` : ''}.
          </p>
        ) : (
          <form className="chat-form" onSubmit={send}>
            <input value={text} maxLength={300} placeholder={cooldown > 0 ? `슬로우 모드: ${cooldown}초 후 전송 가능` : '채팅 입력'}
              onChange={e => setText(e.target.value)} />
            <button className="btn btn-primary btn-sm" disabled={cooldown > 0}>{cooldown > 0 ? cooldown : '전송'}</button>
          </form>
        )
      ) : (
        <p className="muted small chat-login"><Link to="/login">로그인</Link> 후 채팅할 수 있어요.</p>
      )}
    </aside>
  )
}
