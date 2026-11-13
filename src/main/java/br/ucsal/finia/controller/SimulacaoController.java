package br.ucsal.finia.controller;

import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.service.SimulacaoService;

/** Tela de simulação de cenários "E se" (RF06 / UC06). */
public class SimulacaoController {

    private final SimulacaoService simulacao;

    public SimulacaoController(SimulacaoService simulacao) {
        this.simulacao = simulacao;
    }

    public void registrar(Roteador r) {
        r.post("/api/simulacoes", req -> simulacao.simular(req.usuario().id(), req.longoObrigatorio("categoriaId"),
                req.inteiro("percentual") == null ? 0 : req.inteiro("percentual"), req.decimal("rendaExtra")));
    }
}
