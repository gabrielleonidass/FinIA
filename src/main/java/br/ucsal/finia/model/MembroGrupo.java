package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Enums.PermissaoMembro;
import br.ucsal.finia.model.Enums.StatusMembro;

import java.util.Map;

/** Membro de um grupo familiar e sua permissão (RF12). */
public record MembroGrupo(Long id, long grupoId, String nome, String email, PermissaoMembro permissao,
                          StatusMembro status) implements Json.JsonConvertivel {
    @Override
    public Map<String, Object> paraJson() {
        return Json.obj("id", id, "nome", nome, "email", email, "permissao", permissao, "status", status);
    }
}
