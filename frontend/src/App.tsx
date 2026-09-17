import { FormEvent, ReactNode, useEffect, useMemo, useState } from 'react'
import { NavLink, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import {
  Archive, ArrowRight, Bot, Check, ChevronRight, CircleUserRound, Clock3, Images, LoaderCircle,
  Lock, LogOut, Menu, MessageSquarePlus, Plus, RefreshCw, Send, Shirt, Sparkles, Trash2, Upload, X,
} from 'lucide-react'
import { api, ApiError, session } from './api'
import { defaultTryOnSelection, isTryOnActive, toggleTryOnItem } from './tryon-selection'
import type { AgentRun, AuthUser, Conversation, Outfit, TryOn, TryOnCapabilities, UserModel, WardrobeItem, WardrobeSlot } from './types'

const slotNames: Record<string, string> = { INNER_TOP: '上装', OUTER_TOP: '外套', BOTTOM: '下装', DRESS: '连衣裙', SHOES: '鞋履', BAG: '包袋', ACCESSORY: '配饰' }
const statusNames: Record<string, string> = { DRAFT: '待确认', SAVED: '已收藏', CONFIRMED: '已确认', SUBMITTING: '提交中', SUBMITTED: '已提交', PROCESSING: '生成中', SUCCEEDED: '已完成', PARTIALLY_SUCCEEDED: '部分完成', FAILED: '失败', SKIPPED: '未选择', PENDING: '等待中' }
const asset = (url?: string) => url || '/assets/seed/item-01.png'
const ACTIVE_CONVERSATION_KEY = 'wardrobe-agent-active-conversation'

function useAsyncError() {
  const [error, setError] = useState('')
  const capture = (value: unknown) => setError(value instanceof ApiError ? value.message : value instanceof Error ? value.message : '操作失败')
  return { error, setError, capture }
}

function ImageView({ src, alt, className = '' }: { src?: string; alt: string; className?: string }) {
  const protectedMedia = src?.startsWith('/api/files/') ?? false
  const [failed, setFailed] = useState(false)
  const [displaySrc, setDisplaySrc] = useState(protectedMedia ? '' : asset(src))

  useEffect(() => {
    setFailed(false)
    if (!protectedMedia || !src) {
      setDisplaySrc(asset(src))
      return
    }

    const token = session.get()
    if (!token) {
      setFailed(true)
      return
    }

    const controller = new AbortController()
    let objectUrl = ''
    setDisplaySrc('')
    void fetch(src, {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal,
      cache: 'force-cache',
    }).then((response) => {
      if (!response.ok) throw new Error(`图片加载失败：${response.status}`)
      return response.blob()
    }).then((blob) => {
      objectUrl = URL.createObjectURL(blob)
      setDisplaySrc(objectUrl)
    }).catch((error: unknown) => {
      if (!(error instanceof DOMException && error.name === 'AbortError')) setFailed(true)
    })

    return () => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [protectedMedia, src])

  if (failed) return <div className={`image-fallback ${className}`}><Shirt size={28} /></div>
  if (!displaySrc) return <div className={`image-fallback ${className}`}><LoaderCircle className="spin" size={22} /></div>
  return <img className={className} src={displaySrc} alt={alt} onError={() => setFailed(true)} />
}

function Login({ onLogin }: { onLogin: (user: AuthUser) => void }) {
  const [username, setUsername] = useState('demo'); const [password, setPassword] = useState('wardrobe123')
  const [busy, setBusy] = useState(false); const { error, setError, capture } = useAsyncError()
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try { const data = await api.login(username, password); session.set(data.token); onLogin(data.user) } catch (e) { capture(e) } finally { setBusy(false) }
  }
  return <main className="login-page">
    <section className="login-visual" aria-label="智能衣橱预览">
      <div className="brand light"><span>拾</span> 拾搭</div>
      <div className="visual-copy"><p>WARDROBE AGENT</p><h1>从你的衣橱里，<br />找到今天的答案。</h1></div>
      <div className="look-strip">
        {[1, 7, 13].map((n) => <ImageView key={n} src={`/assets/seed/item-${String(n).padStart(2, '0')}.png`} alt="示例衣物" />)}
      </div>
    </section>
    <section className="login-form-wrap">
      <form className="login-form" onSubmit={submit}>
        <div className="brand dark"><span>拾</span> 拾搭</div>
        <div><p className="eyebrow">欢迎回来</p><h2>登录你的智能衣橱</h2></div>
        <label>用户名<input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" /></label>
        <label>密码<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></label>
        {error && <p className="form-error">{error}</p>}
        <button className="primary wide" disabled={busy}>{busy ? <LoaderCircle className="spin" size={18} /> : <ArrowRight size={18} />}进入衣橱</button>
        <p className="demo-note">演示账号已预填，可直接登录</p>
      </form>
    </section>
  </main>
}

function Shell({ user, onLogout }: { user: AuthUser; onLogout: () => void }) {
  const [menu, setMenu] = useState(false)
  const links = [
    ['/agent', <Sparkles />, '搭配助手'], ['/wardrobe', <Shirt />, '我的衣橱'],
    ['/models', <CircleUserRound />, '我的形象'], ['/history', <Archive />, '方案记录'],
  ] as const
  return <div className="app-shell">
    <aside className={menu ? 'sidebar open' : 'sidebar'}>
      <div className="brand dark"><span>拾</span> 拾搭</div>
      <button className="icon-button close-menu" onClick={() => setMenu(false)} aria-label="关闭菜单"><X /></button>
      <nav>{links.map(([to, icon, label]) => <NavLink key={to} to={to} onClick={() => setMenu(false)}>{icon}{label}</NavLink>)}</nav>
      <div className="sidebar-user"><div className="avatar">{user.displayName.slice(0, 1)}</div><div><strong>{user.displayName}</strong><span>@{user.username}</span></div><button className="icon-button" onClick={onLogout} title="退出登录"><LogOut size={18} /></button></div>
    </aside>
    <div className="mobile-head"><button className="icon-button" onClick={() => setMenu(true)} aria-label="打开菜单"><Menu /></button><div className="brand dark"><span>拾</span> 拾搭</div></div>
    <section className="app-content"><Routes>
      <Route path="/agent" element={<AgentPage />} />
      <Route path="/wardrobe" element={<WardrobePage />} />
      <Route path="/models" element={<ModelsPage />} />
      <Route path="/history" element={<HistoryPage />} />
      <Route path="*" element={<Navigate to="/agent" replace />} />
    </Routes></section>
    {menu && <button className="scrim" onClick={() => setMenu(false)} aria-label="关闭菜单" />}
  </div>
}

function PageHeader({ eyebrow, title, actions }: { eyebrow: string; title: string; actions?: ReactNode }) {
  return <header className="page-header"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1></div>{actions && <div className="header-actions">{actions}</div>}</header>
}

type AgentActivity = 'thinking' | 'searching' | 'composing' | 'validating'

const agentActivityLabels: Record<AgentActivity, string> = {
  thinking: '正在思考',
  searching: '正在检索你的衣橱',
  composing: '正在整理搭配方案',
  validating: '正在校验搭配',
}

function AgentPage() {
  const [conversation, setConversation] = useState<Conversation | null>(null)
  const [run, setRun] = useState<AgentRun | null>(null)
  const [prompt, setPrompt] = useState(''); const [assistantText, setAssistantText] = useState('')
  const [busy, setBusy] = useState(false); const [pendingUserText, setPendingUserText] = useState('')
  const [activity, setActivity] = useState<AgentActivity>('thinking')
  const [wardrobe, setWardrobe] = useState<WardrobeItem[]>([]); const [locked, setLocked] = useState<string[]>([])
  const [picker, setPicker] = useState(false); const [tryOnPlan, setTryOnPlan] = useState<Outfit | null>(null)
  const { error, setError, capture } = useAsyncError()
  useEffect(() => {
    void Promise.all([api.conversations(), api.wardrobe()]).then(async ([conversations, wardrobeItems]) => {
      const storedId = sessionStorage.getItem(ACTIVE_CONVERSATION_KEY)
      const summary = conversations.find((item) => item.id === storedId) ?? conversations[0]
      const current = summary ? await api.conversation(summary.id) : await api.createConversation()
      sessionStorage.setItem(ACTIVE_CONVERSATION_KEY, current.id)
      setConversation(current); setWardrobe(wardrobeItems)
      const activeRun = (await api.agentRuns(current.id)).find((item) => ['QUEUED', 'RUNNING'].includes(item.status))
      if (activeRun) {
        setRun(activeRun); setBusy(true); setPendingUserText(activeRun.content); setAssistantText('')
      }
    }).catch(capture)
  }, [])

  useEffect(() => {
    if (!run || !['QUEUED', 'RUNNING'].includes(run.status)) return
    return api.watchAgentRun(run.id, (updated) => {
      setRun(updated)
      if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(updated.status)) {
        setBusy(false); setPendingUserText('')
        if (updated.status === 'FAILED') capture(new Error(updated.errorMessage ?? '对话生成失败'))
        if (conversation) void api.conversation(conversation.id).then(setConversation).catch(capture)
      }
    }, (event) => {
      if (event.type === 'message.delta') setAssistantText((current) => current + (event.data as { content: string }).content)
      const tool = (event.data as { tool?: string }).tool
      if (event.type === 'tool.started' && tool === 'searchWardrobe') setActivity('searching')
      if (event.type === 'tool.completed' && tool === 'searchWardrobe') setActivity('composing')
      if (event.type === 'tool.started' && tool === 'evaluateOutfit') setActivity('validating')
    }, capture, () => setError(''))
  }, [run?.id, conversation?.id])

  async function send(content = prompt, action?: string, sourceOutfitId?: string) {
    const submittedContent = content.trim()
    if (!conversation || !submittedContent || busy) return
    setBusy(true); setError(''); setPendingUserText(submittedContent); setAssistantText(''); setActivity('thinking'); setPrompt('')
    try {
      const created = await api.createAgentRun(conversation.id, { clientMessageId: crypto.randomUUID(), content: submittedContent, lockedItemIds: locked, excludedItemIds: [], action, sourceOutfitId })
      setRun(created)
    } catch (e) { capture(e); setBusy(false); setPendingUserText('') }
  }
  async function newConversation() {
    if (busy) return
    try {
      const created = await api.createConversation(); sessionStorage.setItem(ACTIVE_CONVERSATION_KEY, created.id)
      setConversation(created); setRun(null); setAssistantText(''); setPendingUserText(''); setError('')
    } catch (e) { capture(e) }
  }
  async function mutatePlan(id: string, action: 'save' | 'confirm') {
    try { action === 'save' ? await api.saveOutfit(id) : await api.confirmOutfit(id); if (conversation) setConversation(await api.conversation(conversation.id)) } catch (e) { capture(e) }
  }
  const outfitsByRun = useMemo(() => {
    const grouped = new Map<string, Outfit[]>()
    for (const outfit of conversation?.outfits ?? []) {
      const related = grouped.get(outfit.recommendationRunId) ?? []
      related.push(outfit)
      grouped.set(outfit.recommendationRunId, related)
    }
    return grouped
  }, [conversation?.outfits])
  const linkedRunIds = new Set(conversation?.messages.map((message) => message.recommendationRunId).filter(Boolean))
  const unlinkedOutfits = conversation?.outfits.filter((outfit) => !linkedRunIds.has(outfit.recommendationRunId)) ?? []
  return <div className="page agent-page">
    <PageHeader eyebrow="AI STYLIST" title="今天想怎么穿？" actions={<button className="secondary" disabled={busy} onClick={() => void newConversation()}><MessageSquarePlus size={17} />新对话</button>} />
    <div className="agent-layout">
      <section className="chat-panel">
        {!conversation?.messages.length && !busy && <div className="empty-chat"><div className="spark-mark"><Sparkles /></div><h2>告诉我场景、天气或你的偏好</h2><div className="suggestions">
          {['上海 22 度小雨，通勤见客户', '周末看展，想穿得松弛一点', '今晚朋友聚餐，简洁但有记忆点'].map((text) => <button key={text} onClick={() => void send(text)}>{text}<ChevronRight size={16} /></button>)}
        </div></div>}
        <div className="messages">
          {conversation?.messages.map((m) => <div className="conversation-turn" key={m.id}>
            <div className={`message ${m.role.toLowerCase()}`}><span>{m.role === 'ASSISTANT' ? <Bot size={17} /> : '你'}</span><p>{m.content}</p></div>
            {m.role === 'ASSISTANT' && m.recommendationRunId && outfitsByRun.get(m.recommendationRunId)?.map((outfit) =>
              <OutfitCard key={outfit.id} outfit={outfit} onSave={() => void mutatePlan(outfit.id, 'save')} onConfirm={() => void mutatePlan(outfit.id, 'confirm')} onTryOn={() => setTryOnPlan(outfit)} onExplore={() => void send(outfit.scene, 'EXPLORE_MORE', outfit.id)} />)}
          </div>)}
          {busy && <div className="conversation-turn pending-turn">
            <div className="message user"><span>你</span><p>{pendingUserText}</p></div>
            <div className="message assistant"><span><Bot size={17} /></span><div aria-live="polite">{assistantText ? <p className="streaming-text">{assistantText}<i className="typing-cursor" aria-hidden="true" /></p> : <span className="thinking"><LoaderCircle className="spin" size={16} />{agentActivityLabels[activity]}</span>}</div></div>
          </div>}
          {unlinkedOutfits.map((outfit) => <OutfitCard key={outfit.id} outfit={outfit} onSave={() => void mutatePlan(outfit.id, 'save')} onConfirm={() => void mutatePlan(outfit.id, 'confirm')} onTryOn={() => setTryOnPlan(outfit)} onExplore={() => void send(outfit.scene, 'EXPLORE_MORE', outfit.id)} />)}
        </div>
        <div className="composer">
          {locked.length > 0 && <div className="locked-row"><Lock size={14} />已指定 {locked.length} 件衣物<button onClick={() => setLocked([])}>清除</button></div>}
          <textarea value={prompt} onChange={(e) => setPrompt(e.target.value)} placeholder="例如：北京 18 度，明天去面试，想显得专业但不拘谨" onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void send() } }} />
          <div><button className="secondary icon-text" onClick={() => setPicker(true)}><Shirt size={17} />指定衣物</button><button className="primary send-button" disabled={busy || !prompt.trim()} onClick={() => void send()} aria-label="发送"><Send size={18} /></button></div>
        </div>
        {error && <p className="inline-error">{error}</p>}
      </section>
      <aside className="context-panel"><p className="eyebrow">衣橱概览</p><strong>{wardrobe.length}<small> 件</small></strong><div className="mini-rail">{wardrobe.slice(0, 5).map((item) => <ImageView key={item.id} src={item.imageUrl} alt={item.name} />)}</div><p>AI 只会从已录入的衣物中生成方案。</p></aside>
    </div>
    {picker && <Modal title="指定本次要穿的衣物" onClose={() => setPicker(false)}><div className="picker-grid">{wardrobe.map((item) => { const selected = locked.includes(item.id); return <button className={selected ? 'picker-item selected' : 'picker-item'} key={item.id} onClick={() => setLocked(selected ? locked.filter((id) => id !== item.id) : locked.length < 2 ? [...locked, item.id] : locked)}><ImageView src={item.imageUrl} alt={item.name} /><span>{item.name}</span>{selected && <Check />}</button> })}</div><button className="primary wide" onClick={() => setPicker(false)}>确定选择</button></Modal>}
    {tryOnPlan && <TryOnDialog plan={tryOnPlan} onClose={() => setTryOnPlan(null)} />}
  </div>
}

