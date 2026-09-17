import type { TryOnCapabilities, TryOnCapabilityItem, WardrobeSlot } from './types'

const separateSlots = new Set<WardrobeSlot>(['INNER_TOP', 'OUTER_TOP', 'BOTTOM'])

export function defaultTryOnSelection(capabilities: TryOnCapabilities): Set<string> {
  return new Set(capabilities.supportedItems.map((item) => item.itemId))
}

export function toggleTryOnItem(selected: Set<string>, item: TryOnCapabilityItem, allItems: TryOnCapabilityItem[]): Set<string> {
  if (!item.supported) return new Set(selected)
  const next = new Set(selected)
  if (next.has(item.itemId)) {
    next.delete(item.itemId)
    return next
  }
  if (item.slot === 'DRESS') {
    for (const candidate of allItems) if (separateSlots.has(candidate.slot)) next.delete(candidate.itemId)
  } else if (separateSlots.has(item.slot)) {
    for (const candidate of allItems) if (candidate.slot === 'DRESS') next.delete(candidate.itemId)
  }
  next.add(item.itemId)
  return next
}

export function isTryOnActive(status: string): boolean {
  return ['CREATED', 'SUBMITTING', 'SUBMITTED', 'PROCESSING', 'SUBMISSION_UNKNOWN'].includes(status)
}

export function completedTryOnStages(statuses: string[]): number {
  return statuses.filter((status) => status === 'SUCCEEDED').length
}
