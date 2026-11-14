// ============================================================
// FinIA - funções usadas por todas as telas
// ============================================================

/** Chama a API do back-end. Se a sessão expirou, volta para o login. */
async function api(url, metodo = 'GET', dados = null) {
  const opcoes = { method: metodo, headers: {} };
  if (dados) {
    opcoes.headers['Content-Type'] = 'application/json';
    opcoes.body = JSON.stringify(dados);
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

/** Monta o topo com o menu e confere se a pessoa está logada. */
async function montarTopo(paginaAtual) {
  const paginas = [
    { id: 'inicio', nome: 'Início' },
    { id: 'transacoes', nome: 'Transações' },
    { id: 'metas', nome: 'Metas' },
  ];
  const links = paginas.map(p =>
    `<a href="/${p.id}" class="${p.id === paginaAtual ? 'ativo' : ''}">${p.nome}</a>`).join('');

  document.body.insertAdjacentHTML('afterbegin', `
    <header class="topo">
      <div class="topo-conteudo">
        <a class="logo" href="/inicio">Fin<span>IA</span></a>
        <nav class="menu" aria-label="Menu principal">${links}</nav>
        <div class="usuario"><span id="nome-usuario"></span><button id="sair">Sair</button></div>
      </div>
    </header>`);

  document.getElementById('sair').onclick = async () => {
    await api('/api/auth/sair', 'POST');
    location.href = '/';
  };

  const { usuario } = await api('/api/auth/eu');
  document.getElementById('nome-usuario').textContent = 'Olá, ' + usuario.nome.split(' ')[0];
  return usuario;
}
