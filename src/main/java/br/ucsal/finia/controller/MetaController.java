package br.ucsal.finia.controller;

import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.service.MetaService;

/** Tela Metas. */
public class MetaController {

    private final MetaService metas;

    public MetaController(MetaService metas) {
        this.metas = metas;
    }

    public void registrar(Roteador r) {
        r.get("/api/metas", req -> metas.listar(req.usuario().id()));
        r.post("/api/metas", req -> metas.criar(req.usuario().id(), req.texto("nome"),
                req.decimal("valorAlvo"), req.data("prazo")));
        r.post("/api/metas/{id}/guardar", req ->
                metas.guardar(req.usuario().id(), req.idRota("id"), req.decimal("valor")));
        r.delete("/api/metas/{id}", req -> {
            metas.excluir(req.usuario().id(), req.idRota("id"));
            return null;
        });
    }
}