function OutfitCard({ outfit, onSave, onConfirm, onTryOn, onExplore }: { outfit: Outfit; onSave: () => void; onConfirm: () => void; onTryOn: () => void; onExplore: () => void }) {
  return <article className="outfit-card">
    <div className="outfit-head"><div><span className={`status ${outfit.status.toLowerCase()}`}>{statusNames[outfit.status]}</span><h3>{outfit.title}</h3></div><span>{outfit.consideredCount}/{outfit.eligibleTotal} 件已评估</span></div>
    <div className="outfit-items">{outfit.selectedItems.map((item) => <div key={item.itemId}><div className="outfit-image"><ImageView src={item.imageUrl} alt={item.name} />{item.locked && <Lock size={14} />}</div><strong>{item.name}</strong><span>{slotNames[item.slot] ?? item.slot}</span></div>)}</div>
    <p className="reason">{outfit.reason}</p>
    <div className="outfit-actions"><button className="secondary" onClick={onExplore}><RefreshCw size={16} />换一套</button>{outfit.status === 'DRAFT' && <button className="secondary" onClick={onSave}>收藏</button>}{outfit.status !== 'CONFIRMED' ? <button className="primary" onClick={onConfirm}><Check size={17} />确认方案</button> : <button className="primary" onClick={onTryOn}><Images size={17} />生成试穿</button>}</div>
  </article>
}

