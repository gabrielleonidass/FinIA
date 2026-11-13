package br.ucsal.finia.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Política e armazenamento seguro de senhas (PBKDF2-HMAC-SHA256 com salt). */
public final class SenhaUtil {

    /** Mínimo 8 caracteres, com letra maiúscula, número e caractere especial (RF01). */
    private static final Pattern POLITICA = Pattern.compile("^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    private static final int ITERACOES = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SenhaUtil() {
    }

    public static boolean atendePolitica(String senha) {
        return senha != null && POLITICA.matcher(senha).matches();
    }

    public static String novoSalt() {
        byte[] s = new byte[16];
        RANDOM.nextBytes(s);
        return HexFormat.of().formatHex(s);
    }

    public static String hash(String senha, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), HexFormat.of().parseHex(salt), ITERACOES, 256);
            byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(h);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean confere(String senha, String salt, String hashEsperado) {
        return MessageDigest.isEqual(hash(senha, salt).getBytes(), hashEsperado.getBytes());
    }
}
