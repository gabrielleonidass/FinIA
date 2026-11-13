package br.ucsal.finia.service;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Enums.PermissaoMembro;
import br.ucsal.finia.model.Enums.StatusMembro;
import br.ucsal.finia.model.GrupoFamiliar;
import br.ucsal.finia.model.MembroGrupo;
import br.ucsal.finia.repository.FamiliaRepository;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Núcleo financeiro familiar (RF12 / RN10). Somente o administrador do grupo
 * convida, remove e redefine permissões; o acesso do membro só vale após o aceite.
 */
public class FamiliaService {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private final FamiliaRepository familia;

    public FamiliaService(FamiliaRepository familia) {
        this.familia = familia;
    }

    public Map<String, Object> consultar(long usuarioId) {
        return familia.grupoDoAdmin(usuarioId)
                .map(g -> Json.obj("grupo", Json.obj("id", g.id(), "nome", g.nome()), "admin", true,
                        "membros", familia.membros(g.id())))
                .orElse(Json.obj("grupo", null, "admin", false, "membros", java.util.List.of()));
    }

    public void criarGrupo(long usuarioId, String nome) {
        if (familia.grupoDoAdmin(usuarioId).isPresent()) throw ApiException.conflito("Você já administra um grupo familiar.");
        if (nome == null || nome.isBlank()) throw ApiException.requisicaoInvalida("Informe o nome do grupo.");
        familia.criarGrupo(nome.trim(), usuarioId);
    }

    public void convidar(long usuarioId, String nome, String email, PermissaoMembro permissao) {
        GrupoFamiliar g = grupoAdministrado(usuarioId);
        if (nome == null || nome.isBlank()) throw ApiException.requisicaoInvalida("Informe o nome do membro.");
        if (email == null || !EMAIL.matcher(email).matches()) throw ApiException.requisicaoInvalida("E-mail inválido.");
        if (familia.existeMembro(g.id(), email)) throw ApiException.conflito("Este e-mail já foi convidado.");
        familia.convidar(g.id(), nome.trim(), email.trim().toLowerCase(), permissao == null ? PermissaoMembro.VISUALIZAR : permissao);
    }

    public void alterarPermissao(long usuarioId, long membroId, PermissaoMembro p) {
        if (familia.alterarPermissao(membroId, grupoAdministrado(usuarioId).id(), p) == 0)
            throw ApiException.naoEncontrado("Membro não encontrado.");
    }

    /** Simula o aceite explícito do convite pelo membro. */
    public void aceitarConvite(long usuarioId, long membroId) {
        if (familia.aceitar(membroId, grupoAdministrado(usuarioId).id()) == 0)
            throw ApiException.naoEncontrado("Convite não encontrado.");
    }

    public void remover(long usuarioId, long membroId) {
        if (familia.remover(membroId, grupoAdministrado(usuarioId).id()) == 0)
            throw ApiException.naoEncontrado("Membro não encontrado.");
    }

    /** Regra de autorização do RF12: só membro ATIVO com permissão EDITAR pode alterar dados compartilhados. */
    public static boolean podeEditar(MembroGrupo m) {
        return m.status() == StatusMembro.ATIVO && m.permissao() == PermissaoMembro.EDITAR;
    }

    public static boolean podeVisualizar(MembroGrupo m) {
        return m.status() == StatusMembro.ATIVO;
    }

    private GrupoFamiliar grupoAdministrado(long usuarioId) {
        return familia.grupoDoAdmin(usuarioId)
                .orElseThrow(() -> ApiException.proibido("Somente o administrador do grupo pode realizar esta ação."));
    }
}
