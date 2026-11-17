import { useCallback, useEffect, useRef, useState } from 'react'
import { api, errorMessage, type Chat, type ChatEvent } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Link } from 'react-router-dom'
import { broadcastEvents, useServerEvent } from '../lib/realtime'

interface Props { broadcastId: number }

export function ChatPanel({ broadcastId }: Props) {
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

  // 서버가 보내는 채팅 이벤트(SSE)를 반영한다. 방송 중이 아니어도 동작한다.
  useServerEvent<ChatEvent>(broadcastEvents(broadcastId), 'chat', applyEvent)

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [chats.length])

  // 전송 즉시 입력창을 비우고 내 채팅을 먼저 보여준다. 실패하면 되돌리고 입력 내용을 복구한다.
  async function send(e: React.FormEvent) {
    e.preventDefault()
    const content = text.trim()
    if (!content || !me) return
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
        // 실시간 이벤트가 응답보다 먼저 도착했을 수 있다
        return without.some(c => c.id === chat.id) ? without : [...without, chat]
      })
    } catch (err) {
      setChats(prev => prev.filter(c => c.id !== tempId))
      setText(cur => cur || content)
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

  return (
    <aside className="chat">
      <div className="chat-title">채팅</div>
      <div className="chat-list" ref={listRef}>
        {chats.length === 0 && <p className="muted small">첫 채팅을 남겨보세요.</p>}
        {chats.map(c => (
          <div key={c.id} className={`chat-item${c.pending ? ' pending' : ''}`}>
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
                {me?.id === c.authorId && !c.pending && (
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
