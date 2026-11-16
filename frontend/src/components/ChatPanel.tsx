import { useCallback, useEffect, useRef, useState } from 'react'
import { RoomEvent, type Room } from 'livekit-client'
import { api, errorMessage, type Chat, type ChatEvent } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Link } from 'react-router-dom'

interface Props { broadcastId: number; room: Room | null }

export function ChatPanel({ broadcastId, room }: Props) {
  const { me } = useAuth()
  const [chats, setChats] = useState<Chat[]>([])
  const [text, setText] = useState('')
  const [editing, setEditing] = useState<{ id: number; content: string } | null>(null)
  const [error, setError] = useState('')
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

  useEffect(() => {
    api.chats(broadcastId).then(setChats).catch(e => setError(errorMessage(e)))
  }, [broadcastId])

  // 서버가 미디어 방으로 보내는 채팅 이벤트를 반영한다.
  useEffect(() => {
    if (!room) return
    const handler = (payload: Uint8Array, _p: unknown, _k: unknown, topic?: string) => {
      if (topic !== 'chat') return
      try {
        applyEvent(JSON.parse(new TextDecoder().decode(payload)) as ChatEvent)
      } catch { /* 잘못된 메시지는 무시 */ }
    }
    room.on(RoomEvent.DataReceived, handler)
    return () => { room.off(RoomEvent.DataReceived, handler) }
  }, [room, applyEvent])

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [chats.length])

  async function send(e: React.FormEvent) {
    e.preventDefault()
    if (!text.trim()) return
    try {
      const chat = await api.sendChat(broadcastId, text)
      applyEvent({ type: 'CREATED', chatId: chat.id, chat })
      setText('')
      setError('')
    } catch (err) { setError(errorMessage(err)) }
  }

  async function saveEdit() {
    if (!editing) return
    try {
      const chat = await api.editChat(editing.id, editing.content)
      applyEvent({ type: 'UPDATED', chatId: chat.id, chat })
      setEditing(null)
    } catch (err) { setError(errorMessage(err)) }
  }

  async function remove(id: number) {
    if (!confirm('채팅을 삭제할까요?')) return
    try {
      await api.deleteChat(id)
      applyEvent({ type: 'DELETED', chatId: id, chat: null })
    } catch (err) { setError(errorMessage(err)) }
  }

  return (
    <aside className="chat">
      <div className="chat-title">채팅</div>
      <div className="chat-list" ref={listRef}>
        {chats.length === 0 && <p className="muted small">첫 채팅을 남겨보세요.</p>}
        {chats.map(c => (
          <div key={c.id} className="chat-item">
            <span className="chat-author">{c.authorNickname}</span>
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
                {me?.id === c.authorId && (
                  <span className="chat-actions">
                    <button className="link" onClick={() => setEditing({ id: c.id, content: c.content })}>수정</button>
                    <button className="link" onClick={() => remove(c.id)}>삭제</button>
                  </span>
                )}
              </>
            )}
          </div>
        ))}
      </div>
      {error && <p className="error small">{error}</p>}
      {me ? (
        <form className="chat-form" onSubmit={send}>
          <input value={text} maxLength={300} placeholder="채팅 입력" onChange={e => setText(e.target.value)} />
          <button className="btn btn-primary btn-sm">전송</button>
        </form>
      ) : (
        <p className="muted small chat-login"><Link to="/login">로그인</Link> 후 채팅할 수 있어요.</p>
      )}
    </aside>
  )
}
