package br.ucsal.finia;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tudo que fala com o banco passa por aqui.
 * Cada linha que volta do SELECT vira um Map.
 * Os valores sempre vão pelos "?", assim ninguém consegue fazer SQL injection.
 */
public class Banco {

    private static String url = "jdbc:h2:file:./data/finia_simples;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";

    /** Os testes usam isso pra rodar num banco em memória, sem mexer no arquivo. */
    static void usar(String novaUrl) {
        url = novaUrl;
    }

    private static Connection conectar() throws SQLException {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver do H2 não encontrado");
        }
        return DriverManager.getConnection(url, "sa", "");
    }

    /** Roda o schema.sql e cria as tabelas que ainda não existem. */
    static void criarTabelas() {
        try (InputStream in = Banco.class.getResourceAsStream("/schema.sql");
             Connection c = conectar(); Statement st = c.createStatement()) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("--[^\\n]*", "");
            for (String comando : sql.split(";")) {
                if (!comando.isBlank()) st.execute(comando);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível criar as tabelas: " + e.getMessage(), e);
        }
    }

    static List<Map<String, Object>> linhas(String sql, Object... parametros) {
        try (Connection c = conectar(); PreparedStatement ps = preparar(c, sql, parametros);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> lista = new ArrayList<>();
            ResultSetMetaData colunas = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> linha = new LinkedHashMap<>();
                for (int i = 1; i <= colunas.getColumnCount(); i++) {
                    Object valor = rs.getObject(i);
                    linha.put(colunas.getColumnLabel(i), valor instanceof Date d ? d.toLocalDate() : valor);
                }
                lista.add(linha);
            }
            return lista;
        } catch (SQLException e) {
            throw new IllegalStateException("Erro no banco: " + e.getMessage(), e);
        }
    }

    /** Igual ao linhas(), mas pega só a primeira (ou null se não achar nada). */
    static Map<String, Object> linha(String sql, Object... parametros) {
        List<Map<String, Object>> l = linhas(sql, parametros);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Se não tiver nenhuma linha, o SUM do SQL volta NULL, então a gente troca por zero. */
    static BigDecimal somar(String sql, Object... parametros) {
        Object total = linha(sql, parametros).get("total");
        return total == null ? BigDecimal.ZERO : (BigDecimal) total;
    }

    /** Faz o INSERT e devolve o id que o banco criou. */
    static long inserir(String sql, Object... parametros) {
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(ps, parametros);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Erro no banco: " + e.getMessage(), e);
        }
    }

    static int executar(String sql, Object... parametros) {
        try (Connection c = conectar(); PreparedStatement ps = preparar(c, sql, parametros)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro no banco: " + e.getMessage(), e);
        }
    }

    private static PreparedStatement preparar(Connection c, String sql, Object... parametros) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        preencher(ps, parametros);
        return ps;
    }

    private static void preencher(PreparedStatement ps, Object... parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            Object p = parametros[i];
            ps.setObject(i + 1, p instanceof LocalDate d ? Date.valueOf(d) : p);
        }
    }
}
