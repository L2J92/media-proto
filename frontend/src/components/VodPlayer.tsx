import { useEffect, useRef, useState } from 'react'

/**
 * 다시보기 플레이어. HLS(.m3u8)는 Safari 에서는 네이티브로, 그 외 브라우저는 hls.js 로 재생한다.
 * hls.js 는 다시보기 화면에서만 필요하므로 이때만 불러온다.
 */
export function VodPlayer({ src }: { src: string }) {
  const ref = useRef<HTMLVideoElement>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    const video = ref.current
    if (!video) return
    let destroy: (() => void) | undefined
    let cancelled = false
    const isHls = src.includes('.m3u8')

    if (!isHls || video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = src // mp4 이거나 Safari 네이티브 HLS
    } else {
      import('hls.js').then(({ default: Hls }) => {
        if (cancelled) return
        if (!Hls.isSupported()) { setError('이 브라우저는 HLS 재생을 지원하지 않습니다.'); return }
        const hls = new Hls({ enableWorker: true })
        hls.on(Hls.Events.ERROR, (_e, data) => {
          if (data.fatal) setError('영상을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
        })
        hls.loadSource(src)
        hls.attachMedia(video)
        destroy = () => hls.destroy()
      }).catch(() => setError('플레이어를 불러오지 못했습니다.'))
    }
    return () => { cancelled = true; destroy?.(); video.removeAttribute('src'); video.load() }
  }, [src])

  return (
    <div className="player">
      <video ref={ref} controls playsInline />
      {error && <div className="player-overlay">{error}</div>}
    </div>
  )
}
