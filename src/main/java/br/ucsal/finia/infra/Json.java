package br.ucsal.finia.infra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.*;

/**
 * Serializador e leitor de JSON mínimo, sem dependências externas.
 * Suporta Map, Collection, String, Number, Boolean, null, enums e datas.
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------ escrita

    public static String escrever(Object o) {
        StringBuilder sb = new StringBuilder();
        escrever(o, sb);
        return sb.toString();
    }

    private static void escrever(Object o, StringBuilder sb) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            texto(s, sb);
        } else if (o instanceof BigDecimal b) {
            sb.append(b.toPlainString());
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Enum<?> e) {
            texto(e.name(), sb);
        } else if (o instanceof LocalDate || o instanceof LocalDateTime || o instanceof Temporal) {
            texto(o.toString(), sb);
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                texto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escrever(e.getValue(), sb);
            }
            sb.append('}');
        } else if (o instanceof Collection<?> c) {
            sb.append('[');
            boolean primeiro = true;
            for (Object item : c) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                escrever(item, sb);
            }
            sb.append(']');
        } else if (o instanceof JsonConvertivel j) {
            escrever(j.paraJson(), sb);
        } else {
            texto(o.toString(), sb);
        }
    }

    private static void texto(String s, StringBuilder sb) {
        sb.append('"');
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------ leitura

    public static Object ler(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        Leitor l = new Leitor(json);
        Object v = l.valor();
        l.espacos();
        if (l.pos < json.length()) throw new IllegalArgumentException("JSON inválido");
        return v;
    }

    private static final class Leitor {
        private final String s;
        private int pos;

        Leitor(String s) {
            this.s = s;
        }

        void espacos() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        Object valor() {
            espacos();
            if (pos >= s.length()) throw new IllegalArgumentException("JSON incompleto");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> objeto();
                case '[' -> lista();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> numero();
            };
        }

        Object literal(String palavra, Object v) {
            if (!s.startsWith(palavra, pos)) throw new IllegalArgumentException("JSON inválido");
            pos += palavra.length();
            return v;
        }

        Map<String, Object> objeto() {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++;
            espacos();
            if (s.charAt(pos) == '}') {
                pos++;
                return m;
            }
            while (true) {
                espacos();
                String chave = string();
                espacos();
                if (s.charAt(pos++) != ':') throw new IllegalArgumentException("JSON inválido");
                m.put(chave, valor());
                espacos();
                char c = s.charAt(pos++);
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("JSON inválido");
            }
        }

        List<Object> lista() {
            List<Object> l = new ArrayList<>();
            pos++;
            espacos();
            if (s.charAt(pos) == ']') {
                pos++;
                return l;
            }
            while (true) {
                l.add(valor());
                espacos();
                char c = s.charAt(pos++);
                if (c == ']') return l;
                if (c != ',') throw new IllegalArgumentException("JSON inválido");
            }
        }

        String string() {
            if (s.charAt(pos) != '"') throw new IllegalArgumentException("JSON inválido");
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Object numero() {
            int ini = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            String n = s.substring(ini, pos);
            if (n.isEmpty()) throw new IllegalArgumentException("JSON inválido");
            return new BigDecimal(n);
        }
    }

    /** Implementada por objetos de domínio que sabem se converter para JSON. */
    public interface JsonConvertivel {
        Map<String, Object> paraJson();
    }

    /** Construtor fluente de mapas ordenados. */
    public static Map<String, Object> obj(Object... chavesValores) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < chavesValores.length; i += 2) {
            m.put(String.valueOf(chavesValores[i]), chavesValores[i + 1]);
        }
        return m;
    }
}
