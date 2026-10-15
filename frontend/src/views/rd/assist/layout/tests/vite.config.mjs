import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'
import { existsSync, createReadStream } from 'node:fs'

const root = process.cwd()
const folder = resolve(root, 'src/views/rd/assist/layout/tests')
const font = resolve(root, 'public/fonts/assist/NotoSansSC-Regular.ttf')
const serveFont = (server) => {
  server.middlewares.use((req, res, next) => {
    if (req.url !== '/fonts/assist/NotoSansSC-Regular.ttf') return next()
    if (!existsSync(font)) {
      res.statusCode = 404
      res.end('Parent local font not provisioned')
      return
    }
    res.setHeader('Content-Type', 'font/ttf')
    createReadStream(font).pipe(res)
  })
}
export default defineConfig({
  root,
  publicDir: false,
  plugins: [
    vue(),
    {
      name: 'synthetic-editor-fixtures',
      configureServer: serveFont,
      configurePreviewServer: serveFont
    }
  ],
  resolve: {
    alias: [
      ...[
        '@/utils/auth',
        '@/store/modules/user',
        '@/directives/permission/hasPermi',
        '@/api/rd/masterdata/location',
        '@/api/rd/stock/putaway',
        '@/api/rd/stock/package',
        '@/api/rd/assist/visualDocument'
      ].map((find) => ({
        find: new RegExp(`^${find}$`),
        replacement: resolve(folder, 'fixture.ts')
      })),
      { find: /^@\//, replacement: `${resolve(root, 'src')}/` }
    ]
  },
  server: { host: '127.0.0.1', port: 5187, strictPort: true, open: false },
  preview: { host: '127.0.0.1', port: 5188, strictPort: true, open: false },
  build: {
    outDir: resolve(folder, 'artifacts/build'),
    emptyOutDir: false,
    rollupOptions: { input: resolve(folder, 'index.html') }
  }
})
