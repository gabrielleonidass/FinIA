package br.ucsal.finia.service;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.model.Usuario;
import br.ucsal.finia.repository.UsuarioRepository;
import br.ucsal.finia.util.SenhaUtil;

import java.util.regex.Pattern;

/** Regras de cadastro e login. */
public class UsuarioService {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final UsuarioRepository repositorio;

    public UsuarioService(UsuarioRepository repositorio) {
        this.repositorio = repositorio;
    }

    public Usuario cadastrar(String nome, String email, String senha) {
        if (nome == null || nome.isBlank()) throw ApiException.requisicaoInvalida("Informe seu nome.");
        if (email == null || !EMAIL.matcher(email.trim()).matches())
            throw ApiException.requisicaoInvalida("Informe um e-mail válido.");
        if (!SenhaUtil.atendePolitica(senha))
            throw ApiException.requisicaoInvalida(
                    "A senha precisa ter no mínimo 8 caracteres, com uma letra maiúscula, um número e um símbolo.");

        String emailNormalizado = email.trim().toLowerCase();
        if (repositorio.buscarPorEmail(emailNormalizado).isPresent())
            throw ApiException.conflito("Este e-mail já está cadastrado.");

        String salt = SenhaUtil.novoSalt();
        long id = repositorio.inserir(nome.trim(), emailNormalizado, SenhaUtil.hash(senha, salt), salt);
        return repositorio.buscarPorId(id).orElseThrow();
    }

    public Usuario entrar(String email, String senha) {
        if (email == null || senha == null) throw ApiException.naoAutorizado("E-mail ou senha incorretos.");
        return repositorio.buscarPorEmail(email.trim().toLowerCase())
                .filter(u -> SenhaUtil.confere(senha, u.senhaSalt(), u.senhaHash()))
                .orElseThrow(() -> ApiException.naoAutorizado("E-mail ou senha incorretos."));
    }
}
