/**
 * <streamx-chat> — Web Component
 *
 * Usage:
 *   <script type="module" src="streamx-chat.js"></script>
 *   <streamx-chat api-url="http://localhost:8081" title="Assistant"></streamx-chat>
 *
 * Attributes:
 *   api-url      – RAG service base URL (default: same origin as the page)
 *   title        – Header title          (default: Assistant)
 *   placeholder  – Input placeholder     (default: Type your question…)
 *   welcome      – First bot message     (default: generic, content-agnostic)
 *   auto-open    – Open on load after N ms, e.g. auto-open="1500"
 */
class StreamxChat extends HTMLElement {

  /** Default greeting when no `welcome` attribute is set — domain-neutral. */
  static DEFAULT_WELCOME =
    'How can I help you today? Ask in your own words — I will answer in the same language you use.';

  /* ─── observed attributes ─────────────────────────────────── */
  static get observedAttributes() {
    return ['api-url', 'title', 'placeholder', 'welcome', 'auto-open'];
  }

  constructor() {
    super();
    this._shadow    = this.attachShadow({ mode: 'open' });
    this._open      = false;
    this._streaming = false;
    this._sessionId = this._loadOrCreateSession();
  }

  _loadOrCreateSession() {
    const key = 'streamx-chat-session';
    let id = sessionStorage.getItem(key);
    if (!id) {
      id = typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : Math.random().toString(36).slice(2) + Date.now().toString(36);
      sessionStorage.setItem(key, id);
    }
    return id;
  }

  /* ─── lifecycle ────────────────────────────────────────────── */
  connectedCallback() {
    this._injectFont();
    this._render();
    this._bind();

    const delay = parseInt(this.getAttribute('auto-open') ?? '0', 10);
    if (delay > 0) setTimeout(() => this._toggleChat(), delay);
  }

  attributeChangedCallback(name, _old, val) {
    if (!this._shadow.querySelector('.w-root')) return; // not rendered yet
    if (name === 'title')       this._shadow.querySelector('.w-header-name').textContent = val;
    if (name === 'placeholder') this._shadow.querySelector('.w-textarea').placeholder    = val;
    if (name === 'welcome') {
      const el = this._shadow.getElementById('welcomeBubble');
      if (el) el.innerHTML = (val || StreamxChat.DEFAULT_WELCOME).replace(/\n/g, '<br>');
    }
  }

  /* ─── font (inject once into real document head) ────────────── */
  _injectFont() {
    const id = 'streamx-inter-font';
    if (document.getElementById(id)) return;
    const link = document.createElement('link');
    link.id   = id;
    link.rel  = 'stylesheet';
    link.href = 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap';
    document.head.appendChild(link);
  }

  /* ─── template ─────────────────────────────────────────────── */
  _render() {
    let apiUrl        = this.getAttribute('api-url')     || window.location.origin;
    const title       = this.getAttribute('title')       || 'Assistant';
    const placeholder = this.getAttribute('placeholder') || 'Type your question…';
    const welcomeRaw  = this.getAttribute('welcome')       || StreamxChat.DEFAULT_WELCOME;
    const welcomeHtml = welcomeRaw
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/\n/g, '<br>');

    // Auto-replace localhost with the actual page hostname so the widget
    // works transparently both on local machine and on LAN (e.g. 192.168.x.x)
    const pageHost = window.location.hostname;
    if (pageHost && pageHost !== 'localhost' && pageHost !== '127.0.0.1') {
      apiUrl = apiUrl.replace(/localhost|127\.0\.0\.1/, pageHost);
    }

