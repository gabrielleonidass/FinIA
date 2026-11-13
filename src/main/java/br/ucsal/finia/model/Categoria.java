package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;

import java.util.Map;

/** Categoria pré-definida de transação (RF03). */
public record Categoria(Long id, String nome, String cor, boolean receita) implements Json.JsonConvertivel {
    @Override
    public Map<String, Object> paraJson() {
        return Json.obj("id", id, "nome", nome, "cor", cor, "receita", receita);
    }
}
