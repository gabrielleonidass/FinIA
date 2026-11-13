package br.ucsal.finia.controller;

import br.ucsal.finia.infra.Arquivo;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.service.RelatorioService;

/** Tela de relatórios e exportação PDF/XLSX (RF11 / UC11). */
public class RelatorioController {

    private final RelatorioService relatorios;

    public RelatorioController(RelatorioService relatorios) {
        this.relatorios = relatorios;
    }

    public void registrar(Roteador r) {
        r.get("/api/relatorios", req -> relatorios.dados(req.usuario().id(), req.query("tipo", "mensal"), req.query("referencia")));
        r.get("/api/relatorios/exportar", req -> {
            String tipo = req.query("tipo", "mensal"), ref = req.query("referencia");
            String nome = "finia-relatorio-" + tipo + "-" + RelatorioService.periodo(tipo, ref).inicio();
            if ("xlsx".equals(req.query("formato"))) {
                return new Arquivo(relatorios.gerarXlsx(req.usuario(), tipo, ref),
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", nome + ".xlsx");
            }
            return new Arquivo(relatorios.gerarPdf(req.usuario(), tipo, ref), "application/pdf", nome + ".pdf");
        });
    }
}
