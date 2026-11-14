package br.ucsal.finia.controller;

import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.service.InicioService;

/** Tela Início (resumo do mês). */
public class InicioController {

    private final InicioService inicio;

    public InicioController(InicioService inicio) {
        this.inicio = inicio;
    }

    public void registrar(Roteador r) {
        r.get("/api/inicio", req -> inicio.resumo(req.usuario().id(), Meses.ler(req.query("mes"))));
    }
}
