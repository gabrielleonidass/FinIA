package br.ucsal.finia;

import br.ucsal.finia.controller.AuthController;
import br.ucsal.finia.controller.InicioController;
import br.ucsal.finia.controller.MetaController;
import br.ucsal.finia.controller.TransacaoController;
import br.ucsal.finia.infra.DadosIniciais;
import br.ucsal.finia.infra.Database;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.repository.CategoriaRepository;
import br.ucsal.finia.repository.MetaRepository;
import br.ucsal.finia.repository.TransacaoRepository;
import br.ucsal.finia.repository.UsuarioRepository;
import br.ucsal.finia.service.*;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class FinIaApplication {

    public static void main(String[] args) throws Exception {
        int porta = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        // 1) Cria as tabelas do banco (se ainda não existirem)
        Database.inicializarEsquema();

        // 2) Repositórios (acesso ao banco)
        UsuarioRepository usuarioRepo = new UsuarioRepository();
        CategoriaRepository categoriaRepo = new CategoriaRepository();
        TransacaoRepository transacaoRepo = new TransacaoRepository();
        MetaRepository metaRepo = new MetaRepository();

        // 3) Serviços (regras de negócio)
        UsuarioService usuarioService = new UsuarioService(usuarioRepo);
        CategorizadorService categorizador = new CategorizadorService(categoriaRepo, transacaoRepo);
        TransacaoService transacaoService = new TransacaoService(transacaoRepo, categoriaRepo, categorizador);
        MetaService metaService = new MetaService(metaRepo);
        InicioService inicioService = new InicioService(transacaoRepo, metaRepo);

        // 4) Dados de demonstração
        new DadosIniciais(categoriaRepo, usuarioRepo, transacaoRepo, metaRepo, usuarioService, transacaoService).carregar();

        // 5) Rotas da API (uma classe por tela)
        Roteador roteador = new Roteador(usuarioRepo);
        new AuthController(usuarioService).registrar(roteador);
        new InicioController(inicioService).registrar(roteador);
        new TransacaoController(transacaoService, categorizador, categoriaRepo).registrar(roteador);
        new MetaController(metaService).registrar(roteador);

        // 6) Liga o servidor
        HttpServer server = HttpServer.create(new InetSocketAddress(porta), 0);
        server.createContext("/", roteador);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("FinIA rodando em http://localhost:" + porta);
        System.out.println("Login de teste: " + DadosIniciais.EMAIL_DEMO + " / " + DadosIniciais.SENHA_DEMO);
        System.out.println("Para desligar, aperte Ctrl+C.");
    }
}
