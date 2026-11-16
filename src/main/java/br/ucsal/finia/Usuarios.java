package br.ucsal.finia;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

/** Tudo do Login e do Cadastro (RF01). */
public class Usuarios {

    static void rotas(Web web) {
        web.publica("POST", "/api/auth/cadastro", p -> {
            Map<String, Object> u = cadastrar(p.texto("nome"), p.texto("email"), p.texto("cpf"), p.texto("senha"));
            p.entrar((Long) u.get("id")); // já entra logado
            return Web.obj("usuario", u);
        });
        web.publica("POST", "/api/auth/login", p -> {
            Map<String, Object> u = entrar(p.texto("email"), p.texto("senha"));
            p.entrar((Long) u.get("id"));
            return Web.obj("usuario", u);
        });
        web.publica("POST", "/api/auth/sair", p -> {
            p.sair();
            return null;
        });
        web.get("/api/auth/eu", p -> Web.obj("usuario", buscar(p.usuarioId())));
    }

    static Map<String, Object> cadastrar(String nome, String email, String cpf, String senha) {
        if (nome == null) throw new Web.Erro(400, "Informe seu nome.");
        if (email == null || !email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$"))
            throw new Web.Erro(400, "Informe um e-mail válido.");
        if (!cpfValido(cpf)) throw new Web.Erro(400, "Informe um CPF válido.");
        if (!senhaForte(senha))
            throw new Web.Erro(400, "A senha precisa ter no mínimo 8 caracteres, com uma letra maiúscula, um número e um símbolo.");

        email = email.toLowerCase();
        cpf = cpf.replaceAll("\\D", "");
        if (Banco.linha("SELECT id FROM usuario WHERE email = ?", email) != null)
            throw new Web.Erro(409, "Este e-mail já está cadastrado.");
        if (Banco.linha("SELECT id FROM usuario WHERE cpf = ?", cpf) != null)
            throw new Web.Erro(409, "Este CPF já está cadastrado.");

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = HexFormat.of().formatHex(salt);
        long id = Banco.inserir("INSERT INTO usuario (nome, email, cpf, senha_hash, senha_salt) VALUES (?, ?, ?, ?, ?)",
                nome, email, cpf, hash(senha, saltHex), saltHex);
        return buscar(id);
    }

    static Map<String, Object> entrar(String email, String senha) {
        Map<String, Object> u = Banco.linha("SELECT * FROM usuario WHERE email = ?", email == null ? "" : email.toLowerCase());
        boolean senhaCerta = u != null && senha != null && MessageDigest.isEqual(
                hash(senha, (String) u.get("senha_salt")).getBytes(), ((String) u.get("senha_hash")).getBytes());
        if (!senhaCerta) throw new Web.Erro(401, "E-mail ou senha incorretos.");
        return buscar((Long) u.get("id"));
    }

    /** Só o que pode ir pra tela. A senha nunca sai daqui. */
    static Map<String, Object> buscar(long id) {
        return Banco.linha("SELECT id, nome, email, cpf FROM usuario WHERE id = ?", id);
    }

    /** Senha forte: 8 caracteres ou mais, com maiúscula, número e símbolo. */
    static boolean senhaForte(String senha) {
        return senha != null && senha.matches("^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    }

    /** Faz a conta dos 2 últimos dígitos do CPF pra ver se ele é de verdade. */
    static boolean cpfValido(String cpf) {
        String c = cpf == null ? "" : cpf.replaceAll("\\D", "");
        if (c.length() != 11 || c.chars().distinct().count() == 1) return false; // ex.: 111.111.111-11
        for (int posicao = 9; posicao <= 10; posicao++) {
            int soma = 0;
            for (int i = 0; i < posicao; i++) soma += (c.charAt(i) - '0') * (posicao + 1 - i);
            if ((soma * 10) % 11 % 10 != c.charAt(posicao) - '0') return false;
        }
        return true;
    }

    /** Embaralha a senha (PBKDF2 com salt). Nem quem abrir o banco consegue ver a senha. */
    private static String hash(String senha, String saltHex) {
        try {
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), HexFormat.of().parseHex(saltHex), 120_000, 256);
            return HexFormat.of().formatHex(
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
