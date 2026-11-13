package br.ucsal.finia.service;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.*;
import br.ucsal.finia.model.Enums.OrigemTransacao;
import br.ucsal.finia.model.Enums.StatusTransacao;
import br.ucsal.finia.model.Enums.TipoTransacao;
import br.ucsal.finia.repository.*;
import br.ucsal.finia.util.Texto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Registro, categorização e reclassificação de transações (RF02, RF03, RF04). */
public class TransacaoService {

    /** Número de correções seguidas iguais que gera a sugestão de regra (RN05). */
    public static final int CORRECOES_PARA_REGRA = 3;

    private final TransacaoRepository transacoes;
    private final ContaRepository contas;
    private final CategoriaRepository categorias;
    private final ReclassificacaoRepository reclassificacoes;
    private final RegraRepository regras;
    private final CategorizadorService categorizador;
    private final AlertaService alertas;
    private final OrcamentoService orcamentos;

    public TransacaoService(TransacaoRepository transacoes, ContaRepository contas, CategoriaRepository categorias,
                            ReclassificacaoRepository reclassificacoes, RegraRepository regras,
                            CategorizadorService categorizador, AlertaService alertas, OrcamentoService orcamentos) {
        this.transacoes = transacoes;
        this.contas = contas;
        this.categorias = categorias;
        this.reclassificacoes = reclassificacoes;
        this.regras = regras;
        this.categorizador = categorizador;
        this.alertas = alertas;
        this.orcamentos = orcamentos;
    }

    /**
     * Registra uma transação: categoriza ANTES de exibir (RF03), verifica gasto
     * atípico e orçamento (RF08/RF09). Devolve null se for duplicada (RN03).
     */
    public Transacao registrar(long usuarioId, long contaId, LocalDate data, String descricao, BigDecimal valor,
                               TipoTransacao tipo, OrigemTransacao origem) {
        ContaFinanceira conta = contas.buscar(contaId, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Conta não encontrada."));
        if (!conta.ativa()) throw ApiException.requisicaoInvalida("A conta \"" + conta.apelido() + "\" está desativada e não recebe novas transações.");
        BigDecimal v = valor.abs().setScale(2, RoundingMode.HALF_UP);
        if (transacoes.existeDuplicada(usuarioId, data, v, descricao)) return null;

        CategorizadorService.Resultado r = categorizador.classificar(usuarioId, descricao, tipo);
        long id = transacoes.inserir(new Transacao(null, usuarioId, contaId, null, data, descricao, v, tipo,
                r.categoria().id(), null, null, r.confianca(), r.status(), origem));
        Transacao t = transacoes.buscar(id, usuarioId).orElseThrow();
        alertas.verificarGastoAtipico(t);
        if (t.despesa()) orcamentos.verificar(usuarioId, t.categoriaId(), YearMonth.from(data));
        return t;
    }

    public List<Transacao> listar(long usuarioId, LocalDate ini, LocalDate fim, Long categoriaId,
                                  StatusTransacao status, Long contaId, String busca) {
        return transacoes.listar(usuarioId, ini, fim, categoriaId, status, contaId, busca);
    }

    /**
     * Reclassificação manual (RF04): grava auditoria e, após 3 correções seguidas
     * do mesmo estabelecimento para a mesma categoria, sugere uma regra permanente.
     */
    public Map<String, Object> reclassificar(long usuarioId, long transacaoId, long novaCategoriaId) {
        Transacao t = transacoes.buscar(transacaoId, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Transação não encontrada."));
        Categoria nova = categorias.buscarPorId(novaCategoriaId)
                .orElseThrow(() -> ApiException.requisicaoInvalida("Categoria inválida."));
        String padrao = Texto.padraoEstabelecimento(t.descricao());

        reclassificacoes.inserir(t.id(), usuarioId, t.categoriaId(), nova.id(), padrao);
        transacoes.atualizarCategoria(t.id(), nova.id(), 1.0, StatusTransacao.RECLASSIFICADA);
        if (t.despesa()) {
            orcamentos.verificar(usuarioId, nova.id(), YearMonth.from(t.data()));
        }

        boolean sugerir = false;
        if (regras.buscarPorPadrao(usuarioId, padrao).isEmpty()) {
            List<Long> ultimas = reclassificacoes.ultimasCategoriasDoPadrao(usuarioId, padrao, CORRECOES_PARA_REGRA);
            sugerir = ultimas.size() == CORRECOES_PARA_REGRA && ultimas.stream().allMatch(c -> c == nova.id());
        }
        return Json.obj("transacao", transacoes.buscar(t.id(), usuarioId).orElseThrow(),
                "sugerirRegra", sugerir, "padrao", padrao, "categoriaId", nova.id(), "categoria", nova.nome());
    }

    /** Cria regra permanente e aplica às transações ainda não revisadas do mesmo padrão. */
    public Map<String, Object> criarRegra(long usuarioId, String padrao, long categoriaId) {
        String p = Texto.normalizar(padrao);
        if (p.length() < 2) throw ApiException.requisicaoInvalida("Informe o padrão do estabelecimento.");
        Categoria c = categorias.buscarPorId(categoriaId).orElseThrow(() -> ApiException.requisicaoInvalida("Categoria inválida."));
        regras.salvar(usuarioId, p, c.id());
        int aplicadas = 0;
        for (Transacao t : transacoes.listar(usuarioId, null, null, null, null, null, null)) {
            boolean mesmoPadrao = Texto.padraoEstabelecimento(t.descricao()).equals(p);
            boolean pendente = t.status() == StatusTransacao.INCERTA || !Long.valueOf(c.id()).equals(t.categoriaId());
            if (mesmoPadrao && t.status() != StatusTransacao.RECLASSIFICADA && pendente) {
                transacoes.atualizarCategoria(t.id(), c.id(), 0.99, StatusTransacao.CATEGORIZADA);
                aplicadas++;
            }
        }
        return Json.obj("padrao", p, "categoria", c.nome(), "transacoesAtualizadas", aplicadas);
    }

    /** Confirma a categoria sugerida de uma transação incerta. */
    public Map<String, Object> confirmar(long usuarioId, long transacaoId) {
        Transacao t = transacoes.buscar(transacaoId, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Transação não encontrada."));
        return reclassificar(usuarioId, t.id(), t.categoriaId());
    }
}
