package br.ucsal.finia.controller;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.infra.Requisicao;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.infra.Sessoes;
import br.ucsal.finia.model.Usuario;
import br.ucsal.finia.repository.AlertaRepository;
import br.ucsal.finia.service.UsuarioService;

/** Tela de login e cadastro (RF01 / UC01). */
public class AuthController {

    private final UsuarioService usuarios;
    private final AlertaRepository alertas;

    public AuthController(UsuarioService usuarios, AlertaRepository alertas) {
        this.usuarios = usuarios;
        this.alertas = alertas;
    }

    public void registrar(Roteador r) {
        r.publico("POST", "/api/auth/cadastro", this::cadastrar);
        r.publico("POST", "/api/auth/confirmar", this::confirmar);
        r.publico("POST", "/api/auth/login", this::login);
        r.publico("POST", "/api/auth/logout", this::logout);
        r.get("/api/auth/eu", this::eu);
    }

    private Object cadastrar(Requisicao req) {
        Usuario u = usuarios.cadastrar(req.texto("nome"), req.texto("email"), req.texto("cpf"), req.texto("senha"));
        // Em produção o link seria enviado por e-mail; aqui é devolvido para simular a caixa de entrada.
        return Json.obj("mensagem", "Cadastro realizado! Confirme seu e-mail em até 24 horas.",
                "email", u.email(), "linkConfirmacao", "/confirmar?token=" + u.tokenConfirmacao());
    }

    private Object confirmar(Requisicao req) {
        Usuario u = usuarios.confirmarEmail(req.textoObrigatorio("token", "token"));
        return Json.obj("mensagem", "E-mail confirmado. Sua conta está ativa!", "email", u.email());
    }

    private Object login(Requisicao req) {
        Usuario u = usuarios.autenticar(req.texto("email"), req.texto("senha"));
        String token = Sessoes.criar(u.id());
        req.getExchange().getResponseHeaders().add("Set-Cookie",
                Sessoes.COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict");
        return Json.obj("usuario", u);
    }

    private Object logout(Requisicao req) {
        Sessoes.encerrar(req.cookie(Sessoes.COOKIE));
        req.getExchange().getResponseHeaders().add("Set-Cookie", Sessoes.COOKIE + "=; Path=/; Max-Age=0");
        return null;
    }

    private Object eu(Requisicao req) {
        return Json.obj("usuario", req.usuario(), "alertasNaoLidos", alertas.contarNaoLidos(req.usuario().id()));
    }
}
