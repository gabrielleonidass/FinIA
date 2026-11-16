package br.ucsal.finia;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor web do FinIA (usa o HttpServer que já vem no Java). Ele:
 *  1. entrega as telas (pasta resources/static);
 *  2. chama a ação certa para cada rota da API (ex.: GET /api/metas);
 *  3. confere o login (sessão guardada em cookie, expira em 2 horas sem uso);
 *  4. devolve a resposta em JSON, ou {"erro": "..."} quando algo dá errado.
 */
public class Web {

    /** Erro que vira resposta para a tela (400 = dado inválido, 401 = sem login, 404 = não encontrado...). */
    public static class Erro extends RuntimeException {
        final int status;

        public Erro(int status, String mensagem) {
            super(mensagem);
            this.status = status;
        }
    }

    /** O que uma rota faz: recebe o pedido e devolve o que vai para a tela. */
    public interface Acao {
        Object executar(Pedido pedido) throws Exception;
    }

    private record Rota(String metodo, String caminho, Acao acao, boolean publica) {
    }

    private record Sessao(long usuarioId, Instant ultimoUso) {
    }

    private final List<Rota> rotas = new ArrayList<>();
    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();

    public void get(String caminho, Acao acao) { rotas.add(new Rota("GET", caminho, acao, false)); }
    public void post(String caminho, Acao acao) { rotas.add(new Rota("POST", caminho, acao, false)); }
    public void put(String caminho, Acao acao) { rotas.add(new Rota("PUT", caminho, acao, false)); }
    public void delete(String caminho, Acao acao) { rotas.add(new Rota("DELETE", caminho, acao, false)); }

    /** Rota que funciona sem login (cadastro, login e sair). */
    public void publica(String metodo, String caminho, Acao acao) {
        rotas.add(new Rota(metodo, caminho, acao, true));
    }

    public void iniciar(int porta) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    // ================= pedido da tela =================

    /** Um pedido vindo da tela: os campos enviados, o {id} do caminho e quem está logado. */
    public class Pedido {
        private final HttpExchange troca;
        private final Map<String, String> campos = new HashMap<>();
        private final String id;
        private long usuarioId;

        Pedido(HttpExchange troca, String id) throws IOException {
            this.troca = troca;
            this.id = id;
            lerCampos(troca.getRequestURI().getRawQuery());                                       // ?mes=2026-11
            lerCampos(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); // formulário
        }

        public long usuarioId() {
            return usuarioId;
        }

        public long id() {
            try {
                return Long.parseLong(id);
            } catch (NumberFormatException e) {
                throw new Erro(400, "Identificador inválido.");
            }
        }

        /** Texto enviado pela tela, ou null se veio vazio. */
        public String texto(String campo) {
            String v = campos.get(campo);
            return v == null || v.isBlank() ? null : v.trim();
        }

        public BigDecimal decimal(String campo) {
            String v = texto(campo);
            if (v == null) return null;
            try {
                return new BigDecimal(v.contains(",") ? v.replace(".", "").replace(",", ".") : v); // aceita 1.234,56
            } catch (NumberFormatException e) {
                throw new Erro(400, "Número inválido no campo " + campo + ".");
            }
        }

        public Long numero(String campo) {
            BigDecimal v = decimal(campo);
            return v == null ? null : v.longValue();
        }

        public LocalDate data(String campo) {
            try {
                return texto(campo) == null ? null : LocalDate.parse(texto(campo));
            } catch (RuntimeException e) {
                throw new Erro(400, "Data inválida (use AAAA-MM-DD).");
            }
        }

        public boolean simOuNao(String campo) {
            return "true".equals(texto(campo));
        }

        /** Login: cria a sessão e guarda o código dela num cookie do navegador. */
        public void entrar(long usuario) {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            String token = HexFormat.of().formatHex(bytes);
            sessoes.put(token, new Sessao(usuario, Instant.now()));
            troca.getResponseHeaders().add("Set-Cookie", "FINIA_SESSAO=" + token + "; Path=/; HttpOnly; SameSite=Strict");
        }

        public void sair() {
            String token = cookie();
            if (token != null) sessoes.remove(token);
            troca.getResponseHeaders().add("Set-Cookie", "FINIA_SESSAO=; Path=/; Max-Age=0");
        }

        private String cookie() {
            String cabecalho = troca.getRequestHeaders().getFirst("Cookie");
            if (cabecalho == null) return null;
            for (String parte : cabecalho.split(";")) {
                String[] kv = parte.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals("FINIA_SESSAO")) return kv[1];
            }
            return null;
        }

        /** Lê "a=1&b=2" (formato em que o navegador envia formulários). */
        private void lerCampos(String texto) {
            if (texto == null || texto.isBlank()) return;
            for (String par : texto.split("&")) {
                String[] kv = par.split("=", 2);
                campos.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
        }
    }

