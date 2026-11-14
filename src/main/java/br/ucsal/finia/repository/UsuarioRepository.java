package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.Usuario;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Acesso à tabela usuario. */
public class UsuarioRepository {

    public long inserir(String nome, String email, String senhaHash, String senhaSalt) {
        return Jdbc.inserir("INSERT INTO usuario (nome, email, senha_hash, senha_salt) VALUES (?, ?, ?, ?)",
                nome, email, senhaHash, senhaSalt);
    }

    public Optional<Usuario> buscarPorId(long id) {
        return Jdbc.buscar("SELECT * FROM usuario WHERE id = ?", UsuarioRepository::mapear, id);
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return Jdbc.buscar("SELECT * FROM usuario WHERE email = ?", UsuarioRepository::mapear, email);
    }

    private static Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(rs.getLong("id"), rs.getString("nome"), rs.getString("email"),
                rs.getString("senha_hash"), rs.getString("senha_salt"));
    }
}
