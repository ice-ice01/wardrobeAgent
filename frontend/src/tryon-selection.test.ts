import { describe, expect, it } from 'vitest'
import { completedTryOnStages, defaultTryOnSelection, isTryOnActive, toggleTryOnItem } from './tryon-selection'
import type { TryOnCapabilities, TryOnCapabilityItem } from './types'

const items: TryOnCapabilityItem[] = [
  { itemId: 'top', slot: 'INNER_TOP', name: '上装', imageUrl: '/top.jpg', supported: true },
  { itemId: 'bottom', slot: 'BOTTOM', name: '下装', imageUrl: '/bottom.jpg', supported: true },
  { itemId: 'dress', slot: 'DRESS', name: '连衣裙', imageUrl: '/dress.jpg', supported: true },
  { itemId: 'shoes', slot: 'SHOES', name: '鞋履', imageUrl: '/shoes.jpg', supported: false, reason: '不支持' },
]

const capabilities: TryOnCapabilities = {
  providerCode: 'MOCK', displayName: 'Mock', mode: 'MOCK', enabled: true, coverage: 'MULTI_STAGE',
  supportedItems: items.slice(0, 3), unsupportedItems: items.slice(3), estimatedMinSeconds: 1,
  estimatedMaxSeconds: 2, requiresExplicitConsent: false, remainingToday: -1,
}

describe('try-on selection', () => {
  it('defaults to every provider-supported item', () => {
    expect([...defaultTryOnSelection(capabilities)]).toEqual(['top', 'bottom', 'dress'])
  })

  it('lets the user uncheck a single item', () => {
    expect([...toggleTryOnItem(new Set(['top', 'bottom']), items[1], items)]).toEqual(['top'])
  })

  it('enforces dress and separates as mutually exclusive', () => {
    expect([...toggleTryOnItem(new Set(['top', 'bottom']), items[2], items)]).toEqual(['dress'])
    expect([...toggleTryOnItem(new Set(['dress']), items[0], items)]).toEqual(['top'])
  })

  it('does not select provider-unsupported items', () => {
    expect([...toggleTryOnItem(new Set(['top']), items[3], items)]).toEqual(['top'])
  })

  it('recognizes active states and completed stages', () => {
    expect(isTryOnActive('PROCESSING')).toBe(true)
    expect(isTryOnActive('PARTIALLY_SUCCEEDED')).toBe(false)
    expect(completedTryOnStages(['SUCCEEDED', 'FAILED', 'SUCCEEDED'])).toBe(2)
  })
})