function TryOnDialog({ plan, onClose }: { plan: Outfit; onClose: () => void }) {
  const [models, setModels] = useState<UserModel[]>([]); const [modelId, setModelId] = useState('')
  const [capabilities, setCapabilities] = useState<TryOnCapabilities | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set()); const [consent, setConsent] = useState(false)
  const [task, setTask] = useState<TryOn | null>(null); const [busy, setBusy] = useState(false); const { error, setError, capture } = useAsyncError()
  useEffect(() => {
    void Promise.all([api.models(), api.tryOnCapabilities(plan.id, plan.version), api.tryOns()]).then(([modelData, capabilityData, tasks]) => {
      setModels(modelData); setModelId(modelData.find((model) => model.defaultModel)?.id ?? modelData[0]?.id ?? '')
      setCapabilities(capabilityData); setSelected(defaultTryOnSelection(capabilityData))
      setTask(tasks.find((item) => item.planId === plan.id && isTryOnActive(item.status)) ?? null)
    }).catch(capture)
  }, [plan.id, plan.version])
  useEffect(() => {
    if (!task || !isTryOnActive(task.status)) return
    return api.watchTryOn(task.id, setTask, capture, () => setError(''))
  }, [task?.id])
  async function generate() {
    if (!modelId || !capabilities || selected.size === 0) return; setBusy(true)
    try {
      const selectedItemIds = [...selected]
      const confirmation = await api.createConfirmation({ planId: plan.id, planVersion: plan.version, userModelId: modelId, provider: capabilities.providerCode, selectedItemIds, consent })
      setTask(await api.createTryOn({ confirmationToken: confirmation.token, idempotencyKey: crypto.randomUUID(), userModelId: modelId, selectedItemIds, simulateFailure: false, retryFromTaskId: null }))
    } catch (e) { capture(e) } finally { setBusy(false) }
  }
  const selectableItems = capabilities ? [...capabilities.supportedItems, ...capabilities.unsupportedItems] : []
  const complete = task && ['SUCCEEDED', 'PARTIALLY_SUCCEEDED'].includes(task.status)
  return <Modal title="生成虚拟试穿" onClose={onClose} wide>
    {!task ? <>{!capabilities ? <div className="processing"><LoaderCircle className="spin" /><span>读取试穿能力</span></div> : <>
      <div className="tryon-provider"><div><strong>{capabilities.displayName}</strong><span>{capabilities.coverage === 'MULTI_STAGE' ? '分阶段整套试穿' : '单件试穿'}</span></div><small>预计 {capabilities.estimatedMinSeconds}-{capabilities.estimatedMaxSeconds} 秒{capabilities.remainingToday >= 0 ? ` · 今日剩余 ${capabilities.remainingToday} 次` : ''}</small></div>
      {capabilities.providerCode === 'GPT_IMAGE_EDIT' && <p className="preview-notice">当前使用 GPT Image 双图编辑：每个阶段都会读取上一张人物结果和当前商品图。它会尽量保持人物与服装细节，但仍属于 AI 效果预览。</p>}
      {capabilities.providerCode === 'SPRING_AI_IMAGE' && <p className="preview-notice">当前使用 Spring AI 通用图片模型直接生成低保真效果预览，不保证保持所选人物身份、原服装颜色、图案或版型。</p>}
      {capabilities.fallbackEnabled && <p className="preview-notice">FASHN 暂时不可用时，将自动改用 Spring AI 生成效果预览。预览可能无法完整保持人物与服装细节。</p>}
      <p className="modal-intro">已默认勾选方案中可试穿的衣物。取消不想生成的单品后，再选择人物形象。</p>
      <div className="tryon-item-list">{selectableItems.map((item) => <label key={item.itemId} className={!item.supported ? 'tryon-item unsupported' : 'tryon-item'}>
        <input type="checkbox" checked={selected.has(item.itemId)} disabled={!item.supported} onChange={() => setSelected((current) => toggleTryOnItem(current, item, selectableItems))} />
        <ImageView src={item.imageUrl} alt={item.name} /><span><strong>{item.name}</strong><small>{item.supported ? slotNames[item.slot] : item.reason}</small></span>
      </label>)}</div>
      <div className="model-picker">{models.map((model) => <button key={model.id} className={modelId === model.id ? 'model-choice selected' : 'model-choice'} onClick={() => setModelId(model.id)}><ImageView src={model.imageUrl} alt={model.name} /><span>{model.name}</span>{modelId === model.id && <Check />}</button>)}</div>
      {capabilities.requiresExplicitConsent && <label className="consent-row"><input type="checkbox" checked={consent} onChange={(event) => setConsent(event.target.checked)} /><span>{capabilities.providerCode === 'GPT_IMAGE_EDIT' ? '我同意将所选人物与衣物图片发送给已配置的 GPT Image 图片编辑服务，并按所选顺序逐件生成效果预览' : capabilities.providerCode === 'SPRING_AI_IMAGE' ? '我同意调用已配置的 Spring AI 图片服务；当前通用生图只根据受控穿搭描述生成预览，不保证保持所选人物身份与原服装图片细节' : '我同意将所选人物与衣物图片发送给 FASHN；若主服务发生可降级故障，也同意由已配置的 Spring AI 图片服务生成效果预览'}</span></label>}
      <button className="primary wide" disabled={!capabilities.enabled || !modelId || selected.size === 0 || busy || (capabilities.requiresExplicitConsent && !consent)} onClick={() => void generate()}>{busy ? <LoaderCircle className="spin" /> : <Sparkles />}生成已选 {selected.size} 件</button>
    </>}</> : <div className="tryon-result">
        {complete && task.resultImageUrl && <ImageView src={task.resultImageUrl} alt="虚拟试穿结果" />}
        <h3>{task.status === 'SUCCEEDED' ? (task.resultKind === 'AI_PREVIEW' ? 'AI 效果预览已生成' : '试穿效果已生成') : task.status === 'PARTIALLY_SUCCEEDED' ? '已生成部分试穿效果' : statusNames[task.status] ?? task.status}</h3>
        {task.resultKind === 'AI_PREVIEW' && <p className="preview-notice"><strong>{task.effectiveProvider === 'GPT_IMAGE_EDIT' ? 'GPT Image 双图编辑预览' : 'Spring AI 效果预览'}</strong><span>人物身份、衣物颜色、图案和版型可能与原图不完全一致。</span>{task.fallbackReason && <small>切换原因：{task.fallbackReason}</small>}</p>}
        {isTryOnActive(task.status) && <div className="processing"><LoaderCircle className="spin" /><span>正在按层次逐件生成</span></div>}
        <div className="tryon-stage-list">{task.items.filter((item) => item.selected).map((item) => <div key={item.itemId}><span className={`stage-dot ${item.status.toLowerCase()}`}>{item.status === 'SUCCEEDED' ? <Check size={13} /> : item.sequence}</span><div><strong>{item.name}</strong><small>{statusNames[item.status] ?? item.status}{item.effectiveProvider ? ` · ${item.effectiveProvider === 'SPRING_AI_IMAGE' ? 'Spring AI 预览' : item.effectiveProvider === 'GPT_IMAGE_EDIT' ? 'GPT Image 编辑' : item.effectiveProvider}` : ''}{item.errorMessage ? ` · ${item.errorMessage}` : ''}</small></div></div>)}</div>
        {task.errorMessage && <p className="inline-error">{task.errorMessage}</p>}
        {complete && <p>已渲染 {task.renderedItemIds.length}/{task.selectedItemIds.length} 件所选衣物，未支持的鞋履和配饰不计入结果。</p>}
      </div>}
    {error && <p className="inline-error">{error}</p>}
  </Modal>
}

