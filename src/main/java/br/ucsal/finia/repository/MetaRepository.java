package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.Meta;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Acesso à tabela meta. */
public class MetaRepository {

    public long inserir(long usuarioId, String nome, BigDecimal valorAlvo, BigDecimal valorGuardado, LocalDate prazo) {
        return Jdbc.inserir("INSERT INTO meta (usuario_id, nome, valor_alvo, valor_guardado, prazo) VALUES (?, ?, ?, ?, ?)",
                usuarioId, nome, valorAlvo, valorGuardado, prazo);
    }

    public List<Meta> listar(long usuarioId) {
        return Jdbc.listar("SELECT * FROM meta WHERE usuario_id = ? ORDER BY prazo", MetaRepository::mapear, usuarioId);
    }

    public Optional<Meta> buscar(long usuarioId, long id) {
        return Jdbc.buscar("SELECT * FROM meta WHERE usuario_id = ? AND id = ?", MetaRepository::mapear, usuarioId, id);
    }

    public void atualizarValorGuardado(long id, BigDecimal novoValor) {
        Jdbc.executar("UPDATE meta SET valor_guardado = ? WHERE id = ?", novoValor, id);
    }

    public int excluir(long usuarioId, long id) {
        return Jdbc.executar("DELETE FROM meta WHERE id = ? AND usuario_id = ?", id, usuarioId);
    }

    private static Meta mapear(ResultSet rs) throws SQLException {
        return new Meta(rs.getLong("id"), rs.getLong("usuario_id"), rs.getString("nome"),
                rs.getBigDecimal("valor_alvo"), rs.getBigDecimal("valor_guardado"), Jdbc.data(rs, "prazo"));
    }
}
