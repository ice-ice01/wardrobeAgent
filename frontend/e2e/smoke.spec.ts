import { expect, test } from '@playwright/test'

test('login screen remains usable on desktop and mobile', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '登录你的智能衣橱' })).toBeVisible()
  await expect(page.getByLabel('用户名')).toHaveValue('demo')
  await expect(page.getByRole('button', { name: '进入衣橱' })).toBeEnabled()
  await expect(page.locator('body')).not.toHaveCSS('overflow-x', 'scroll')
})

test('confirmed outfit defaults to supported items and labels Spring AI fallback preview', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('wardrobe-agent-token', 'e2e-token'))
  const outfit = {
    id: 'plan-1', recommendationRunId: 'run-1', title: '通勤方案', scene: '上海通勤', version: 1,
    status: 'CONFIRMED', complete: true, missingSlots: [], reason: '适合通勤', explorationStatus: 'ALL_ITEMS_CONSIDERED',
    wardrobeTotal: 3, eligibleTotal: 3, consideredCount: 3, hasMore: false, createdAt: '2026-08-25T00:00:00Z',
    selectedItems: [
      { itemId: 'top', slot: 'INNER_TOP', name: '白色上装', imageUrl: '/assets/top.jpg', locked: false },
      { itemId: 'bottom', slot: 'BOTTOM', name: '黑色长裤', imageUrl: '/assets/bottom.jpg', locked: false },
      { itemId: 'shoes', slot: 'SHOES', name: '黑色皮鞋', imageUrl: '/assets/shoes.jpg', locked: false },
    ],
  }
  const conversation = {
    id: 'conversation-1', title: 'E2E', lastMessageAt: '2026-08-25T00:00:00Z',
    messages: [{ id: 'message-1', clientMessageId: 'client-1', recommendationRunId: 'run-1', role: 'ASSISTANT', content: '方案已生成', status: 'COMPLETED', createdAt: '2026-08-25T00:00:00Z' }],
    outfits: [outfit],
  }
  let taskRequested = false
  await page.route('**/api/**', async (route) => {
    const request = route.request(); const url = new URL(request.url()); const method = request.method()
    if (url.pathname === '/api/agent/conversations' && method === 'POST') return route.fulfill({ json: conversation })
    if (url.pathname === '/api/wardrobe/items') return route.fulfill({ json: [] })
    if (url.pathname === '/api/user-models') return route.fulfill({ json: [{ id: 'model-1', name: '默认形象', type: 'PRESET', imageUrl: '/assets/model.jpg', defaultModel: true, authorized: true }] })
    if (url.pathname === '/api/tryon/capabilities') return route.fulfill({ json: {
      providerCode: 'FASHN_V1_6', displayName: 'FASHN 真实试穿', mode: 'REAL', enabled: true, coverage: 'MULTI_STAGE',
      supportedItems: outfit.selectedItems.slice(0, 2).map((item) => ({ ...item, supported: true })),
      unsupportedItems: [{ ...outfit.selectedItems[2], supported: false, reason: '当前 Provider 不支持鞋履' }],
      estimatedMinSeconds: 5, estimatedMaxSeconds: 20, requiresExplicitConsent: true, remainingToday: 2,
      fallbackEnabled: true, fallbackProviderCode: 'SPRING_AI_IMAGE', fallbackResultKind: 'AI_PREVIEW',
    } })
    if (url.pathname === '/api/tryon/confirmations' && method === 'POST') {
      expect((request.postDataJSON() as { selectedItemIds: string[] }).selectedItemIds).toEqual(['bottom'])
      return route.fulfill({ json: { token: 'confirmation-token', expiresAt: '2026-08-25T00:05:00Z' } })
    }
    if (url.pathname === '/api/tryon/tasks' && method === 'POST') {
      taskRequested = true
      return route.fulfill({ json: tryOnTask('PROCESSING') })
    }
    if (url.pathname === '/api/tryon/tasks/task-1' && method === 'GET') return route.fulfill({ json: tryOnTask('SUCCEEDED') })
    return route.fulfill({ status: 404, json: { code: 'NOT_MOCKED', message: url.pathname } })
  })

  await page.goto('/agent')
  await page.getByRole('button', { name: '生成试穿' }).click()
  await expect(page.getByText('分阶段整套试穿')).toBeVisible()
  await expect(page.getByText(/自动改用 Spring AI 生成效果预览/)).toBeVisible()
  const supported = page.locator('.tryon-item input:not(:disabled)')
  await expect(supported).toHaveCount(2)
  await expect(supported.nth(0)).toBeChecked()
  await expect(supported.nth(1)).toBeChecked()
  await expect(page.locator('.tryon-item input:disabled')).toHaveCount(1)
  await supported.nth(0).uncheck()
  await page.getByText(/我同意将所选人物与衣物图片发送给 FASHN/).click()
  await page.getByRole('button', { name: '生成已选 1 件' }).click()
  await expect(page.getByRole('heading', { name: 'AI 效果预览已生成' })).toBeVisible()
  await expect(page.getByText('人物身份、衣物颜色、图案和版型可能与原图不完全一致。')).toBeVisible()
  await expect(page.getByText('切换原因：FASHN_SERVICE_UNAVAILABLE: service unavailable')).toBeVisible()
  await expect(page.getByText(/Spring AI 预览/)).toBeVisible()
  await expect(page.getByText('已渲染 1/1 件所选衣物')).toBeVisible()
  expect(taskRequested).toBe(true)
})

function tryOnTask(status: 'PROCESSING' | 'SUCCEEDED') {
  return {
    id: 'task-1', planId: 'plan-1', planVersion: 1, userModelId: 'model-1', provider: 'FASHN_V1_6',
    effectiveProvider: 'SPRING_AI_IMAGE', fallbackUsed: true,
    fallbackReason: 'FASHN_SERVICE_UNAVAILABLE: service unavailable', resultKind: 'AI_PREVIEW', status,
    coverage: 'SINGLE', selectedItemIds: ['bottom'], renderedItemIds: status === 'SUCCEEDED' ? ['bottom'] : [],
    unrenderedItemIds: status === 'SUCCEEDED' ? ['top', 'shoes'] : ['top', 'bottom', 'shoes'],
    resultImageUrl: status === 'SUCCEEDED' ? '/assets/result.jpg' : undefined, statusVersion: status === 'SUCCEEDED' ? 4 : 2,
    items: [
      { itemId: 'top', slot: 'INNER_TOP', name: '白色上装', imageUrl: '/assets/top.jpg', locked: false, selected: false, rendered: false, status: 'SKIPPED' },
      { itemId: 'bottom', slot: 'BOTTOM', name: '黑色长裤', imageUrl: '/assets/bottom.jpg', locked: false, selected: true, rendered: status === 'SUCCEEDED', sequence: 1, effectiveProvider: 'SPRING_AI_IMAGE', fallbackUsed: true, resultKind: 'AI_PREVIEW', status: status === 'SUCCEEDED' ? 'SUCCEEDED' : 'PROCESSING' },
      { itemId: 'shoes', slot: 'SHOES', name: '黑色皮鞋', imageUrl: '/assets/shoes.jpg', locked: false, selected: false, rendered: false, status: 'SKIPPED' },
    ], createdAt: '2026-08-25T00:00:00Z',
  }
}