function WardrobePage() {
  const [allItems, setItems] = useState<WardrobeItem[]>([]); const [slot, setSlot] = useState('')
  const [add, setAdd] = useState(false); const { error, setError, capture } = useAsyncError()
  const load = () => api.wardrobe().then(setItems).catch(capture)
  useEffect(() => { void load() }, [])
  const items = slot ? allItems.filter((item) => item.slot === slot) : allItems
  async function remove(id: string) { if (!confirm('确定从衣橱中移除这件衣物吗？')) return; try { await api.deleteItem(id); await load() } catch (e) { capture(e) } }
  const groups = useMemo(() => new Map(Object.entries(slotNames)), [])
  return <div className="page"><PageHeader eyebrow="MY WARDROBE" title="我的衣橱" actions={<button className="primary" onClick={() => setAdd(true)}><Plus size={18} />添加衣物</button>} />
    <div className="filter-tabs"><button className={!slot ? 'active' : ''} onClick={() => setSlot('')}>全部 <span>{allItems.length}</span></button>{[...groups.entries()].map(([key, value]) => <button key={key} className={slot === key ? 'active' : ''} onClick={() => setSlot(key)}>{value}</button>)}</div>
    {error && <p className="inline-error">{error}</p>}
    <div className="wardrobe-grid">{items.map((item) => <article className="garment" key={item.id}><div className="garment-image"><ImageView src={item.imageUrl} alt={item.name} /><button className="icon-button danger" onClick={() => void remove(item.id)} title="删除"><Trash2 size={17} /></button></div><div><span>{slotNames[item.slot]}</span><h3>{item.name}</h3><p>{item.color} · {item.material}</p><div className="tags">{item.styleTags.slice(0, 2).map((tag) => <em key={tag}>{tag}</em>)}</div></div></article>)}</div>
    {add && <AddItemDialog onClose={() => setAdd(false)} onCreated={() => { setAdd(false); setError(''); void load() }} />}
  </div>
}

