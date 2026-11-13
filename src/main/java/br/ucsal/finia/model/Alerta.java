package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Enums.TipoAlerta;

import java.time.LocalDateTime;
import java.util.Map;

/** Notificação emitida ao usuário (RF08). */
public record Alerta(Long id, long usuarioId, TipoAlerta tipo, String mensagem, boolean lido,
                     LocalDateTime criadoEm) implements Json.JsonConvertivel {
    @Override
    public Map<String, Object> paraJson() {
        return Json.obj("id", id, "tipo", tipo, "tipoRotulo", tipo.rotulo, "mensagem", mensagem,
                "lido", lido, "criadoEm", criadoEm.withNano(0));
    }
}
