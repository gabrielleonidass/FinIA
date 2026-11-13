/* =========================================================
   FinIA - funções compartilhadas do front-end
   (layout, chamadas à API, formatação, modais e avisos)
   ========================================================= */
const FinIA = (() => {
  const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
  const MESES_LONGOS = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

  const icones = {
    painel: '<path d="M3 13h8V3H3zM13 21h8V11h-8zM3 21h8v-6H3zM13 3v6h8V3z"/>',
    transacoes: '<path d="M7 7h13l-3-3M17 17H4l3 3"/>',
    contas: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20M6 15h4"/>',
    metas: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.5"/>',
    orcamentos: '<path d="M21 12a9 9 0 1 1-9-9v9z"/><path d="M15 3.5A9 9 0 0 1 20.5 9H15z"/>',
    simulacao: '<path d="M4 19V5M4 19h16M8 15l4-4 3 3 5-6"/>',
    relatorios: '<path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><path d="M14 3v6h6M8 13h8M8 17h5"/>',
    alertas: '<path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
    familia: '<circle cx="9" cy="7" r="4"/><path d="M3 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2M16 3.13a4 4 0 0 1 0 7.75M21 21v-2a4 4 0 0 0-3-3.85"/>',
    sair: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
    mais: '<path d="M12 5v14M5 12h14"/>',
    upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12"/>',
    baixar: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>',
    info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>',
    check: '<path d="M20 6 9 17l-5-5"/>',
    alerta: '<path d="m10.29 3.86-8.47 14.14A2 2 0 0 0 3.53 21h16.94a2 2 0 0 0 1.71-3l-8.47-14.14a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01"/>',
    raio: '<path d="M13 2 3 14h9l-1 8 10-12h-9z"/>',
    menu: '<path d="M3 6h18M3 12h18M3 18h18"/>',
    banco: '<path d="M3 21h18M5 21V10M19 21V10M9 21V10M15 21V10M12 3l9 5H3z"/>',
    cartao: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/>',
    porco: '<path d="M19 9c1 0 2 1 2 2.5S20 14 19 14l-1 3h-3v-2H9v2H6l-1-3c-2-1-3-3-3-5a6 6 0 0 1 6-6h5a6 6 0 0 1 6 5z"/><circle cx="15.5" cy="9.5" r=".5"/>',
    lapis: '<path d="M12 20h9M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z"/>',
    lixo: '<path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"/>',
  };

  function icone(nome, extra = '') {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" ${extra}>${icones[nome] || ''}</svg>`;
  }

  const menu = [
    { id: 'dashboard', href: '/dashboard', icone: 'painel', rotulo: 'Visão geral' },
    { id: 'transacoes', href: '/transacoes', icone: 'transacoes', rotulo: 'Transações' },
    { id: 'contas', href: '/contas', icone: 'contas', rotulo: 'Contas e cartões' },
    { id: 'metas', href: '/metas', icone: 'metas', rotulo: 'Metas' },
    { id: 'orcamentos', href: '/orcamentos', icone: 'orcamentos', rotulo: 'Orçamentos' },
    { id: 'simulacao', href: '/simulacao', icone: 'simulacao', rotulo: 'Simulação "E se"' },
    { id: 'relatorios', href: '/relatorios', icone: 'relatorios', rotulo: 'Relatórios' },
    { id: 'alertas', href: '/alertas', icone: 'alertas', rotulo: 'Alertas' },
    { id: 'familia', href: '/familia', icone: 'familia', rotulo: 'Grupo familiar' },
  ];

  // ------------------------------------------------------------- formatação
  const moeda = (v) => Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const data = (s) => (s ? String(s).substring(0, 10).split('-').reverse().join('/') : '-');
  const dataHora = (s) => (s ? data(s) + ' ' + String(s).substring(11, 16) : '-');
  const mesCurto = (ym) => { const [a, m] = ym.split('-'); return MESES[m - 1] + '/' + a.substring(2); };
  const mesLongo = (ym) => { const [a, m] = ym.split('-'); return MESES_LONGOS[m - 1] + ' de ' + a; };
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const mesAtual = () => new Date().toISOString().substring(0, 7);

  // ------------------------------------------------------------- API
  async function api(url, opcoes = {}) {
    const cfg = { method: opcoes.method || 'GET', headers: { 'Content-Type': 'application/json' }, credentials: 'same-origin' };
    if (opcoes.body !== undefined) cfg.body = JSON.stringify(opcoes.body);
    const r = await fetch(url, cfg);
    if (r.status === 401 && !opcoes.publico) {
      location.href = '/?sessao=expirada';
      throw new Error('Sessão expirada');
    }
    const tipo = r.headers.get('content-type') || '';
    const corpo = tipo.includes('json') ? await r.json() : null;
    if (!r.ok) throw new Error((corpo && corpo.erro) || 'Erro ' + r.status);
    return corpo;
  }
  const get = (url) => api(url);
  const post = (url, body) => api(url, { method: 'POST', body: body || {} });
  const put = (url, body) => api(url, { method: 'PUT', body: body || {} });
  const del = (url) => api(url, { method: 'DELETE' });

  // ------------------------------------------------------------- avisos
  function toast(msg, erro = false) {
    let area = document.querySelector('.toast-area');
    if (!area) {
      area = document.createElement('div');
      area.className = 'toast-area';
      area.setAttribute('role', 'status');
      area.setAttribute('aria-live', 'polite');
      document.body.appendChild(area);
    }
    const t = document.createElement('div');
    t.className = 'toast' + (erro ? ' erro' : '');
    t.textContent = msg;
    area.appendChild(t);
    setTimeout(() => t.remove(), 4500);
  }

  function abrirModal(id) {
    const d = document.getElementById(id);
    d.showModal();
    const primeiro = d.querySelector('input, select, textarea');
    if (primeiro) primeiro.focus();
  }

  function fecharModal(id) {
    document.getElementById(id).close();
  }

  document.addEventListener('click', (e) => {
    const f = e.target.closest('[data-fechar]');
    if (f) f.closest('dialog').close();
  });

  /** Lê os campos de um formulário como objeto. */
  function dadosForm(form) {
    const o = {};
    new FormData(form).forEach((v, k) => { o[k] = typeof v === 'string' ? v.trim() : v; });
    form.querySelectorAll('input[type=checkbox]').forEach((c) => { o[c.name] = c.checked; });
    return o;
  }

  // ------------------------------------------------------------- layout
  async function iniciar(paginaId, titulo, subtitulo) {
    const ativo = menu.find((m) => m.id === paginaId);
    const sidebar = document.getElementById('sidebar');
    sidebar.className = 'sidebar';
    sidebar.setAttribute('aria-label', 'Menu principal');
    sidebar.innerHTML = `
      <a class="marca" href="/dashboard"><span class="marca-logo">Fi</span><span><strong>FinIA</strong><small>finanças com inteligência</small></span></a>
      <nav><ul class="menu">${menu.map((m) => `
        <li><a href="${m.href}" ${m.id === paginaId ? 'aria-current="page"' : ''}>${icone(m.icone)}<span>${m.rotulo}</span>
        ${m.id === 'alertas' ? '<span class="badge b-cont" id="menu-alertas" hidden></span>' : ''}</a></li>`).join('')}
        <li><a href="#" id="sair">${icone('sair')}<span>Sair</span></a></li>
      </ul></nav>
      <div class="sidebar-rodape">FinIA v1.0 · UCSAL 2026<br>Somente leitura nas instituições financeiras</div>`;

    const topo = document.getElementById('topo');
    topo.className = 'topo';
    topo.innerHTML = `
      <button class="btn-menu" id="btn-menu" aria-label="Abrir menu">${icone('menu', 'width="20" height="20"')}</button>
      <div><h1>${esc(titulo || (ativo && ativo.rotulo))}</h1>${subtitulo ? `<div class="subtitulo">${esc(subtitulo)}</div>` : ''}</div>
      <div class="topo-acoes">
        <a class="sino" href="/alertas" aria-label="Alertas">${icone('alertas')}<span class="contador" id="topo-alertas" hidden></span></a>
        <div class="usuario"><div class="avatar" id="avatar">..</div>
          <div class="usuario-info"><div class="usuario-nome" id="usuario-nome"></div><div class="usuario-email" id="usuario-email"></div></div></div>
      </div>`;
    document.title = (titulo || (ativo && ativo.rotulo)) + ' · FinIA';
    document.getElementById('btn-menu').onclick = () => sidebar.classList.toggle('aberta');
    document.getElementById('sair').onclick = async (e) => {
      e.preventDefault();
      await post('/api/auth/logout');
      location.href = '/';
    };

    const eu = await get('/api/auth/eu');
    document.getElementById('avatar').textContent = eu.usuario.iniciais;
    document.getElementById('usuario-nome').textContent = eu.usuario.nome;
    document.getElementById('usuario-email').textContent = eu.usuario.email;
    atualizarContadorAlertas(eu.alertasNaoLidos);
    return eu.usuario;
  }

  function atualizarContadorAlertas(n) {
    ['topo-alertas', 'menu-alertas'].forEach((id) => {
      const el = document.getElementById(id);
      if (!el) return;
      el.hidden = !n;
      el.textContent = n;
    });
  }

  async function categorias() {
    if (!FinIA._cats) FinIA._cats = await get('/api/categorias');
    return FinIA._cats;
  }

  function opcoesCategorias(lista, selecionada, somenteDespesa = false) {
    return lista.filter((c) => !somenteDespesa || !c.receita)
      .map((c) => `<option value="${c.id}" ${c.id === selecionada ? 'selected' : ''}>${esc(c.nome)}</option>`).join('');
  }

  const rotuloStatusMeta = { ATIVA: ['Ativa', 'b-ok'], EM_RISCO: ['Em risco', 'b-perigo'], PAUSADA: ['Pausada', 'b-neutro'], CONCLUIDA: ['Concluída', 'b-info'], CANCELADA: ['Cancelada', 'b-neutro'] };
  const rotuloSituacao = { DENTRO_DO_LIMITE: ['Dentro do limite', 'b-ok', ''], EM_ALERTA: ['Em alerta', 'b-alerta', 'alerta'], ESTOURADO: ['Estourado', 'b-perigo', 'perigo'] };
  const corAlerta = { GASTO_ATIPICO: ['#fee2e2', '#b91c1c', 'raio'], ORCAMENTO_EM_ALERTA: ['#fef3c7', '#b45309', 'orcamentos'], ORCAMENTO_ESTOURADO: ['#fee2e2', '#b91c1c', 'alerta'], VENCIMENTO_FATURA: ['#dbeafe', '#1d4ed8', 'cartao'], META_EM_RISCO: ['#fef3c7', '#b45309', 'metas'] };

  return {
    icone, moeda, data, dataHora, mesCurto, mesLongo, esc, mesAtual, api, get, post, put, del, toast, abrirModal, fecharModal,
    dadosForm, iniciar, atualizarContadorAlertas, categorias, opcoesCategorias, rotuloStatusMeta, rotuloSituacao, corAlerta,
  };
})();
