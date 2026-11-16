// ============================================================
// FinIA - funções usadas por todas as telas
// ============================================================

/** Chama a API do back-end. Se a sessão expirou, volta para o login. */
async function api(url, metodo = 'GET', dados = null) {
  const opcoes = { method: metodo };
  if (dados) {
    // Envia como formulário (campo=valor&campo=valor), sem os campos vazios
    const campos = Object.entries(dados).filter(([, valor]) => valor !== null && valor !== undefined);
    opcoes.body = new URLSearchParams(campos);
  }
  const resposta = await fetch(url, opcoes);
  const corpo = await resposta.json().catch(() => ({}));
  const telaDeEntrada = url === '/api/auth/login' || url === '/api/auth/cadastro';
  if (resposta.status === 401 && !telaDeEntrada) {
    location.href = '/';            // não está logado: volta para o login
    return new Promise(() => {});    // e não continua o resto da tela
  }
  if (!resposta.ok) throw new Error(corpo.erro || 'Algo deu errado. Tente de novo.');
  return corpo;
}

/** 1234.5 -> "R$ 1.234,50" */
function moeda(valor) {
  return Number(valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

/** "2026-11-14" -> "14/11/2026" */
function dataBR(texto) {
  return texto ? texto.split('-').reverse().join('/') : '';
}

/** "2026-11" -> "novembro de 2026" */
function nomeDoMes(anoMes) {
  const [ano, mes] = anoMes.split('-').map(Number);
  return new Date(ano, mes - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
}

/** Mês atual no formato "AAAA-MM". */
function mesAtual() {
  const d = new Date();
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0');
}

/** Data de hoje no formato "AAAA-MM-DD". */
function hoje() {
  const d = new Date();
  return mesAtual() + '-' + String(d.getDate()).padStart(2, '0');
}

/** Evita que textos digitados virem HTML. */
function esc(texto) {
  return String(texto ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** Pega os campos de um formulário como objeto { nome: valor }. */
function dadosDoForm(form) {
  return Object.fromEntries(new FormData(form).entries());
}

/** Monta o menu lateral e confere se a pessoa está logada. */
async function montarTopo(paginaAtual) {
  // Telas com back-end completo (salvam no banco) e telas de protótipo (só front-end)
  const grupos = [
    { titulo: 'Principal', paginas: [
      { id: 'inicio', nome: 'Início', icone: '🏠' },
      { id: 'transacoes', nome: 'Transações', icone: '💸' },
      { id: 'importar', nome: 'Importar extrato', icone: '📥' },
      { id: 'contas', nome: 'Contas e cartões', icone: '💳' },
    ]},
    { titulo: 'Planejamento', paginas: [
      { id: 'orcamentos', nome: 'Orçamentos', icone: '📊' },
      { id: 'metas', nome: 'Metas', icone: '🎯' },
      { id: 'simulacao', nome: 'Simulação "E se"', icone: '🧮' },
    ]},
    { titulo: 'Acompanhamento', paginas: [
      { id: 'alertas', nome: 'Alertas', icone: '🔔' },
      { id: 'relatorios', nome: 'Relatórios', icone: '📄' },
      { id: 'familia', nome: 'Grupo familiar', icone: '👨‍👩‍👧' },
    ]},
  ];
  const menu = grupos.map(g => `
    <p class="menu-grupo">${g.titulo}</p>
    ${g.paginas.map(p => `<a href="/${p.id}" class="${p.id === paginaAtual ? 'ativo' : ''}">
      <span aria-hidden="true">${p.icone}</span>${esc(p.nome)}</a>`).join('')}`).join('');

  document.body.classList.add('com-menu');
  document.body.insertAdjacentHTML('afterbegin', `
    <aside class="lateral">
      <a class="logo" href="/inicio">Fin<span>IA</span></a>
      <nav class="menu" aria-label="Menu principal">${menu}</nav>
      <div class="usuario"><span id="nome-usuario"></span><button id="sair">Sair</button></div>
    </aside>`);

  document.getElementById('sair').onclick = async () => {
    await api('/api/auth/sair', 'POST');
    location.href = '/';
  };

  const { usuario } = await api('/api/auth/eu');
  document.getElementById('nome-usuario').textContent = usuario.nome;
  return usuario;
}

/** Faixa amarela que identifica as telas de protótipo (só front-end). */
function avisoPrototipo(requisito, texto) {
  const main = document.querySelector('main');
  main.insertAdjacentHTML('afterbegin', `
    <div class="prototipo" role="note"><strong>Protótipo de interface (${requisito})</strong> ${texto}</div>`);
}

/** Abre uma janela simples (modal) com título, conteúdo HTML e botão de fechar. */
function abrirModal(titulo, html) {
  const fundo = document.createElement('div');
  fundo.className = 'modal-fundo';
  fundo.innerHTML = `<div class="modal" role="dialog" aria-modal="true" aria-label="${esc(titulo)}">
      <h2>${esc(titulo)}</h2>${html}
      <div style="text-align:right;margin-top:16px"><button class="botao botao-claro" data-fechar>Fechar</button></div>
    </div>`;
  fundo.addEventListener('click', e => { if (e.target === fundo || e.target.dataset.fechar !== undefined) fundo.remove(); });
  document.body.appendChild(fundo);
  return fundo;
}

/** Mensagem rápida no canto da tela. */
function aviso(texto) {
  const t = document.createElement('div');
  t.className = 'toast';
  t.textContent = texto;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 3200);
}
