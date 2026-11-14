package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;

import java.util.Map;

/** Categoria de uma transação (ex.: Alimentação, Transporte, Salário). */
public record Categoria(Long id, String nome, TipoTransacao tipo, String cor) implements Json.JsonConvertivel {

    @Override
    public Map<String, Object> paraJson() {
        return Json.obj("id", id, "nome", nome, "tipo", tipo, "cor", cor);
    }
}
