package br.ucsal.finia.infra;

import br.ucsal.finia.model.Usuario;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Encapsula uma requisição HTTP: parâmetros de rota, query string, corpo JSON e usuário logado. */
public class Requisicao {

    private final HttpExchange exchange;
    private final Map<String, String> rota;
    private final Map<String, String> query;
    private Map<String, Object> corpo;
    private Usuario usuario;

    public Requisicao(HttpExchange exchange, Map<String, String> rota) {
        this.exchange = exchange;
        this.rota = rota;
        this.query = parseQuery(exchange.getRequestURI().getRawQuery());
    }

    public HttpExchange getExchange() {
        return exchange;
    }

    public Usuario usuario() {
        return usuario;
    }

    void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public long idRota(String nome) {
        try {
            return Long.parseLong(rota.get(nome));
        } catch (NumberFormatException e) {
            throw ApiException.requisicaoInvalida("Identificador inválido: " + rota.get(nome));
        }
    }

    public String query(String nome) {
        return query.get(nome);
    }

    public String query(String nome, String padrao) {
        String v = query.get(nome);
        return v == null || v.isBlank() ? padrao : v;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> corpo() {
        if (corpo == null) {
            try {
                String txt = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Object o = Json.ler(txt);
                corpo = o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
            } catch (IOException | RuntimeException e) {
                throw ApiException.requisicaoInvalida("Corpo da requisição inválido (JSON esperado).");
            }
        }
        return corpo;
    }

    // ---------------------------------------------------- leitura tipada do corpo

    public String texto(String campo) {
        Object v = corpo().get(campo);
        return v == null ? null : String.valueOf(v).trim();
    }

    public String textoObrigatorio(String campo, String rotulo) {
        String v = texto(campo);
        if (v == null || v.isBlank()) throw ApiException.requisicaoInvalida("Informe o campo " + rotulo + ".");
        return v;
    }

    public BigDecimal decimal(String campo) {
        Object v = corpo().get(campo);
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            String s = String.valueOf(v).trim();
            if (s.contains(",")) s = s.replace(".", "").replace(",", ".");
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw ApiException.requisicaoInvalida("Valor numérico inválido em " + campo + ".");
        }
    }

    public BigDecimal decimalObrigatorio(String campo, String rotulo) {
        BigDecimal v = decimal(campo);
        if (v == null) throw ApiException.requisicaoInvalida("Informe o campo " + rotulo + ".");
        return v;
    }

    public Long longo(String campo) {
        BigDecimal v = decimal(campo);
        return v == null ? null : v.longValue();
    }

    public long longoObrigatorio(String campo) {
        Long v = longo(campo);
        if (v == null) throw ApiException.requisicaoInvalida("Informe o campo " + campo + ".");
        return v;
    }

    public Integer inteiro(String campo) {
        BigDecimal v = decimal(campo);
        return v == null ? null : v.intValue();
    }

    public boolean booleano(String campo) {
        Object v = corpo().get(campo);
        return v instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(v));
    }

    public LocalDate data(String campo) {
        String v = texto(campo);
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw ApiException.requisicaoInvalida("Data inválida em " + campo + " (use AAAA-MM-DD).");
        }
    }

    public String cookie(String nome) {
        String h = exchange.getRequestHeaders().getFirst("Cookie");
        if (h == null) return null;
        for (String parte : h.split(";")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(nome)) return kv[1];
        }
        return null;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isBlank()) return m;
        for (String par : raw.split("&")) {
            String[] kv = par.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            m.put(k, v);
        }
        return m;
    }
}
