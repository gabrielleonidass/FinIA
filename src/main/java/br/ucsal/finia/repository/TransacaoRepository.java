package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.Categoria;
import br.ucsal.finia.model.TipoTransacao;
import br.ucsal.finia.model.Transacao;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Acesso à tabela transacao (sempre filtrando pelo usuário logado). */
public class TransacaoRepository {

    private static final String SELECT = """
            SELECT t.*, c.nome AS cat_nome, c.tipo AS cat_tipo, c.cor AS cat_cor
            FROM transacao t JOIN categoria c ON c.id = t.categoria_id
            """;

    public long inserir(long usuarioId, String descricao, BigDecimal valor, TipoTransacao tipo, LocalDate data,
                        long categoriaId, boolean automatica) {
        return Jdbc.inserir("""
                INSERT INTO transacao (usuario_id, descricao, valor, tipo, data, categoria_id, categoria_automatica)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", usuarioId, descricao, valor, tipo, data, categoriaId, automatica);
    }

    public List<Transacao> listarDoMes(long usuarioId, YearMonth mes) {
        return Jdbc.listar(SELECT + " WHERE t.usuario_id = ? AND t.data BETWEEN ? AND ? ORDER BY t.data DESC, t.id DESC",
                TransacaoRepository::mapear, usuarioId, mes.atDay(1), mes.atEndOfMonth());
    }

    public List<Transacao> ultimas(long usuarioId, int quantidade) {
        return Jdbc.listar(SELECT + " WHERE t.usuario_id = ? ORDER BY t.data DESC, t.id DESC LIMIT " + quantidade,
                TransacaoRepository::mapear, usuarioId);
    }

    public Optional<Transacao> buscar(long usuarioId, long id) {
        return Jdbc.buscar(SELECT + " WHERE t.usuario_id = ? AND t.id = ?", TransacaoRepository::mapear, usuarioId, id);
    }

    /** Categoria que o próprio usuário escolheu da última vez para essa mesma descrição (aprendizado). */
    public Optional<Long> categoriaEscolhidaAntes(long usuarioId, String descricao) {
        return Jdbc.buscar("""
                SELECT categoria_id FROM transacao
                WHERE usuario_id = ? AND UPPER(descricao) = UPPER(?) AND categoria_automatica = FALSE
                ORDER BY id DESC LIMIT 1""", rs -> rs.getLong(1), usuarioId, descricao);
    }

    public void alterarCategoria(long usuarioId, long id, long categoriaId) {
        Jdbc.executar("UPDATE transacao SET categoria_id = ?, categoria_automatica = FALSE WHERE id = ? AND usuario_id = ?",
                categoriaId, id, usuarioId);
    }

    public int excluir(long usuarioId, long id) {
        return Jdbc.executar("DELETE FROM transacao WHERE id = ? AND usuario_id = ?", id, usuarioId);
    }

    public BigDecimal somarDoMes(long usuarioId, YearMonth mes, TipoTransacao tipo) {
        return Jdbc.decimal("SELECT SUM(valor) FROM transacao WHERE usuario_id = ? AND tipo = ? AND data BETWEEN ? AND ?",
                usuarioId, tipo, mes.atDay(1), mes.atEndOfMonth());
    }

    /** Total de despesas do mês agrupado por categoria (para o gráfico). */
    public List<Map<String, Object>> despesasPorCategoria(long usuarioId, YearMonth mes) {
        return Jdbc.listar("""
                SELECT c.nome, c.cor, SUM(t.valor) AS total
                FROM transacao t JOIN categoria c ON c.id = t.categoria_id
                WHERE t.usuario_id = ? AND t.tipo = 'DESPESA' AND t.data BETWEEN ? AND ?
                GROUP BY c.nome, c.cor ORDER BY total DESC""",
                rs -> Map.of("categoria", rs.getString("nome"), "cor", rs.getString("cor"),
                        "total", rs.getBigDecimal("total")),
                usuarioId, mes.atDay(1), mes.atEndOfMonth());
    }

    public long contar(long usuarioId) {
        return Jdbc.numero("SELECT COUNT(*) FROM transacao WHERE usuario_id = ?", usuarioId);
    }

    private static Transacao mapear(ResultSet rs) throws SQLException {
        Categoria c = new Categoria(rs.getLong("categoria_id"), rs.getString("cat_nome"),
                TipoTransacao.valueOf(rs.getString("cat_tipo")), rs.getString("cat_cor"));
        return new Transacao(rs.getLong("id"), rs.getLong("usuario_id"), rs.getString("descricao"),
                rs.getBigDecimal("valor"), TipoTransacao.valueOf(rs.getString("tipo")),
                Jdbc.data(rs, "data"), c, rs.getBoolean("categoria_automatica"));
    }
}
