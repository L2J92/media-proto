import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    // 휴대폰에서 같은 네트워크로 접속해 볼 수 있도록 외부 바인딩
    host: true,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
