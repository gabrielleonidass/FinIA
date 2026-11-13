package br.ucsal.finia.infra;

import br.ucsal.finia.model.Usuario;
import br.ucsal.finia.repository.UsuarioRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Roteador HTTP: associa método + caminho (com parâmetros {id}) a um
 * manipulador, faz a autenticação por sessão, converte o retorno em JSON e
 * serve os arquivos estáticos do front-end (resources/static).
 */
public class Roteador implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(Roteador.class.getName());

    @FunctionalInterface
    public interface Manipulador {
        Object tratar(Requisicao req) throws Exception;
    }

    private record Rota(String metodo, String[] partes, Manipulador m, boolean publica) {
    }

    private final List<Rota> rotas = new ArrayList<>();
    private final UsuarioRepository usuarios;

    public Roteador(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public Roteador get(String caminho, Manipulador m) {
        return add("GET", caminho, m, false);
    }

    public Roteador post(String caminho, Manipulador m) {
        return add("POST", caminho, m, false);
    }

    public Roteador put(String caminho, Manipulador m) {
        return add("PUT", caminho, m, false);
    }

    public Roteador delete(String caminho, Manipulador m) {
        return add("DELETE", caminho, m, false);
    }

    public Roteador publico(String metodo, String caminho, Manipulador m) {
        return add(metodo, caminho, m, true);
    }

    private Roteador add(String metodo, String caminho, Manipulador m, boolean publica) {
        rotas.add(new Rota(metodo, caminho.substring(1).split("/"), m, publica));
        return this;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String caminho = ex.getRequestURI().getPath();
        try {
            if (!caminho.startsWith("/api/")) {
                servirEstatico(ex, caminho);
                return;
            }
            String[] partes = caminho.substring(1).split("/");
            for (Rota r : rotas) {
                Map<String, String> vars = casar(r.partes(), partes);
                if (vars == null || !r.metodo().equals(ex.getRequestMethod())) continue;
                Requisicao req = new Requisicao(ex, vars);
                if (!r.publica()) {
                    Long id = Sessoes.usuarioDaSessao(req.cookie(Sessoes.COOKIE));
                    Optional<Usuario> u = id == null ? Optional.empty() : usuarios.buscarPorId(id);
                    if (u.isEmpty()) throw ApiException.naoAutorizado("Sessão expirada. Faça login novamente.");
                    req.setUsuario(u.get());
                }
                Object resultado = r.m().tratar(req);
                responder(ex, resultado);
                return;
            }
            throw ApiException.naoEncontrado("Recurso não encontrado: " + caminho);
        } catch (ApiException e) {
            enviarJson(ex, e.getStatus(), Json.obj("erro", e.getMessage()));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao processar " + caminho, e);
            enviarJson(ex, 500, Json.obj("erro", "Erro interno: " + e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private static Map<String, String> casar(String[] modelo, String[] real) {
        if (modelo.length != real.length) return null;
        Map<String, String> vars = new HashMap<>();
        for (int i = 0; i < modelo.length; i++) {
            if (modelo[i].startsWith("{")) vars.put(modelo[i].substring(1, modelo[i].length() - 1), real[i]);
            else if (!modelo[i].equals(real[i])) return null;
        }
        return vars;
    }

    private void responder(HttpExchange ex, Object resultado) throws IOException {
        if (resultado instanceof Arquivo a) {
            ex.getResponseHeaders().set("Content-Type", a.contentType());
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + a.nomeArquivo() + "\"");
            ex.sendResponseHeaders(200, a.conteudo().length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(a.conteudo());
            }
        } else {
            enviarJson(ex, 200, resultado == null ? Json.obj("ok", true) : resultado);
        }
    }

    static void enviarJson(HttpExchange ex, int status, Object corpo) throws IOException {
        byte[] b = Json.escrever(corpo).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static final Map<String, String> TIPOS = Map.of(
            "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8", "svg", "image/svg+xml",
            "png", "image/png", "ico", "image/x-icon", "csv", "text/csv; charset=utf-8",
            "ofx", "application/x-ofx");

    private void servirEstatico(HttpExchange ex, String caminho) throws IOException {
        if (caminho.equals("/favicon.ico")) {
            ex.sendResponseHeaders(204, -1);
            return;
        }
        if (caminho.equals("/")) caminho = "/index.html";
        if (caminho.contains("..")) caminho = "/index.html";
        if (!caminho.contains(".")) caminho = caminho + ".html";
        try (InputStream in = Roteador.class.getResourceAsStream("/static" + caminho)) {
            if (in == null) {
                byte[] b = "Página não encontrada".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(404, b.length);
                ex.getResponseBody().write(b);
                return;
            }
            byte[] b = in.readAllBytes();
            String ext = caminho.substring(caminho.lastIndexOf('.') + 1);
            ex.getResponseHeaders().set("Content-Type", TIPOS.getOrDefault(ext, "application/octet-stream"));
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        }
    }
}
