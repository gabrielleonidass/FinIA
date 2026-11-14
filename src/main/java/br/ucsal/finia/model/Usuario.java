package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;

import java.util.Map;

/** Pessoa que usa o sistema. A senha fica guardada como hash (nunca em texto puro). */
public record Usuario(Long id, String nome, String email, String senhaHash, String senhaSalt)
        implements Json.JsonConvertivel {

    /** O que vai para o front-end (sem a senha). */
    @Override
    public Map<String, Object> paraJson() {
        return Json.obj("id", id, "nome", nome, "email", email);
    }
}
