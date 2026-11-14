package br.ucsal.finia.controller;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.Json;
import br.ucsal.finia.infra.Requisicao;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.model.TipoTransacao;
import br.ucsal.finia.repository.CategoriaRepository;
import br.ucsal.finia.service.CategorizadorService;
import br.ucsal.finia.service.TransacaoService;

/** Tela Transações. */
public class TransacaoController {

    private final TransacaoService transacoes;
    private final CategorizadorService categorizador;
    private final CategoriaRepository categorias;

    public TransacaoController(TransacaoService transacoes, CategorizadorService categorizador,
                               CategoriaRepository categorias) {
        this.transacoes = transacoes;
        this.categorizador = categorizador;
        this.categorias = categorias;
    }

    public void registrar(Roteador r) {
        r.get("/api/categorias", req -> categorias.listar());
        r.get("/api/transacoes", req -> transacoes.listarDoMes(req.usuario().id(), Meses.ler(req.query("mes"))));
        r.get("/api/transacoes/sugestao", this::sugerir);
        r.post("/api/transacoes", this::cadastrar);
        r.put("/api/transacoes/{id}/categoria", req ->
                transacoes.trocarCategoria(req.usuario().id(), req.idRota("id"), req.longoObrigatorio("categoriaId")));
        r.delete("/api/transacoes/{id}", req -> {
            transacoes.excluir(req.usuario().id(), req.idRota("id"));
            return null;
        });
    }

    private Object cadastrar(Requisicao req) {
        return transacoes.registrar(req.usuario().id(), req.texto("descricao"),
                req.decimal("valor"), tipo(req.texto("tipo")), req.data("data"), req.longo("categoriaId"));
    }

    /** Mostra, enquanto a pessoa digita, qual categoria a IA vai escolher. */
    private Object sugerir(Requisicao req) {
        String descricao = req.query("descricao", "");
        if (descricao.isBlank()) return Json.obj("categoria", null, "motivo", "");
        CategorizadorService.Sugestao s =
                categorizador.sugerir(req.usuario().id(), descricao, tipo(req.query("tipo", "DESPESA")));
        return Json.obj("categoria", s.categoria(), "motivo", s.motivo());
    }

    private static TipoTransacao tipo(String valor) {
        try {
            return valor == null ? null : TipoTransacao.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.requisicaoInvalida("Tipo inválido (use RECEITA ou DESPESA).");
        }
    }
}