function AddItemDialog({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [file, setFile] = useState<File | null>(null); const [name, setName] = useState(''); const [color, setColor] = useState('')
  const [slot, setSlot] = useState<WardrobeSlot>('INNER_TOP'); const [busy, setBusy] = useState(false); const { error, capture } = useAsyncError()
  async function submit(event: FormEvent) {
    event.preventDefault(); if (!file) return; setBusy(true)
    try { const media = await api.upload(file, 'WARDROBE'); await api.createItem({ name, category: slot, slot, color, material: '', seasonTags: ['四季'], sceneTags: [], styleTags: [], warmthLevel: 3, breathabilityLevel: 3, imageFileId: media.id }); onCreated() } catch (e) { capture(e) } finally { setBusy(false) }
  }
  return <Modal title="添加一件衣物" onClose={onClose}><form className="stack-form" onSubmit={submit}><label className="upload-box"><Upload /><strong>{file ? file.name : '选择衣物图片'}</strong><span>JPEG、PNG 或 WebP，最大 10MB</span><input type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></label><label>名称<input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} placeholder="例如：灰色羊毛西装" /></label><div className="form-row"><label>位置<select value={slot} onChange={(e) => setSlot(e.target.value as WardrobeSlot)}>{Object.entries(slotNames).map(([key, value]) => <option key={key} value={key}>{value}</option>)}</select></label><label>颜色<input value={color} onChange={(e) => setColor(e.target.value)} placeholder="灰色" /></label></div>{error && <p className="form-error">{error}</p>}<button className="primary wide" disabled={busy || !file}>{busy ? <LoaderCircle className="spin" /> : <Plus />}加入衣橱</button></form></Modal>
}

