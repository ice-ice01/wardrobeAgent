export type AuthUser = { id: string; username: string; displayName: string; wardrobeRevision: number }
export type AuthResponse = { token: string; user: AuthUser }

export type WardrobeSlot = 'INNER_TOP' | 'OUTER_TOP' | 'BOTTOM' | 'DRESS' | 'SHOES' | 'BAG' | 'ACCESSORY'
export type WardrobeItem = {
  id: string; name: string; category: string; slot: WardrobeSlot; color: string; material: string
  seasonTags: string[]; sceneTags: string[]; styleTags: string[]; imageUrl: string
  imageSource: string; imageStatus: string; warmthLevel: number; breathabilityLevel: number; updatedAt: string
}
export type OutfitItem = { itemId: string; slot: string; name: string; imageUrl: string; locked: boolean }
export type Outfit = {
  id: string; recommendationRunId: string; title: string; scene: string; version: number; status: 'DRAFT' | 'SAVED' | 'CONFIRMED'
  complete: boolean; missingSlots: string[]; reason: string; explorationStatus: string
  wardrobeTotal: number; eligibleTotal: number; consideredCount: number; hasMore: boolean
  selectedItems: OutfitItem[]; createdAt: string
}
export type Conversation = {
  id: string; title: string; lastMessageAt: string
  messages: { id: string; clientMessageId: string; recommendationRunId?: string; role: 'USER' | 'ASSISTANT'; content: string; status: string; createdAt: string }[]
  outfits: Outfit[]
}
export type AgentEvent = { eventId: string; type: string; conversationId: string; sequence: number; timestamp: string; data: unknown }
export type AgentRun = {
  id: string; conversationId: string; clientMessageId: string; content: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  attemptCount: number; statusVersion: number; errorCode?: string; errorMessage?: string; createdAt: string
}
export type UserModel = { id: string; name: string; type: 'PRESET' | 'UPLOAD'; imageUrl: string; defaultModel: boolean; authorized: boolean }
export type TryOnCapabilityItem = { itemId: string; slot: WardrobeSlot; name: string; imageUrl: string; supported: boolean; reason?: string }
export type TryOnCapabilities = {
  providerCode: string; displayName: string; mode: 'MOCK' | 'REAL'; enabled: boolean; coverage: 'SINGLE' | 'MULTI_STAGE'
  supportedItems: TryOnCapabilityItem[]; unsupportedItems: TryOnCapabilityItem[]
  estimatedMinSeconds: number; estimatedMaxSeconds: number; requiresExplicitConsent: boolean; remainingToday: number
  fallbackEnabled?: boolean; fallbackProviderCode?: string; fallbackResultKind?: 'AI_PREVIEW'
}
export type TryOn = {
  id: string; planId: string; planVersion: number; userModelId: string; provider: string
  effectiveProvider?: string; fallbackUsed?: boolean; fallbackReason?: string
  resultKind?: 'VIRTUAL_TRY_ON' | 'AI_PREVIEW' | 'MOCK_PREVIEW'
  status: string; coverage: string; selectedItemIds: string[]; renderedItemIds: string[]; unrenderedItemIds: string[]
  resultImageUrl?: string; errorCode?: string; errorMessage?: string; statusVersion: number
  retryFromTaskId?: string; items: (OutfitItem & {
    selected: boolean; rendered: boolean; sequence?: number; status: string
    effectiveProvider?: string; fallbackUsed?: boolean; fallbackReason?: string
    resultKind?: 'VIRTUAL_TRY_ON' | 'AI_PREVIEW' | 'MOCK_PREVIEW'
    resultImageUrl?: string; errorCode?: string; errorMessage?: string
  })[]; createdAt: string
}
export type ApiErrorPayload = { code?: string; message?: string; requestId?: string }
