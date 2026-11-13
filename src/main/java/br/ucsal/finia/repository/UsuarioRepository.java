package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.Enums.StatusUsuario;
import br.ucsal.finia.model.Usuario;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/** Acesso à tabela usuario. */
public class UsuarioRepository {

    private static final String COLS = "id,nome,email,cpf,senha_hash,senha_salt,status,token_confirmacao,prazo_confirmacao,criado_em";

    private static Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(rs.getLong("id"), rs.getString("nome"), rs.getString("email"), rs.getString("cpf"),
                rs.getString("senha_hash"), rs.getString("senha_salt"), StatusUsuario.valueOf(rs.getString("status")),
                rs.getString("token_confirmacao"), Jdbc.dataHora(rs, "prazo_confirmacao"), Jdbc.dataHora(rs, "criado_em"));
    }

    public Optional<Usuario> buscarPorId(long id) {
        return Jdbc.buscar("select " + COLS + " from usuario where id = ?", UsuarioRepository::mapear, id);
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return Jdbc.buscar("select " + COLS + " from usuario where lower(email) = lower(?)", UsuarioRepository::mapear, email);
    }

    public Optional<Usuario> buscarPorToken(String token) {
        return Jdbc.buscar("select " + COLS + " from usuario where token_confirmacao = ?", UsuarioRepository::mapear, token);
    }

    public boolean existeEmail(String email) {
        return Jdbc.numero("select count(*) from usuario where lower(email) = lower(?)", email) > 0;
    }

    public boolean existeCpf(String cpf) {
        return Jdbc.numero("select count(*) from usuario where cpf = ?", cpf) > 0;
    }

    public long contar() {
        return Jdbc.numero("select count(*) from usuario");
    }

    public long inserir(Usuario u) {
        return Jdbc.inserir("insert into usuario (nome,email,cpf,senha_hash,senha_salt,status,token_confirmacao," +
                        "prazo_confirmacao,criado_em) values (?,?,?,?,?,?,?,?,?)",
                u.nome(), u.email(), u.cpf(), u.senhaHash(), u.senhaSalt(), u.status(), u.tokenConfirmacao(),
                u.prazoConfirmacao(), LocalDateTime.now());
    }

    public void atualizarStatus(long id, StatusUsuario status) {
        Jdbc.executar("update usuario set status = ?, token_confirmacao = null where id = ?", status, id);
    }
}
