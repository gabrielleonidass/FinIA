package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Enums.StatusUsuario;

import java.time.LocalDateTime;
import java.util.Map;

/** Usuário do sistema (RF01). A senha é guardada como hash PBKDF2 + salt. */
public record Usuario(Long id, String nome, String email, String cpf, String senhaHash, String senhaSalt,
                      StatusUsuario status, String tokenConfirmacao, LocalDateTime prazoConfirmacao,
                      LocalDateTime criadoEm) implements Json.JsonConvertivel {

    public String iniciais() {
        String[] p = nome.trim().split("\\s+");
        String s = p[0].substring(0, 1);
        if (p.length > 1) s += p[p.length - 1].substring(0, 1);
        return s.toUpperCase();
    }

    @Override
    public Map<String, Object> paraJson() {
        return Json.obj("id", id, "nome", nome, "email", email,
                "cpf", cpf.substring(0, 3) + ".***.***-" + cpf.substring(9),
                "status", status, "iniciais", iniciais());
    }
}