function ModelsPage() {
  const [models, setModels] = useState<UserModel[]>([]); const [uploading, setUploading] = useState(false); const { error, capture } = useAsyncError()
  const load = () => api.models().then(setModels).catch(capture); useEffect(() => { void load() }, [])
  async function upload(file?: File) { if (!file) return; setUploading(true); try { const media = await api.upload(file, 'USER_MODEL'); await api.createModel({ name: file.name.replace(/\.[^.]+$/, ''), fileId: media.id, authorized: true }); await load() } catch (e) { capture(e) } finally { setUploading(false) } }
  async function makeDefault(id: string) { try { await api.defaultModel(id); await load() } catch (e) { capture(e) } }
  async function remove(id: string) { try { await api.deleteModel(id); await load() } catch (e) { capture(e) } }
  return <div className="page"><PageHeader eyebrow="MY MODELS" title="我的形象" actions={<label className="primary file-button">{uploading ? <LoaderCircle className="spin" /> : <Upload size={18} />}上传形象<input type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => void upload(e.target.files?.[0])} /></label>} />
    <p className="page-lead">选择默认形象，用于虚拟试穿效果的生成。上传本人照片即表示你确认拥有该照片的使用权。</p>{error && <p className="inline-error">{error}</p>}
    <div className="models-grid">{models.map((model) => <article key={model.id} className={model.defaultModel ? 'model-card default' : 'model-card'}><ImageView src={model.imageUrl} alt={model.name} /><div><h3>{model.name}</h3><span>{model.type === 'PRESET' ? '预设形象' : '我的上传'}</span></div>{model.defaultModel ? <b><Check size={15} />默认</b> : <button className="secondary" onClick={() => void makeDefault(model.id)}>设为默认</button>}{model.type === 'UPLOAD' && <button className="icon-button danger model-delete" onClick={() => void remove(model.id)} title="删除"><Trash2 size={17} /></button>}</article>)}</div>
  </div>
}

