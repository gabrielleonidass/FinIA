package br.ucsal.finia.service;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.model.Categoria;
import br.ucsal.finia.model.TipoTransacao;
import br.ucsal.finia.model.Transacao;
import br.ucsal.finia.repository.CategoriaRepository;
import br.ucsal.finia.repository.TransacaoRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Regras das transações: cadastrar, listar, trocar categoria e excluir. */
public class TransacaoService {

    private final TransacaoRepository transacoes;
    private final CategoriaRepository categorias;
    private final CategorizadorService categorizador;

    public TransacaoService(TransacaoRepository transacoes, CategoriaRepository categorias,
                            CategorizadorService categorizador) {
        this.transacoes = transacoes;
        this.categorias = categorias;
        this.categorizador = categorizador;
    }

    /**
     * Cadastra uma transação. Se categoriaId vier vazio, o categorizador escolhe
     * a categoria automaticamente.
     */
    public Transacao registrar(long usuarioId, String descricao, BigDecimal valor, TipoTransacao tipo,
                               LocalDate data, Long categoriaId) {
        if (descricao == null || descricao.isBlank()) throw ApiException.requisicaoInvalida("Informe a descrição.");
        if (descricao.trim().length() > 150) throw ApiException.requisicaoInvalida("Descrição muito longa (máx. 150).");
        if (valor == null || valor.signum() <= 0) throw ApiException.requisicaoInvalida("O valor deve ser maior que zero.");
        if (tipo == null) throw ApiException.requisicaoInvalida("Escolha se é receita ou despesa.");
        if (data == null) throw ApiException.requisicaoInvalida("Informe a data.");

        Categoria categoria;
        boolean automatica;
        if (categoriaId == null) {
            categoria = categorizador.sugerir(usuarioId, descricao, tipo).categoria();
            automatica = true;
        } else {
            categoria = categoriaValida(categoriaId, tipo);
            automatica = false;
        }

        long id = transacoes.inserir(usuarioId, descricao.trim(), valor, tipo, data, categoria.id(), automatica);
        return transacoes.buscar(usuarioId, id).orElseThrow();
    }

    public List<Transacao> listarDoMes(long usuarioId, YearMonth mes) {
        return transacoes.listarDoMes(usuarioId, mes);
    }

    /** O usuário corrige a categoria. A correção é usada pelo categorizador nas próximas vezes. */
    public Transacao trocarCategoria(long usuarioId, long transacaoId, long categoriaId) {
        Transacao t = transacoes.buscar(usuarioId, transacaoId)
                .orElseThrow(() -> ApiException.naoEncontrado("Transação não encontrada."));
        categoriaValida(categoriaId, t.tipo());
        transacoes.alterarCategoria(usuarioId, transacaoId, categoriaId);
        return transacoes.buscar(usuarioId, transacaoId).orElseThrow();
    }

    public void excluir(long usuarioId, long transacaoId) {
        if (transacoes.excluir(usuarioId, transacaoId) == 0) throw ApiException.naoEncontrado("Transação não encontrada.");
    }

    private Categoria categoriaValida(long categoriaId, TipoTransacao tipo) {
        Categoria c = categorias.buscarPorId(categoriaId)
                .orElseThrow(() -> ApiException.requisicaoInvalida("Categoria inválida."));
        if (c.tipo() != tipo) {
            throw ApiException.requisicaoInvalida("A categoria " + c.nome() + " não serve para "
                    + (tipo == TipoTransacao.RECEITA ? "receitas." : "despesas."));
        }
        return c;
    }
}
