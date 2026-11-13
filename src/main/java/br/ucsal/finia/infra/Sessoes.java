package br.ucsal.finia.infra;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controle de sessões autenticadas. O token é guardado em cookie HttpOnly
 * (FINIA_SESSAO) e expira após 2 horas de inatividade.
 */
public final class Sessoes {

    public static final String COOKIE = "FINIA_SESSAO";
    private static final long EXPIRACAO_SEGUNDOS = 2 * 60 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Sessao> SESSOES = new ConcurrentHashMap<>();

    private record Sessao(long usuarioId, Instant ultimoAcesso) {
    }

    private Sessoes() {
    }

    public static String criar(long usuarioId) {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        String token = HexFormat.of().formatHex(b);
        SESSOES.put(token, new Sessao(usuarioId, Instant.now()));
        return token;
    }

    /** Devolve o id do usuário da sessão ou null se inexistente/expirada. */
    public static Long usuarioDaSessao(String token) {
        if (token == null) return null;
        Sessao s = SESSOES.get(token);
        if (s == null) return null;
        if (s.ultimoAcesso().plusSeconds(EXPIRACAO_SEGUNDOS).isBefore(Instant.now())) {
            SESSOES.remove(token);
            return null;
        }
        SESSOES.put(token, new Sessao(s.usuarioId(), Instant.now()));
        return s.usuarioId();
    }

    public static void encerrar(String token) {
        if (token != null) SESSOES.remove(token);
    }
}
