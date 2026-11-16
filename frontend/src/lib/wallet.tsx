import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from './api'
import { useAuth } from './auth'

interface WalletCtx { balance: number | null; refresh: () => Promise<void>; setBalance: (b: number) => void }

const WalletContext = createContext<WalletCtx | null>(null)

/** 로그인한 사용자의 포인트 잔액. 헤더·후원창이 같은 값을 본다. */
export function WalletProvider({ children }: { children: ReactNode }) {
  const { me } = useAuth()
  const [balance, setBalanceState] = useState<number | null>(null)

  const refresh = useCallback(async () => {
    if (!me) { setBalanceState(null); return }
    try { setBalanceState((await api.wallet()).balance) } catch { /* 무시 */ }
  }, [me])

  useEffect(() => {
    let alive = true
    if (me) api.wallet().then(w => alive && setBalanceState(w.balance)).catch(() => {})
    return () => { alive = false }
  }, [me])

  const setBalance = useCallback((b: number) => setBalanceState(b), [])

  return (
    <WalletContext.Provider value={{ balance: me ? balance : null, refresh, setBalance }}>
      {children}
    </WalletContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useWallet() {
  const ctx = useContext(WalletContext)
  if (!ctx) throw new Error('WalletProvider missing')
  return ctx
}
