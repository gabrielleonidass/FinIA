package br.ucsal.finia.controller;

import br.ucsal.finia.infra.Requisicao;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.repository.CategoriaRepository;
import br.ucsal.finia.service.DashboardService;
import br.ucsal.finia.util.Periodo;

/** Tela de dashboard (RF07 / UC07). */
public class DashboardController {

    private final DashboardService dashboard;
    private final CategoriaRepository categorias;

    public DashboardController(DashboardService dashboard, CategoriaRepository categorias) {
        this.dashboard = dashboard;
        this.categorias = categorias;
    }

    public void registrar(Roteador r) {
        r.get("/api/dashboard", req -> dashboard.resumo(req.usuario().id(),
                Periodo.de(req.query("periodo"), req.query("inicio"), req.query("fim"))));
        r.get("/api/categorias", (Requisicao req) -> categorias.listarTodas());
    }
}
