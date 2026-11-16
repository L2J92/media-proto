import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, errorMessage } from '../lib/api'

/** 등록(/broadcasts/new)과 수정(/broadcasts/:id/edit)을 함께 처리 */
export function BroadcastFormPage() {
  const { id } = useParams()
  const editId = id ? Number(id) : null
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    if (editId) api.broadcast(editId).then(b => { setTitle(b.title); setDescription(b.description ?? '') })
      .catch(e => setError(errorMessage(e)))
  }, [editId])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    try {
      if (editId) {
        await api.updateBroadcast(editId, title, description)
        navigate(`/broadcasts/${editId}`)
      } else {
        const b = await api.createBroadcast(title, description)
        navigate(`/broadcasts/${b.id}/studio`)
      }
    } catch (err) { setError(errorMessage(err)) }
  }

  return (
    <div className="auth">
      <h1>{editId ? '방송 정보 수정' : '새 방송'}</h1>
      <form onSubmit={submit} className="form">
        <label>제목<input value={title} maxLength={100} onChange={e => setTitle(e.target.value)} required /></label>
        <label>설명<textarea value={description} maxLength={1000} rows={4} onChange={e => setDescription(e.target.value)} /></label>
        {error && <p className="error">{error}</p>}
        <button className="btn btn-primary">{editId ? '저장' : '만들고 송출 화면으로'}</button>
      </form>
    </div>
  )
}
