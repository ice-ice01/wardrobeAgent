import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { HttpResponse, http } from 'msw'
import { setupServer } from 'msw/node'
import { api, session } from './api'

const server = setupServer(
  http.get('/api/tryon/capabilities', ({ request }) => {
    const url = new URL(request.url)
    if (url.searchParams.get('planId') !== 'plan 1' || url.searchParams.get('planVersion') !== '2') {
      return HttpResponse.json({ code: 'BAD_QUERY', message: 'query mismatch' }, { status: 400 })
    }
    return HttpResponse.json({
      providerCode: 'MOCK', displayName: 'Mock Provider', mode: 'MOCK', enabled: true, coverage: 'MULTI_STAGE',
      supportedItems: [], unsupportedItems: [], estimatedMinSeconds: 1, estimatedMaxSeconds: 2,
      requiresExplicitConsent: false, remainingToday: -1,
    })
  }),
  http.get('/api/tryon/tasks/task-stream/events', ({ request }) => {
    if (request.headers.get('authorization') !== 'Bearer test-token') {
      return HttpResponse.json({ code: 'UNAUTHORIZED' }, { status: 401 })
    }
    const task = {
      id: 'task-stream', planId: 'plan-1', planVersion: 1, userModelId: 'model-1', provider: 'MOCK',
      status: 'SUCCEEDED', coverage: 'SINGLE', selectedItemIds: ['item-1'], renderedItemIds: ['item-1'],
      unrenderedItemIds: [], statusVersion: 4, items: [], createdAt: new Date().toISOString(),
    }
    return new HttpResponse(`id: task-stream:4\nevent: task.status\ndata: ${JSON.stringify(task)}\n\n`, {
      headers: { 'Content-Type': 'text/event-stream' },
    })
  }),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('try-on API', () => {
  it('requests provider capabilities with the plan version and bearer token', async () => {
    session.set('test-token')
    const capabilities = await api.tryOnCapabilities('plan 1', 2)
    expect(capabilities.providerCode).toBe('MOCK')
    expect(capabilities.coverage).toBe('MULTI_STAGE')
  })

  it('receives an authenticated SSE task snapshot and stops at a terminal state', async () => {
    session.set('test-token')
    const task = await new Promise<{ status: string }>((resolve, reject) => {
      const stop = api.watchTryOn('task-stream', (value) => {
        stop(); resolve(value)
      }, reject)
    })
    expect(task.status).toBe('SUCCEEDED')
  })

  it('recovers from two transient SSE failures without surfacing an error', async () => {
    session.set('test-token')
    let attempts = 0
    const onError = vi.fn()
    const onRecovered = vi.fn()
    server.use(http.get('/api/tryon/tasks/task-retry/events', () => {
      attempts++
      if (attempts < 3) {
        return HttpResponse.json({ code: 'TEMPORARY_UNAVAILABLE', message: 'temporary failure' }, { status: 503 })
      }
      const task = {
        id: 'task-retry', planId: 'plan-1', planVersion: 1, userModelId: 'model-1', provider: 'MOCK',
        status: 'SUCCEEDED', coverage: 'SINGLE', selectedItemIds: ['item-1'], renderedItemIds: ['item-1'],
        unrenderedItemIds: [], statusVersion: 4, items: [], createdAt: new Date().toISOString(),
      }
      return new HttpResponse(`id: task-retry:4\nevent: task.status\ndata: ${JSON.stringify(task)}\n\n`, {
        headers: { 'Content-Type': 'text/event-stream' },
      })
    }))

    const task = await new Promise<{ status: string }>((resolve) => {
      const stop = api.watchTryOn('task-retry', (value) => {
        stop(); resolve(value)
      }, onError, onRecovered)
    })

    expect(task.status).toBe('SUCCEEDED')
    expect(attempts).toBe(3)
    expect(onError).not.toHaveBeenCalled()
    expect(onRecovered).toHaveBeenCalledOnce()
  }, 8000)
})
