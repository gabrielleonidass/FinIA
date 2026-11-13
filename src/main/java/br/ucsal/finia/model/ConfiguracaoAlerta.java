package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Enums.CanalAlerta;
import br.ucsal.finia.model.Enums.TipoAlerta;

import java.util.Map;

/** Preferência do usuário por tipo de alerta e canal (RF08). */
public record ConfiguracaoAlerta(Long id, long usuarioId, TipoAlerta tipo, boolean ativo, CanalAlerta canal)
        implements Json.JsonConvertivel {
    @Override
    public Map<String, Object> paraJson() {
        return Json.obj("tipo", tipo, "rotulo", tipo.rotulo, "ativo", ativo, "canal", canal);
    }
}
