package br.ucsal.finia.util;

/** Validação de CPF por formato e dígitos verificadores (RF01 / RN01). */
public final class CpfValidador {

    private CpfValidador() {
    }

    /** Remove pontuação, mantendo apenas dígitos. */
    public static String somenteDigitos(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("\\D", "");
    }

    public static boolean valido(String cpf) {
        String c = somenteDigitos(cpf);
        if (c.length() != 11 || c.chars().distinct().count() == 1) return false;
        int d1 = digito(c, 9, 10);
        int d2 = digito(c, 10, 11);
        return d1 == c.charAt(9) - '0' && d2 == c.charAt(10) - '0';
    }

    private static int digito(String c, int qtd, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < qtd; i++) soma += (c.charAt(i) - '0') * (pesoInicial - i);
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