    this._shadow.innerHTML = `
      <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        :host { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                -webkit-font-smoothing: antialiased; }

        /* ── variables ── */
        .w-root {
          --w-blue:    #2563EB;
          --w-blue-dk: #1D4ED8;
          --w-header:  #0F172A;
          --w-white:   #FFFFFF;
          --w-bg:      #F8FAFC;
          --w-bd:      #E2E8F0;
          --w-text:    #0F172A;
          --w-muted:   #64748B;
          --w-bot-bg:  #F8FAFC;
          --w-shadow:  0 4px 24px rgba(0,0,0,.11), 0 1px 4px rgba(0,0,0,.06);
        }

        /* ── bubble trigger ── */
        #chat-bubble {
          position: fixed; bottom: 28px; right: 28px; z-index: 10000;
          width: 52px; height: 52px; border-radius: 14px;
          background: var(--w-header);
          box-shadow: 0 4px 16px rgba(0,0,0,.22);
          border: none; cursor: pointer;
          display: flex; align-items: center; justify-content: center;
          transition: transform .18s, box-shadow .18s, background .15s;
          outline: none;
        }
        #chat-bubble:hover {
          transform: translateY(-2px);
          box-shadow: 0 8px 24px rgba(0,0,0,.28);
          background: #1E293B;
        }
        #chat-bubble svg { transition: opacity .15s, transform .15s; }
        #chat-bubble .icon-open  { position: absolute; }
        #chat-bubble .icon-close { position: absolute; opacity: 0; transform: rotate(-90deg) scale(.6); }
        #chat-bubble.open .icon-open  { opacity: 0; transform: rotate(90deg) scale(.6); }
        #chat-bubble.open .icon-close { opacity: 1; transform: rotate(0deg) scale(1); }

        /* badge */
        #chat-bubble .badge {
          position: absolute; top: -3px; right: -3px;
          width: 18px; height: 18px; border-radius: 50%;
          background: #EF4444; border: 2px solid #fff;
          font-size: .65rem; font-weight: 700; color: #fff;
          display: flex; align-items: center; justify-content: center;
          animation: badge-pop .3s cubic-bezier(.34,1.56,.64,1);
        }
        @keyframes badge-pop { from { transform: scale(0); } to { transform: scale(1); } }

        /* ── panel ── */
        #chat-panel {
          position: fixed; bottom: 92px; right: 28px; z-index: 9999;
          width: 384px;
          background: var(--w-white);
          border-radius: 12px;
          box-shadow: var(--w-shadow);
          border: 1px solid var(--w-bd);
          display: flex; flex-direction: column;
          overflow: hidden;
          transform: translateY(20px) scale(.95);
          opacity: 0;
          pointer-events: none;
          transform-origin: bottom right;
          transition: transform .25s cubic-bezier(.34,1.2,.64,1), opacity .2s ease;
          max-height: calc(100dvh - 130px);
        }
        #chat-panel.open {
          transform: translateY(0) scale(1);
          opacity: 1;
          pointer-events: all;
        }

        /* header */
        .w-header {
          display: flex; align-items: center; gap: 11px;
          padding: 14px 16px 13px;
          background: var(--w-header);
          color: var(--w-white);
          flex-shrink: 0;
          border-radius: 12px 12px 0 0;
          border-bottom: 1px solid rgba(255,255,255,.07);
        }
        .w-avatar {
          width: 34px; height: 34px; border-radius: 8px;
          background: rgba(255,255,255,.12);
          display: flex; align-items: center; justify-content: center;
          flex-shrink: 0;
        }
        .w-header-info { flex: 1; min-width: 0; }
        .w-header-name { font-size: .88rem; font-weight: 600; letter-spacing: -.01em; }
        .w-header-sub  {
          font-size: .69rem; opacity: .55; margin-top: 2px;
          display: flex; align-items: center; gap: 5px;
          font-weight: 400;
        }
        .w-status {
          width: 6px; height: 6px; border-radius: 50%;
          background: #4ADE80; flex-shrink: 0;
          animation: pulse 2s infinite;
        }
        @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.35} }
        .w-header-actions { display: flex; gap: 2px; flex-shrink: 0; }
        .w-icon-btn {
          width: 28px; height: 28px; border-radius: 6px; border: none;
          background: transparent; color: rgba(255,255,255,.5); cursor: pointer;
          display: flex; align-items: center; justify-content: center;
          transition: background .12s, color .12s;
        }
        .w-icon-btn:hover { background: rgba(255,255,255,.1); color: #fff; }

        /* messages */
        .w-messages {
          flex: 1; overflow-y: auto;
          padding: 16px 14px;
          display: flex; flex-direction: column; gap: 12px;
          scroll-behavior: smooth; min-height: 0;
        }
        .w-messages::-webkit-scrollbar { width: 3px; }
        .w-messages::-webkit-scrollbar-thumb { background: var(--w-bd); border-radius: 4px; }

        .w-msg { display: flex; gap: 8px; align-items: flex-end; max-width: 92%; }
        .w-msg.user { align-self: flex-end; flex-direction: row-reverse; }
        .w-msg.bot  { align-self: flex-start; }

        .w-av {
          width: 26px; height: 26px; border-radius: 7px; flex-shrink: 0;
          display: flex; align-items: center; justify-content: center;
          font-size: .62rem; font-weight: 700; letter-spacing: .02em;
        }
        .bot .w-av  { background: var(--w-header); color: #fff; }
        .user .w-av { background: var(--w-bd); color: var(--w-text); }

        .w-bubble {
          padding: 9px 13px; border-radius: 10px;
          line-height: 1.55; font-size: .835rem; white-space: pre-wrap;
        }
        .bot .w-bubble {
          background: var(--w-bot-bg); border: 1px solid var(--w-bd);
          border-bottom-left-radius: 3px; color: var(--w-text);
        }
        .user .w-bubble {
          background: var(--w-header);
          border-bottom-right-radius: 3px; color: #fff;
        }
        .w-bubble strong { font-weight: 600; }
        .w-bubble a { color: var(--w-blue); text-decoration: underline; }
        .user .w-bubble a { color: #93C5FD; }

        /* timestamp */
        .w-ts {
          font-size: .65rem; color: var(--w-muted);
          align-self: flex-end; margin-bottom: 2px; white-space: nowrap;
        }
        .w-msg.user .w-ts { order: -1; }

        /* typing */
        .w-msg.typing .w-bubble {
          display: flex; align-items: center; gap: 4px; padding: 11px 14px;
        }
        .w-dot {
          width: 5px; height: 5px; border-radius: 50%;
          background: var(--w-muted); animation: bounce .9s infinite;
        }
        .w-dot:nth-child(2) { animation-delay: .15s; }
        .w-dot:nth-child(3) { animation-delay: .30s; }
        @keyframes bounce { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-4px)} }

        /* input bar */
        .w-input-bar {
          display: flex; gap: 8px; padding: 10px 12px;
          border-top: 1px solid var(--w-bd);
          background: var(--w-white); flex-shrink: 0;
        }
        .w-textarea {
          flex: 1; resize: none;
          border: 1px solid var(--w-bd); border-radius: 8px;
          padding: 8px 12px; font-size: .835rem; font-family: inherit;
          outline: none; line-height: 1.45; max-height: 100px; overflow-y: auto;
          transition: border-color .15s, box-shadow .15s;
          background: var(--w-white); color: var(--w-text);
        }
        .w-textarea:focus {
          border-color: var(--w-blue);
          box-shadow: 0 0 0 3px rgba(37,99,235,.10);
        }
        .w-textarea::placeholder { color: #CBD5E1; }

        .w-send {
          width: 36px; height: 36px; border-radius: 8px;
          background: var(--w-header); border: none; cursor: pointer;
          display: flex; align-items: center; justify-content: center;
          flex-shrink: 0; align-self: flex-end;
          transition: background .15s, transform .1s;
        }
        .w-send:hover  { background: #1E293B; }
        .w-send:active { transform: scale(.92); }
        .w-send:disabled { background: var(--w-bd); cursor: not-allowed; }

        /* responsive */
        @media (max-width: 480px) {
          #chat-panel { right: 12px; bottom: 76px; width: calc(100vw - 24px); }
          #chat-bubble { right: 16px; bottom: 16px; }
        }
      </style>

      <div class="w-root">

        <!-- BUBBLE -->
        <button id="chat-bubble" aria-label="Open chat assistant">
          <svg class="icon-open" width="22" height="22" viewBox="0 0 24 24" fill="none"
               stroke="rgba(255,255,255,.9)" stroke-width="1.8"
               stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            <line x1="9" y1="10" x2="15" y2="10"/>
            <line x1="9" y1="13.5" x2="13" y2="13.5"/>
          </svg>
          <svg class="icon-close" width="18" height="18" viewBox="0 0 24 24" fill="none"
               stroke="rgba(255,255,255,.9)" stroke-width="2.2"
               stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
          <span class="badge" id="badge">1</span>
        </button>

        <!-- PANEL -->
        <div id="chat-panel" role="dialog" aria-label="${title}">

          <!-- header -->
          <div class="w-header">
            <div class="w-avatar">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
                   stroke="rgba(255,255,255,.85)" stroke-width="1.8"
                   stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <div class="w-header-info">
              <div class="w-header-name">${title}</div>
              <div class="w-header-sub"><span class="w-status"></span> Online</div>
            </div>
            <div class="w-header-actions">
              <button class="w-icon-btn" id="clearBtn" title="Clear conversation">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="1 4 1 10 7 10"/>
                  <path d="M3.51 15a9 9 0 1 0 .49-4.5"/>
                </svg>
              </button>
              <button class="w-icon-btn" id="closeBtn" title="Close">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- messages -->
          <div class="w-messages" id="messages">
            <div class="w-msg bot">
              <div class="w-av">AI</div>
              <div class="w-bubble" id="welcomeBubble">${welcomeHtml}</div>
            </div>
          </div>

          <!-- input -->
          <div class="w-input-bar">
            <textarea class="w-textarea" id="input" rows="1"
              placeholder="${placeholder}"></textarea>
            <button class="w-send" id="sendBtn" title="Send">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
                   stroke="#fff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <line x1="22" y1="2" x2="11" y2="13"/>
                <polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    `;

