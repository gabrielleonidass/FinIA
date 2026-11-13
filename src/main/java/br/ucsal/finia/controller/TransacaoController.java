package br.ucsal.finia.controller;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.Json;
import br.ucsal.finia.infra.Requisicao;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.model.Enums.OrigemTransacao;
import br.ucsal.finia.model.Enums.StatusTransacao;
import br.ucsal.finia.model.Enums.TipoTransacao;
import br.ucsal.finia.model.Transacao;
import br.ucsal.finia.repository.ReclassificacaoRepository;
import br.ucsal.finia.repository.RegraRepository;
import br.ucsal.finia.service.ImportacaoService;
import br.ucsal.finia.service.TransacaoService;
import br.ucsal.finia.util.Periodo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Tela de transações: listagem, importação, reclassificação e regras (RF02-RF04). */
public class TransacaoController {

    private final TransacaoService transacoes;
    private final ImportacaoService importacao;
    private final RegraRepository regras;
    private final ReclassificacaoRepository reclassificacoes;

    public TransacaoController(TransacaoService transacoes, ImportacaoService importacao, RegraRepository regras,
                               ReclassificacaoRepository reclassificacoes) {
        this.transacoes = transacoes;
        this.importacao = importacao;
        this.regras = regras;
        this.reclassificacoes = reclassificacoes;
    }

    public void registrar(Roteador r) {
        r.get("/api/transacoes", this::listar);
        r.post("/api/transacoes", this::criar);
        r.post("/api/transacoes/importar", req -> importacao.importar(req.usuario().id(),
                req.longoObrigatorio("contaId"), req.texto("nomeArquivo"), req.texto("conteudo")));
        r.put("/api/transacoes/{id}/categoria", req -> transacoes.reclassificar(req.usuario().id(), req.idRota("id"),
                req.longoObrigatorio("categoriaId")));
        r.post("/api/transacoes/{id}/confirmar", req -> transacoes.confirmar(req.usuario().id(), req.idRota("id")));
        r.get("/api/regras", req -> Json.obj("regras", regras.listar(req.usuario().id()),
                "historico", reclassificacoes.historico(req.usuario().id(), 8)));
        r.post("/api/regras", req -> transacoes.criarRegra(req.usuario().id(), req.texto("padrao"), req.longoObrigatorio("categoriaId")));
        r.delete("/api/regras/{id}", req -> {
            if (regras.remover(req.idRota("id"), req.usuario().id()) == 0) throw ApiException.naoEncontrado("Regra não encontrada.");
            return null;
        });
    }

    private Object listar(Requisicao req) {
        LocalDate ini = null, fim = null;
        String periodo = req.query("periodo");
        if (periodo != null && !periodo.isBlank() && !"todos".equals(periodo)) {
            Periodo p = Periodo.de(periodo, req.query("inicio"), req.query("fim"));
            ini = p.inicio();
            fim = p.fim();
        }
        StatusTransacao status = null;
        if (req.query("status") != null && !req.query("status").isBlank()) status = StatusTransacao.valueOf(req.query("status"));
        Long cat = req.query("categoria") == null || req.query("categoria").isBlank() ? null : Long.valueOf(req.query("categoria"));
        Long conta = req.query("conta") == null || req.query("conta").isBlank() ? null : Long.valueOf(req.query("conta"));
        var lista = transacoes.listar(req.usuario().id(), ini, fim, cat, status, conta, req.query("busca"));
        BigDecimal entradas = lista.stream().filter(t -> !t.despesa() && !"Transferências".equals(t.categoriaNome())).map(Transacao::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saidas = lista.stream().filter(t -> t.despesa() && !"Transferências".equals(t.categoriaNome())).map(Transacao::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        long incertas = lista.stream().filter(t -> t.status() == StatusTransacao.INCERTA).count();
        return Json.obj("transacoes", lista, "total", lista.size(), "entradas", entradas, "saidas", saidas, "incertas", incertas);
    }

    private Object criar(Requisicao req) {
        BigDecimal valor = req.decimalObrigatorio("valor", "valor");
        if (valor.signum() <= 0) throw ApiException.requisicaoInvalida("O valor deve ser positivo.");
        LocalDate data = req.data("data");
        if (data == null) throw ApiException.requisicaoInvalida("Informe a data.");
        TipoTransacao tipo = TipoTransacao.valueOf(req.texto("tipo") == null ? "DESPESA" : req.texto("tipo"));
        Transacao t = transacoes.registrar(req.usuario().id(), req.longoObrigatorio("contaId"), data,
                req.textoObrigatorio("descricao", "descrição"), valor, tipo, OrigemTransacao.MANUAL);
        if (t == null) throw ApiException.conflito("Transação duplicada: já existe lançamento com mesma data, valor e descrição.");
        return t;
    }
}
