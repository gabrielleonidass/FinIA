package br.ucsal.finia.controller;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.infra.Requisicao;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.model.Enums.StatusMeta;
import br.ucsal.finia.service.MetaService;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Tela de metas financeiras (RF05 / UC05). */
public class MetaController {

    private final MetaService metas;

    public MetaController(MetaService metas) {
        this.metas = metas;
    }

    public void registrar(Roteador r) {
        r.get("/api/metas", req -> metas.listar(req.usuario().id()));
        r.post("/api/metas", req -> Json.obj("id", metas.criar(req.usuario().id(), req.texto("nome"), req.decimal("valorAlvo"),
                req.decimal("valorAcumulado"), req.data("dataLimite"), req.decimal("taxaRendimentoMensal"))));
        r.put("/api/metas/{id}", req -> {
            metas.editar(req.usuario().id(), req.idRota("id"), req.texto("nome"), req.decimal("valorAlvo"),
                    req.decimal("valorAcumulado"), req.data("dataLimite"), req.decimal("taxaRendimentoMensal"));
            return null;
        });
        r.post("/api/metas/{id}/aporte", req -> {
            metas.aportar(req.usuario().id(), req.idRota("id"), req.decimal("valor"));
            return null;
        });
        r.post("/api/metas/{id}/status", req -> {
            metas.alterarStatus(req.usuario().id(), req.idRota("id"), StatusMeta.valueOf(req.texto("status")));
            return null;
        });
        r.post("/api/metas/simular", this::simularAporte);
    }

    /** Simulação rápida do aporte antes de salvar (critério: R$ 12.000 em 12 meses = R$ 1.000/mês). */
    private Object simularAporte(Requisicao req) {
        BigDecimal alvo = req.decimalObrigatorio("valorAlvo", "valor-alvo");
        BigDecimal acum = req.decimal("valorAcumulado");
        LocalDate data = req.data("dataLimite");
        Integer meses = req.inteiro("meses");
        int n = meses != null ? meses : data != null ? MetaService.mesesRestantes(data) : 12;
        BigDecimal aporte = MetaService.aporteMensal(alvo, acum == null ? BigDecimal.ZERO : acum, n, req.decimal("taxaRendimentoMensal"));
        return Json.obj("meses", n, "aporteMensal", aporte);
    }
}
