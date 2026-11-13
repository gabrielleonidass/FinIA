package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.Enums.PermissaoMembro;
import br.ucsal.finia.model.Enums.StatusMembro;
import br.ucsal.finia.model.GrupoFamiliar;
import br.ucsal.finia.model.MembroGrupo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Acesso às tabelas grupo_familiar e membro_grupo (RF12). */
public class FamiliaRepository {

    private static MembroGrupo mapearMembro(ResultSet rs) throws SQLException {
        return new MembroGrupo(rs.getLong("id"), rs.getLong("grupo_id"), rs.getString("nome"), rs.getString("email"),
                PermissaoMembro.valueOf(rs.getString("permissao")), StatusMembro.valueOf(rs.getString("status")));
    }

    public Optional<GrupoFamiliar> grupoDoAdmin(long adminId) {
        return Jdbc.buscar("select * from grupo_familiar where admin_id=?",
                rs -> new GrupoFamiliar(rs.getLong("id"), rs.getString("nome"), rs.getLong("admin_id")), adminId);
    }

    public long criarGrupo(String nome, long adminId) {
        return Jdbc.inserir("insert into grupo_familiar (nome,admin_id,criado_em) values (?,?,?)", nome, adminId, LocalDateTime.now());
    }

    public List<MembroGrupo> membros(long grupoId) {
        return Jdbc.listar("select * from membro_grupo where grupo_id=? order by status desc, nome", FamiliaRepository::mapearMembro, grupoId);
    }

    public boolean existeMembro(long grupoId, String email) {
        return Jdbc.numero("select count(*) from membro_grupo where grupo_id=? and lower(email)=lower(?)", grupoId, email) > 0;
    }

    public long convidar(long grupoId, String nome, String email, PermissaoMembro permissao) {
        return Jdbc.inserir("insert into membro_grupo (grupo_id,nome,email,permissao,status,convidado_em) values (?,?,?,?,?,?)",
                grupoId, nome, email, permissao, StatusMembro.CONVIDADO, LocalDateTime.now());
    }

    public int alterarPermissao(long membroId, long grupoId, PermissaoMembro permissao) {
        return Jdbc.executar("update membro_grupo set permissao=? where id=? and grupo_id=?", permissao, membroId, grupoId);
    }

    public int aceitar(long membroId, long grupoId) {
        return Jdbc.executar("update membro_grupo set status=? where id=? and grupo_id=?", StatusMembro.ATIVO, membroId, grupoId);
    }

    public int remover(long membroId, long grupoId) {
        return Jdbc.executar("delete from membro_grupo where id=? and grupo_id=?", membroId, grupoId);
    }
}
