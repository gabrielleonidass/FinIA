package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.Categoria;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Acesso à tabela categoria. */
public class CategoriaRepository {

    private static Categoria mapear(ResultSet rs) throws SQLException {
        return new Categoria(rs.getLong("id"), rs.getString("nome"), rs.getString("cor"), rs.getBoolean("receita"));
    }

    public List<Categoria> listarTodas() {
        return Jdbc.listar("select * from categoria order by receita, nome", CategoriaRepository::mapear);
    }

    public List<Categoria> listarDespesas() {
        return Jdbc.listar("select * from categoria where receita = false order by nome", CategoriaRepository::mapear);
    }

    public Optional<Categoria> buscarPorId(long id) {
        return Jdbc.buscar("select * from categoria where id = ?", CategoriaRepository::mapear, id);
    }

    public Optional<Categoria> buscarPorNome(String nome) {
        return Jdbc.buscar("select * from categoria where nome = ?", CategoriaRepository::mapear, nome);
    }

    public long inserir(String nome, String cor, boolean receita) {
        return Jdbc.inserir("insert into categoria (nome,cor,receita) values (?,?,?)", nome, cor, receita);
    }

    public long contar() {
        return Jdbc.numero("select count(*) from categoria");
    }
}
