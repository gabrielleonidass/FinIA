package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;

import java.util.Map;

/** Regra permanente de categorização criada pelo usuário (RF04). */
public record RegraCategorizacao(Long id, long usuarioId, String padrao, long categoriaId, String categoriaNome)
        implements Json.JsonConvertivel {
    @Override
    public Map<String, Object> paraJson() {
        return Json.obj("id", id, "padrao", padrao, "categoriaId", categoriaId, "categoria", categoriaNome);
    }
}
