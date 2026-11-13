package br.ucsal.finia.controller;

import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.model.Enums.PermissaoMembro;
import br.ucsal.finia.service.FamiliaService;

/** Tela do grupo familiar (RF12 / UC12). */
public class FamiliaController {

    private final FamiliaService familia;

    public FamiliaController(FamiliaService familia) {
        this.familia = familia;
    }

    public void registrar(Roteador r) {
        r.get("/api/familia", req -> familia.consultar(req.usuario().id()));
        r.post("/api/familia", req -> {
            familia.criarGrupo(req.usuario().id(), req.texto("nome"));
            return null;
        });
        r.post("/api/familia/membros", req -> {
            familia.convidar(req.usuario().id(), req.texto("nome"), req.texto("email"),
                    PermissaoMembro.valueOf(req.texto("permissao") == null ? "VISUALIZAR" : req.texto("permissao")));
            return null;
        });
        r.put("/api/familia/membros/{id}", req -> {
            familia.alterarPermissao(req.usuario().id(), req.idRota("id"), PermissaoMembro.valueOf(req.texto("permissao")));
            return null;
        });
        r.post("/api/familia/membros/{id}/aceitar", req -> {
            familia.aceitarConvite(req.usuario().id(), req.idRota("id"));
            return null;
        });
        r.delete("/api/familia/membros/{id}", req -> {
            familia.remover(req.usuario().id(), req.idRota("id"));
            return null;
        });
    }
}