function HistoryPage() {
  const [outfits, setOutfits] = useState<Outfit[]>([]); const [tasks, setTasks] = useState<TryOn[]>([]); const [tab, setTab] = useState<'outfits' | 'tryons'>('outfits'); const { error, capture } = useAsyncError()
  useEffect(() => { void Promise.all([api.outfits(), api.tryOns()]).then(([o, t]) => { setOutfits(o); setTasks(t) }).catch(capture) }, [])
  const activeTaskIds = tasks.filter((task) => isTryOnActive(task.status)).map((task) => task.id).sort().join(',')
  useEffect(() => {
    const stops = activeTaskIds.split(',').filter(Boolean).map((id) => api.watchTryOn(id, (updated) => {
      setTasks((current) => current.map((item) => item.id === updated.id ? updated : item))
    }, capture))
    return () => stops.forEach((stop) => stop())
  }, [activeTaskIds])
  return <div className="page"><PageHeader eyebrow="HISTORY" title="方案记录" /><div className="segmented"><button className={tab === 'outfits' ? 'active' : ''} onClick={() => setTab('outfits')}>搭配方案 <span>{outfits.length}</span></button><button className={tab === 'tryons' ? 'active' : ''} onClick={() => setTab('tryons')}>试穿记录 <span>{tasks.length}</span></button></div>{error && <p className="inline-error">{error}</p>}
    {tab === 'outfits' ? <div className="history-list">{outfits.map((outfit) => <article key={outfit.id} className="history-row"><div className="history-thumbs">{outfit.selectedItems.slice(0, 3).map((item) => <ImageView key={item.itemId} src={item.imageUrl} alt={item.name} />)}</div><div className="history-copy"><span className={`status ${outfit.status.toLowerCase()}`}>{statusNames[outfit.status]}</span><h3>{outfit.title}</h3><p>{outfit.scene}</p></div><time><Clock3 size={15} />{new Date(outfit.createdAt).toLocaleDateString('zh-CN')}</time></article>)}</div> : <div className="tryon-grid">{tasks.map((task) => <article key={task.id} className="tryon-card"><ImageView src={task.resultImageUrl || task.items[0]?.imageUrl} alt="试穿记录" /><div><span className={`status ${task.status.toLowerCase()}`}>{statusNames[task.status] ?? task.status}</span><h3>{task.status === 'SUCCEEDED' ? (task.resultKind === 'AI_PREVIEW' ? 'AI 效果预览' : '虚拟试穿结果') : '试穿任务'}</h3><p>{task.renderedItemIds.length} 件衣物参与渲染{task.fallbackUsed ? ' · 已使用兜底' : ''}</p></div></article>)}</div>}
    {((tab === 'outfits' && !outfits.length) || (tab === 'tryons' && !tasks.length)) && <EmptyState />}
  </div>
}

function EmptyState() { const navigate = useNavigate(); return <div className="empty-state"><Archive /><h3>这里还没有记录</h3><button className="primary" onClick={() => navigate('/agent')}>去生成搭配</button></div> }
function Modal({ title, onClose, children, wide = false }: { title: string; onClose: () => void; children: ReactNode; wide?: boolean }) { return <div className="modal-layer"><button className="modal-scrim" onClick={onClose} aria-label="关闭弹窗" /><section className={wide ? 'modal wide-modal' : 'modal'} role="dialog" aria-modal="true"><header><h2>{title}</h2><button className="icon-button" onClick={onClose} aria-label="关闭"><X /></button></header><div className="modal-body">{children}</div></section></div> }

export default function App() {
  const [user, setUser] = useState<AuthUser | null>(() => session.get() ? { id: '', username: 'demo', displayName: '演示用户', wardrobeRevision: 0 } : null)
  if (!user) return <Login onLogin={setUser} />
  return <Shell user={user} onLogout={() => { session.clear(); setUser(null) }} />
}
