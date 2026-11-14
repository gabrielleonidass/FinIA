package br.ucsal.finia.infra;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utilitário de acesso a dados (estilo JdbcTemplate simplificado) usado
 * pelos repositórios. Converte automaticamente LocalDate, LocalDateTime,
 * BigDecimal e enums nos parâmetros.
 */
public final class Jdbc {

    private Jdbc() {
    }

    @FunctionalInterface
    public interface Mapeador<T> {
        T mapear(ResultSet rs) throws SQLException;
    }

    public static <T> List<T> listar(String sql, Mapeador<T> m, Object... params) {
        try (Connection c = Database.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            definir(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> lista = new ArrayList<>();
                while (rs.next()) lista.add(m.mapear(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro de banco: " + e.getMessage(), e);
        }
    }

    public static <T> Optional<T> buscar(String sql, Mapeador<T> m, Object... params) {
        List<T> l = listar(sql, m, params);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    public static BigDecimal decimal(String sql, Object... params) {
        // SUM() de nenhuma linha devolve NULL no SQL: nesse caso a soma é zero.
        List<BigDecimal> l = listar(sql, rs -> rs.getBigDecimal(1), params);
        return l.isEmpty() || l.get(0) == null ? BigDecimal.ZERO : l.get(0);
    }

    public static long numero(String sql, Object... params) {
        return buscar(sql, rs -> rs.getLong(1), params).orElse(0L);
    }

    /** Executa INSERT e devolve o id gerado. */
    public static long inserir(String sql, Object... params) {
        try (Connection c = Database.conectar();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            definir(ps, params);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro de banco: " + e.getMessage(), e);
        }
    }

    /** Executa UPDATE/DELETE e devolve o número de linhas afetadas. */
    public static int executar(String sql, Object... params) {
        try (Connection c = Database.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            definir(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro de banco: " + e.getMessage(), e);
        }
    }

    private static void definir(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p instanceof LocalDate d) ps.setDate(i + 1, Date.valueOf(d));
            else if (p instanceof LocalDateTime dt) ps.setTimestamp(i + 1, Timestamp.valueOf(dt));
            else if (p instanceof Enum<?> e) ps.setString(i + 1, e.name());
            else ps.setObject(i + 1, p);
        }
    }

    public static LocalDate data(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    public static LocalDateTime dataHora(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }

    public static Long longOuNulo(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    public static Integer intOuNulo(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
