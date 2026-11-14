package br.ucsal.finia.infra;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso ao banco de dados via JDBC.
 * Por padrão usa H2 em arquivo (./data/finia_simples.mv.db). A URL pode ser trocada pela
 * variável de ambiente FINIA_DB_URL (ex.: MySQL), com FINIA_DB_USER/FINIA_DB_PASS.
 */
public final class Database {

    private static String url = System.getenv().getOrDefault("FINIA_DB_URL",
            "jdbc:h2:file:./data/finia_simples;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1");
    private static String user = System.getenv().getOrDefault("FINIA_DB_USER", "sa");
    private static String pass = System.getenv().getOrDefault("FINIA_DB_PASS", "");

    static {
        try {
            Class.forName("org.h2.Driver"); // registra o driver mesmo em JARs sem META-INF/services
        } catch (ClassNotFoundException ignored) {
            // outro banco configurado via FINIA_DB_URL
        }
    }

    private Database() {
    }

    /** Permite apontar para outro banco (usado nos testes com H2 em memória). */
    public static void configurar(String novaUrl, String novoUser, String novaSenha) {
        url = novaUrl;
        user = novoUser;
        pass = novaSenha;
    }

    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    /** Executa o schema.sql (criação das tabelas). */
    public static void inicializarEsquema() {
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IllegalStateException("schema.sql não encontrado");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection c = conectar(); Statement st = c.createStatement()) {
                for (String comando : dividirComandos(sql)) {
                    st.execute(comando);
                }
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Falha ao criar o esquema do banco: " + e.getMessage(), e);
        }
    }

    private static List<String> dividirComandos(String sql) {
        List<String> comandos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String linha : sql.split("\n")) {
            String l = linha.strip();
            if (l.startsWith("--") || l.isEmpty()) continue;
            int comentario = l.indexOf(" --");
            if (comentario >= 0) l = l.substring(0, comentario);
            atual.append(l).append(' ');
            if (l.endsWith(";")) {
                comandos.add(atual.toString().trim().replaceAll(";$", ""));
                atual.setLength(0);
            }
        }
        return comandos;
    }
}