    this._apiUrl = `${apiUrl}/api/chat`;
  }

  /* ─── event binding ────────────────────────────────────────── */
  _bind() {
    const s = this._shadow;

    s.getElementById('chat-bubble').addEventListener('click', () => this._toggleChat());
    s.getElementById('closeBtn').addEventListener('click',   () => this._toggleChat(false));
    s.getElementById('clearBtn').addEventListener('click',   () => this._clearChat());
    s.getElementById('sendBtn').addEventListener('click',    () => this._sendMessage());
    s.getElementById('input').addEventListener('keydown',    e  => this._handleKey(e));
    s.getElementById('input').addEventListener('input',      e  => this._autoResize(e.target));
  }

  /* ─── helpers ──────────────────────────────────────────────── */
  _toggleChat(force) {
    const s      = this._shadow;
    this._open   = force !== undefined ? force : !this._open;
    s.getElementById('chat-panel').classList.toggle('open', this._open);
    s.getElementById('chat-bubble').classList.toggle('open', this._open);
    if (this._open) {
      s.getElementById('badge').style.display = 'none';
      s.getElementById('input').focus();
      const msgs = s.getElementById('messages');
      msgs.scrollTop = msgs.scrollHeight;
    }
  }

  _clearChat() {
    const s = this._shadow;
    this._sessionId = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : Math.random().toString(36).slice(2) + Date.now().toString(36);
    sessionStorage.setItem('streamx-chat-session', this._sessionId);
    const cleared = (this.getAttribute('welcome') || StreamxChat.DEFAULT_WELCOME)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/\n/g, '<br>');
    s.getElementById('messages').innerHTML = `
      <div class="w-msg bot">
        <div class="w-av">AI</div>
        <div class="w-bubble" id="welcomeBubble">${cleared}</div>
      </div>`;
  }

  _autoResize(el) {
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 100) + 'px';
  }

  _handleKey(e) {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); this._sendMessage(); }
  }

  _ts() {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  _addMessage(role, text) {
    const msgs = this._shadow.getElementById('messages');
    const wrap = document.createElement('div');
    wrap.className = `w-msg ${role}`;

    const av = document.createElement('div');
    av.className = 'w-av';
    av.textContent = role === 'bot' ? 'AI' : 'You';

    const bub = document.createElement('div');
    bub.className = 'w-bubble';
    bub.innerHTML = this._md(text);

    const time = document.createElement('div');
    time.className = 'w-ts';
    time.textContent = this._ts();

    if (role === 'user') {
      wrap.appendChild(time);
      wrap.appendChild(bub);
      wrap.appendChild(av);
    } else {
      wrap.appendChild(av);
      wrap.appendChild(bub);
    }

    msgs.appendChild(wrap);
    msgs.scrollTop = msgs.scrollHeight;
    return bub;
  }

  _addTyping() {
    const msgs = this._shadow.getElementById('messages');
    const wrap = document.createElement('div');
    wrap.className = 'w-msg bot typing';
    wrap.id = 'typing';
    wrap.innerHTML = `<div class="w-av">AI</div><div class="w-bubble"><div class="w-dot"></div><div class="w-dot"></div><div class="w-dot"></div></div>`;
    msgs.appendChild(wrap);
    msgs.scrollTop = msgs.scrollHeight;
  }

  _removeTyping() {
    this._shadow.getElementById('typing')?.remove();
  }

  _md(text) {
    return text
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>')
      .replace(/\n/g, '<br>');
  }

  async _sendMessage() {
    const s     = this._shadow;
    const input = s.getElementById('input');
    const q     = input.value.trim();
    if (!q || this._streaming) return;

    this._streaming = true;
    s.getElementById('sendBtn').disabled = true;
    input.value = '';
    input.style.height = 'auto';

    this._addMessage('user', q);
    this._addTyping();

    try {
      const resp = await fetch(this._apiUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
        body: JSON.stringify({ question: q, sessionId: this._sessionId })
      });

      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);

      this._removeTyping();
      const botBub = this._addMessage('bot', '');
      let full = '';
      const reader  = resp.body.getReader();
      const decoder = new TextDecoder();
      let buf = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += decoder.decode(value, { stream: true });
        const lines = buf.split('\n');
        buf = lines.pop();
        for (const line of lines) {
          if (line.startsWith('data:')) {
            full += line.slice(5);
            const msgs = s.getElementById('messages');
            botBub.innerHTML = this._md(full) + '<span style="opacity:.35">▍</span>';
            msgs.scrollTop = msgs.scrollHeight;
          }
        }
      }
      botBub.innerHTML = this._md(full);

    } catch (err) {
      this._removeTyping();
      this._addMessage('bot', `⚠️ ${err.message}\n\nMake sure the RAG service is running.`);
    }

    this._streaming = false;
    s.getElementById('sendBtn').disabled = false;
    input.focus();
  }
}

customElements.define('streamx-chat', StreamxChat);
