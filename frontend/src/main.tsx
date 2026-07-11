import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'

// Self-hosted fonts (no CDN) via @fontsource.
import '@fontsource/anton/400.css'
import '@fontsource/archivo/400.css'
import '@fontsource/archivo/500.css'
import '@fontsource/archivo/600.css'
import '@fontsource/archivo/700.css'
import '@fontsource/space-mono/400.css'
import '@fontsource/space-mono/700.css'
import './styles.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
