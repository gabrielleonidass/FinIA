package br.ucsal.finia.service;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.model.Enums.StatusUsuario;
import br.ucsal.finia.model.Usuario;
import br.ucsal.finia.repository.UsuarioRepository;
import br.ucsal.finia.util.CpfValidador;
import br.ucsal.finia.util.SenhaUtil;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/** Cadastro, confirmação de e-mail e autenticação (RF01 / RN01 / UC01). */
public class UsuarioService {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private final UsuarioRepository usuarios;
    private final AlertaService alertas;

    public UsuarioService(UsuarioRepository usuarios, AlertaService alertas) {
        this.usuarios = usuarios;
        this.alertas = alertas;
    }

    /** Cria a conta em estado PENDENTE e devolve o token de confirmação enviado por e-mail. */
    public Usuario cadastrar(String nome, String email, String cpf, String senha) {
        if (nome == null || nome.trim().length() < 3) throw ApiException.requisicaoInvalida("Informe o nome completo.");
        if (email == null || !EMAIL.matcher(email).matches()) throw ApiException.requisicaoInvalida("E-mail inválido.");
        String cpfLimpo = CpfValidador.somenteDigitos(cpf);
        if (!CpfValidador.valido(cpfLimpo))
            throw ApiException.requisicaoInvalida("CPF inválido: verifique o formato e os dígitos verificadores.");
        if (!SenhaUtil.atendePolitica(senha))
            throw ApiException.requisicaoInvalida("A senha deve ter no mínimo 8 caracteres, com letra maiúscula, número e caractere especial.");
        if (usuarios.existeEmail(email)) throw ApiException.conflito("Já existe uma conta com este e-mail.");
        if (usuarios.existeCpf(cpfLimpo)) throw ApiException.conflito("Já existe uma conta com este CPF.");

        String salt = SenhaUtil.novoSalt();
        String token = UUID.randomUUID().toString().replace("-", "");
        Usuario novo = new Usuario(null, nome.trim(), email.trim().toLowerCase(), cpfLimpo, SenhaUtil.hash(senha, salt), salt,
                StatusUsuario.PENDENTE, token, LocalDateTime.now().plusHours(24), null);
        long id = usuarios.inserir(novo);
        alertas.criarConfiguracoesPadrao(id);
        return usuarios.buscarPorId(id).orElseThrow();
    }

    /** Ativa a conta se o link for usado dentro do prazo de 24 horas. */
    public Usuario confirmarEmail(String token) {
        Usuario u = usuarios.buscarPorToken(token)
                .orElseThrow(() -> ApiException.naoEncontrado("Link de confirmação inválido ou já utilizado."));
        if (u.prazoConfirmacao() != null && LocalDateTime.now().isAfter(u.prazoConfirmacao())) {
            usuarios.atualizarStatus(u.id(), StatusUsuario.SUSPENSA);
            throw ApiException.proibido("O prazo de 24 horas expirou e a conta foi suspensa temporariamente.");
        }
        usuarios.atualizarStatus(u.id(), StatusUsuario.ATIVA);
        return usuarios.buscarPorId(u.id()).orElseThrow();
    }

    public Usuario autenticar(String email, String senha) {
        Usuario u = usuarios.buscarPorEmail(email == null ? "" : email.trim())
                .filter(x -> senha != null && SenhaUtil.confere(senha, x.senhaSalt(), x.senhaHash()))
                .orElseThrow(() -> ApiException.naoAutorizado("E-mail ou senha incorretos."));
        if (u.status() == StatusUsuario.PENDENTE && u.prazoConfirmacao() != null
                && LocalDateTime.now().isAfter(u.prazoConfirmacao())) {
            usuarios.atualizarStatus(u.id(), StatusUsuario.SUSPENSA);
            throw ApiException.proibido("Conta suspensa: o e-mail não foi confirmado em 24 horas.");
        }
        if (u.status() == StatusUsuario.PENDENTE)
            throw ApiException.proibido("Confirme seu e-mail pelo link enviado antes de entrar.");
        if (u.status() == StatusUsuario.SUSPENSA)
            throw ApiException.proibido("Conta suspensa. Entre em contato com o suporte.");
        return u;
    }
}
