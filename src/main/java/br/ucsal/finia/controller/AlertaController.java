package br.ucsal.finia.controller;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.model.Enums.CanalAlerta;
import br.ucsal.finia.model.Enums.TipoAlerta;
import br.ucsal.finia.repository.AlertaRepository;

/** Tela de alertas e preferências de notificação (RF08 / UC08). */
public class AlertaController {

    private final AlertaRepository alertas;

    public AlertaController(AlertaRepository alertas) {
        this.alertas = alertas;
    }

    public void registrar(Roteador r) {
        r.get("/api/alertas", req -> Json.obj("alertas", alertas.listar(req.usuario().id(), 100),
                "naoLidos", alertas.contarNaoLidos(req.usuario().id()),
                "configuracoes", alertas.listarConfiguracoes(req.usuario().id())));
        r.post("/api/alertas/{id}/lido", req -> {
            alertas.marcarLido(req.idRota("id"), req.usuario().id());
            return null;
        });
        r.post("/api/alertas/lidos", req -> {
            alertas.marcarTodosLidos(req.usuario().id());
            return null;
        });
        r.put("/api/alertas/configuracoes", req -> {
            alertas.salvarConfiguracao(req.usuario().id(), TipoAlerta.valueOf(req.texto("tipo")), req.booleano("ativo"),
                    CanalAlerta.valueOf(req.texto("canal")));
            return null;
        });
    }
}