    // ================= atendimento =================

    private void atender(HttpExchange troca) throws IOException {
        String caminho = troca.getRequestURI().getPath();
        try {
            if (caminho.startsWith("/api/")) responderApi(troca, caminho);
            else entregarTela(troca, caminho);
        } catch (Erro e) {
            enviar(troca, e.status, "application/json", json(obj("erro", e.getMessage())).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
            enviar(troca, 500, "application/json", json(obj("erro", "Erro interno: " + e.getMessage())).getBytes(StandardCharsets.UTF_8));
        } finally {
            troca.close();
        }
    }

    private void responderApi(HttpExchange troca, String caminho) throws Exception {
        for (Rota rota : rotas) {
            String id = casar(rota.caminho(), caminho);
            if (id == null || !rota.metodo().equals(troca.getRequestMethod())) continue;

            Pedido pedido = new Pedido(troca, id);
            if (!rota.publica()) pedido.usuarioId = usuarioLogado(pedido);
            Object resposta = rota.acao().executar(pedido);
            enviar(troca, 200, "application/json",
                    json(resposta == null ? obj("ok", true) : resposta).getBytes(StandardCharsets.UTF_8));
            return;
        }
        throw new Erro(404, "Rota não encontrada: " + caminho);
    }

    private long usuarioLogado(Pedido pedido) {
        String token = pedido.cookie();
        Sessao s = token == null ? null : sessoes.get(token);
        if (s == null || s.ultimoUso().plusSeconds(2 * 60 * 60).isBefore(Instant.now()))
            throw new Erro(401, "Sessão expirada. Faça login novamente.");
        sessoes.put(token, new Sessao(s.usuarioId(), Instant.now())); // renova as 2 horas
        return s.usuarioId();
    }

    /** Compara "/api/metas/{id}" com "/api/metas/7": devolve "7", "" se não tem id, ou null se não combina. */
    private static String casar(String modelo, String caminho) {
        String[] m = modelo.split("/"), c = caminho.split("/");
        if (m.length != c.length) return null;
        String id = "";
        for (int i = 0; i < m.length; i++) {
            if (m[i].equals("{id}")) id = c[i];
            else if (!m[i].equals(c[i])) return null;
        }
        return id;
    }

    /** Entrega os arquivos das telas: "/" -> index.html, "/metas" -> metas.html. */
    private void entregarTela(HttpExchange troca, String caminho) throws IOException {
        if (caminho.equals("/favicon.ico")) {   // a aba do navegador pede um ícone; não temos
            troca.sendResponseHeaders(204, -1);
            return;
        }
        if (caminho.equals("/") || caminho.contains("..")) caminho = "/index.html";
        if (!caminho.contains(".")) caminho += ".html";
        try (InputStream in = Web.class.getResourceAsStream("/static" + caminho)) {
            if (in == null) {
                enviar(troca, 404, "text/plain", "Página não encontrada".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String tipo = caminho.endsWith(".html") ? "text/html" : caminho.endsWith(".css") ? "text/css"
                    : caminho.endsWith(".js") ? "application/javascript" : "application/octet-stream";
            enviar(troca, 200, tipo, in.readAllBytes());
        }
    }

    private static void enviar(HttpExchange troca, int status, String tipo, byte[] corpo) throws IOException {
        troca.getResponseHeaders().set("Content-Type", tipo + "; charset=utf-8");
        troca.getResponseHeaders().set("Cache-Control", "no-store");
        troca.sendResponseHeaders(status, corpo.length);
        troca.getResponseBody().write(corpo);
    }

    // ================= JSON =================

    /** Monta um mapa a partir de pares: obj("nome", "Ana", "idade", 20). */
    public static Map<String, Object> obj(Object... chavesEValores) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) m.put((String) chavesEValores[i], chavesEValores[i + 1]);
        return m;
    }

    /** Transforma mapas, listas, números e textos em JSON. */
    public static String json(Object o) {
        if (o == null) return "null";
        if (o instanceof BigDecimal b) return b.toPlainString();
        if (o instanceof Number || o instanceof Boolean) return o.toString();
        if (o instanceof Map<?, ?> m) {
            StringJoiner sj = new StringJoiner(",", "{", "}");
            m.forEach((k, v) -> sj.add(texto(k) + ":" + json(v)));
            return sj.toString();
        }
        if (o instanceof Collection<?> lista) {
            StringJoiner sj = new StringJoiner(",", "[", "]");
            lista.forEach(item -> sj.add(json(item)));
            return sj.toString();
        }
        return texto(o); // textos e datas
    }

    private static String texto(Object o) {
        return '"' + o.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
    }
}
