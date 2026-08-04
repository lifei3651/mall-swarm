import { vi } from 'vitest'

// Mock Element Plus ElMessage to avoid DOM-dependent toast calls in unit tests
vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
  ElMessageBox: {
    confirm: vi.fn(() => Promise.resolve()),
    alert: vi.fn(() => Promise.resolve()),
  },
}))

// Ensure localStorage is available (jsdom provides it, but guard for safety)
if (!globalThis.localStorage) {
  globalThis.localStorage = {
    _data: {},
    getItem(key) { return this._data[key] || null },
    setItem(key, value) { this._data[key] = String(value) },
    removeItem(key) { delete this._data[key] },
    clear() { this._data = {} },
  }
}

// Mock window.matchMedia for Element Plus responsive components
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// Mock ResizeObserver (used by Element Plus components)
globalThis.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}))
