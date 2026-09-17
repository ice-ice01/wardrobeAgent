import type { AgentEvent, AgentRun, ApiErrorPayload, AuthResponse, Conversation, Outfit, TryOn, TryOnCapabilities, UserModel, WardrobeItem } from './types'

const TOKEN_KEY = 'wardrobe-agent-token'

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public requestId?: string) { super(message) }
}

export const session = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

async function parseError(response: Response): Promise<never> {
  const data = await response.json().catch(() => ({} as ApiErrorPayload)) as ApiErrorPayload
  throw new ApiError(response.status, data.code ?? 'REQUEST_FAILED', data.message ?? '请求失败', data.requestId)
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = session.get()
  const response = await fetch(path, {
    ...init,
    headers: {
      ...(init.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  })
  if (response.status === 401 && path !== '/api/auth/login') session.clear()
  if (!response.ok) return parseError(response)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

type SseFrame = { id: string; event: string; data: unknown }

async function readSse(path: string, lastEventId: string, signal: AbortSignal,
                       onFrame: (frame: SseFrame) => void, onOpen?: () => void): Promise<string> {
  const token = session.get()
  const response = await fetch(path, {
    headers: {
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(lastEventId ? { 'Last-Event-ID': lastEventId } : {}),
    },
    signal,
  })
  if (!response.ok) return parseError(response)
  const reader = response.body?.getReader()
  if (!reader) throw new Error('浏览器不支持流式响应')
  onOpen?.()
  const decoder = new TextDecoder(); let buffer = ''; let latestId = lastEventId
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
    const frames = buffer.split('\n\n'); buffer = frames.pop() ?? ''
    for (const raw of frames) {
      let id = ''; let event = 'message'; const data: string[] = []
      for (const line of raw.split('\n')) {
        if (line.startsWith('id:')) id = line.slice(3).trim()
        else if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
      }
      if (!data.length) continue
      if (id) latestId = id
      onFrame({ id, event, data: JSON.parse(data.join('\n')) })
    }
    if (done) return latestId
  }
}

function watchSse<T>(path: string, onFrame: (frame: SseFrame) => boolean,
                     poll: () => Promise<T>, onPoll: (value: T) => boolean,
                     onError?: (error: unknown) => void, onRecovered?: () => void) {
  let stopped = false; let controller: AbortController | null = null; let timer = 0
  let failures = 0; let lastEventId = ''
  const schedule = (delay: number, action: () => void) => { timer = window.setTimeout(action, delay) }
  const pollOnce = async () => {
    if (stopped) return
    try {
      const value = await poll(); failures = 0; onRecovered?.()
      if (!onPoll(value)) schedule(3000, () => void pollOnce())
    } catch (error) { onError?.(error); schedule(5000, () => void pollOnce()) }
  }
  const connect = async () => {
    if (stopped) return
    controller = new AbortController()
    try {
      lastEventId = await readSse(path, lastEventId, controller.signal, (frame) => {
        if (frame.id) lastEventId = frame.id
        if (onFrame(frame)) { stopped = true; controller?.abort() }
      }, onRecovered)
      if (stopped) return
      failures++
    } catch (error) {
      if (stopped || (error instanceof DOMException && error.name === 'AbortError')) return
      failures++
      if (failures >= 3) onError?.(error)
    }
    if (failures >= 3) schedule(0, () => void pollOnce())
    else schedule([1000, 2000, 5000][failures - 1] ?? 5000, () => void connect())
  }
  void connect()
  return () => { stopped = true; controller?.abort(); window.clearTimeout(timer) }
}

export const api = {
  login: (username: string, password: string) => request<AuthResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  wardrobe: (query = '') => request<WardrobeItem[]>(`/api/wardrobe/items${query}`),
  deleteItem: (id: string) => request<void>(`/api/wardrobe/items/${id}`, { method: 'DELETE' }),
  createItem: (data: object) => request<WardrobeItem>('/api/wardrobe/items', { method: 'POST', body: JSON.stringify(data) }),
  upload: async (file: File, purpose: 'WARDROBE' | 'USER_MODEL') => {
    const body = new FormData(); body.append('file', file)
    return request<{ id: string; url: string }>(`/api/files?purpose=${purpose}`, { method: 'POST', body })
  },
  conversations: () => request<Conversation[]>('/api/agent/conversations'),
  conversation: (id: string) => request<Conversation>(`/api/agent/conversations/${id}`),
  createConversation: (title = '新搭配') => request<Conversation>('/api/agent/conversations', { method: 'POST', body: JSON.stringify({ title }) }),
  agentRuns: (conversationId: string) => request<AgentRun[]>(`/api/agent/conversations/${conversationId}/runs`),
  agentRun: (id: string) => request<AgentRun>(`/api/agent/runs/${id}`),
  createAgentRun: (conversationId: string, data: object) => request<AgentRun>(`/api/agent/conversations/${conversationId}/runs`, { method: 'POST', body: JSON.stringify(data) }),
  watchAgentRun: (id: string, onRun: (run: AgentRun) => void, onEvent: (event: AgentEvent) => void,
                  onError?: (error: unknown) => void, onRecovered?: () => void) =>
    watchSse(`/api/agent/runs/${id}/events`, (frame) => {
      if (frame.event === 'agent.event') onEvent(frame.data as AgentEvent)
      if (frame.event === 'run.status') {
        const run = frame.data as AgentRun; onRun(run)
        return ['COMPLETED', 'FAILED', 'CANCELLED'].includes(run.status)
      }
      return false
    }, () => request<AgentRun>(`/api/agent/runs/${id}`), (run) => {
      onRun(run); return ['COMPLETED', 'FAILED', 'CANCELLED'].includes(run.status)
    }, onError, onRecovered),
  outfits: () => request<Outfit[]>('/api/outfits'),
  saveOutfit: (id: string) => request<Outfit>(`/api/outfits/${id}/save`, { method: 'POST' }),
  confirmOutfit: (id: string) => request<Outfit>(`/api/outfits/${id}/confirm`, { method: 'POST' }),
  models: () => request<UserModel[]>('/api/user-models'),
  createModel: (data: object) => request<UserModel>('/api/user-models', { method: 'POST', body: JSON.stringify(data) }),
  defaultModel: (id: string) => request<UserModel>(`/api/user-models/${id}/default`, { method: 'PUT' }),
  deleteModel: (id: string) => request<void>(`/api/user-models/${id}`, { method: 'DELETE' }),
  tryOns: () => request<TryOn[]>('/api/tryon/tasks'),
  tryOn: (id: string) => request<TryOn>(`/api/tryon/tasks/${id}`),
  tryOnCapabilities: (planId: string, planVersion: number) => request<TryOnCapabilities>(`/api/tryon/capabilities?planId=${encodeURIComponent(planId)}&planVersion=${planVersion}`),
  createConfirmation: (data: object) => request<{ token: string; expiresAt: string }>('/api/tryon/confirmations', { method: 'POST', body: JSON.stringify(data) }),
  createTryOn: (data: object) => request<TryOn>('/api/tryon/tasks', { method: 'POST', body: JSON.stringify(data) }),
  watchTryOn: (id: string, onTask: (task: TryOn) => void, onError?: (error: unknown) => void,
               onRecovered?: () => void) =>
    watchSse(`/api/tryon/tasks/${id}/events`, (frame) => {
      if (frame.event !== 'task.status') return false
      const task = frame.data as TryOn; onTask(task)
      return !['CREATED', 'SUBMITTING', 'SUBMITTED', 'PROCESSING'].includes(task.status)
    }, () => request<TryOn>(`/api/tryon/tasks/${id}`), (task) => {
      onTask(task); return !['CREATED', 'SUBMITTING', 'SUBMITTED', 'PROCESSING'].includes(task.status)
    }, onError, onRecovered),
  sendMessage: async (conversationId: string, data: object, onEvent: (event: AgentEvent) => void) => {
    const response = await fetch(`/api/agent/conversations/${conversationId}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.get() ?? ''}` },
      body: JSON.stringify(data),
    })
    if (!response.ok) return parseError(response)
    const reader = response.body?.getReader(); if (!reader) throw new Error('浏览器不支持流式响应')
    const decoder = new TextDecoder(); let buffer = ''
    while (true) {
      const { value, done } = await reader.read(); buffer += decoder.decode(value, { stream: !done })
      const lines = buffer.split('\n'); buffer = lines.pop() ?? ''
      for (const line of lines) if (line.trim()) onEvent(JSON.parse(line) as AgentEvent)
      if (done) break
    }
    if (buffer.trim()) onEvent(JSON.parse(buffer) as AgentEvent)
  },
}
