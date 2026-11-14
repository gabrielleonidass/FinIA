package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.Categoria;
import br.ucsal.finia.model.TipoTransacao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Acesso à tabela categoria. */
public class CategoriaRepository {

    public void inserirSeNaoExistir(String nome, TipoTransacao tipo, String cor) {
        if (buscarPorNome(nome).isEmpty()) {
            Jdbc.inserir("INSERT INTO categoria (nome, tipo, cor) VALUES (?, ?, ?)", nome, tipo, cor);
        }
    }

    public List<Categoria> listar() {
        return Jdbc.listar("SELECT * FROM categoria ORDER BY tipo, nome", CategoriaRepository::mapear);
    }

    public Optional<Categoria> buscarPorId(long id) {
        return Jdbc.buscar("SELECT * FROM categoria WHERE id = ?", CategoriaRepository::mapear, id);
    }

    public Optional<Categoria> buscarPorNome(String nome) {
        return Jdbc.buscar("SELECT * FROM categoria WHERE nome = ?", CategoriaRepository::mapear, nome);
    }

    static Categoria mapear(ResultSet rs) throws SQLException {
        return new Categoria(rs.getLong("id"), rs.getString("nome"),
                TipoTransacao.valueOf(rs.getString("tipo")), rs.getString("cor"));
    }
}
