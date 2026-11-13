package br.ucsal.finia.controller;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.Json;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.repository.OrcamentoRepository;
import br.ucsal.finia.service.OrcamentoService;

import java.time.YearMonth;

/** Tela de orçamentos por categoria (RF09 / UC09). */
public class OrcamentoController {

    private final OrcamentoService orcamentos;
    private final OrcamentoRepository repo;

    public OrcamentoController(OrcamentoService orcamentos, OrcamentoRepository repo) {
        this.orcamentos = orcamentos;
        this.repo = repo;
    }

    private static YearMonth mes(String m) {
        try {
            return m == null || m.isBlank() ? YearMonth.now() : YearMonth.parse(m);
        } catch (RuntimeException e) {
            throw ApiException.requisicaoInvalida("Mês inválido (use AAAA-MM).");
        }
    }

    public void registrar(Roteador r) {
        r.get("/api/orcamentos", req -> {
            YearMonth m = mes(req.query("mes"));
            return Json.obj("mes", m.toString(), "itens", orcamentos.acompanhamento(req.usuario().id(), m));
        });
        r.post("/api/orcamentos", req -> {
            orcamentos.definir(req.usuario().id(), req.longoObrigatorio("categoriaId"), mes(req.texto("mes")), req.decimal("limite"));
            return null;
        });
        r.delete("/api/orcamentos/{id}", req -> {
            if (repo.remover(req.idRota("id"), req.usuario().id()) == 0) throw ApiException.naoEncontrado("Orçamento não encontrado.");
            return null;
        });
    }
}
