package br.ucsal.finia.controller;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.infra.Requisicao;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.infra.Sessoes;
import br.ucsal.finia.model.Usuario;
import br.ucsal.finia.service.UsuarioService;

/** Telas de Login e Cadastro. */
public class AuthController {

    private final UsuarioService usuarios;

    public AuthController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    public void registrar(Roteador r) {
        r.publico("POST", "/api/auth/cadastro", this::cadastrar);
        r.publico("POST", "/api/auth/login", this::login);
        r.publico("POST", "/api/auth/sair", this::sair);
        r.get("/api/auth/eu", req -> Json.obj("usuario", req.usuario()));
    }

    /** Cria a conta e já deixa a pessoa logada. */
    private Object cadastrar(Requisicao req) {
        Usuario u = usuarios.cadastrar(req.texto("nome"), req.texto("email"), req.texto("senha"));
        abrirSessao(req, u);
        return Json.obj("usuario", u);
    }

    private Object login(Requisicao req) {
        Usuario u = usuarios.entrar(req.texto("email"), req.texto("senha"));
        abrirSessao(req, u);
        return Json.obj("usuario", u);
    }

    private Object sair(Requisicao req) {
        Sessoes.encerrar(req.cookie(Sessoes.COOKIE));
        req.getExchange().getResponseHeaders().add("Set-Cookie", Sessoes.COOKIE + "=; Path=/; Max-Age=0");
        return null;
    }

    private static void abrirSessao(Requisicao req, Usuario u) {
        String token = Sessoes.criar(u.id());
        req.getExchange().getResponseHeaders().add("Set-Cookie",
                Sessoes.COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict");
    }
}
