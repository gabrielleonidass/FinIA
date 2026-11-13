package br.ucsal.finia;

import br.ucsal.finia.controller.*;
import br.ucsal.finia.infra.Database;
import br.ucsal.finia.infra.DadosIniciais;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.repository.*;
import br.ucsal.finia.service.*;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * FinIA - Aplicativo de gestão financeira pessoal e familiar com
 * categorização automática de transações.
 *
 * Arquitetura em camadas (MVC):
 * front-end (HTML/CSS/JS em resources/static) -> controllers (API REST/JSON)
 * -> services (regras de negócio) -> repositories (JDBC) -> banco H2.
 *
 * Execução: mvn package && java -jar target/finia.jar  ->  http://localhost:8080
 */
public class FinIaApplication {

    public static void main(String[] args) throws Exception {
        int porta = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        // 1) Banco de dados
        Database.inicializarEsquema();

        // 2) Repositórios
        UsuarioRepository usuarioRepo = new UsuarioRepository();
        CategoriaRepository categoriaRepo = new CategoriaRepository();
        ContaRepository contaRepo = new ContaRepository();
        TransacaoRepository transacaoRepo = new TransacaoRepository();
        ReclassificacaoRepository reclassRepo = new ReclassificacaoRepository();
        RegraRepository regraRepo = new RegraRepository();
        MetaRepository metaRepo = new MetaRepository();
        OrcamentoRepository orcamentoRepo = new OrcamentoRepository();
        AlertaRepository alertaRepo = new AlertaRepository();
        FamiliaRepository familiaRepo = new FamiliaRepository();

        // 3) Serviços (injeção de dependências manual)
        AlertaService alertaService = new AlertaService(alertaRepo, transacaoRepo, contaRepo);
        UsuarioService usuarioService = new UsuarioService(usuarioRepo, alertaService);
        CategorizadorService categorizador = new CategorizadorService(categoriaRepo, regraRepo, reclassRepo);
        OrcamentoService orcamentoService = new OrcamentoService(orcamentoRepo, transacaoRepo, categoriaRepo, alertaService);
        TransacaoService transacaoService = new TransacaoService(transacaoRepo, contaRepo, categoriaRepo, reclassRepo,
                regraRepo, categorizador, alertaService, orcamentoService);
        ImportacaoService importacaoService = new ImportacaoService(transacaoService);
        MetaService metaService = new MetaService(metaRepo, transacaoRepo, alertaService);
        SimulacaoService simulacaoService = new SimulacaoService(metaRepo, transacaoRepo, categoriaRepo, metaService);
        DashboardService dashboardService = new DashboardService(contaRepo, transacaoRepo, alertaRepo, alertaService, metaService);
        RelatorioService relatorioService = new RelatorioService(transacaoRepo, metaService);
        FamiliaService familiaService = new FamiliaService(familiaRepo);

        // 4) Dados iniciais (categorias + usuário de demonstração)
        new DadosIniciais(usuarioRepo, categoriaRepo, contaRepo, transacaoService, metaRepo, orcamentoService,
                alertaRepo, alertaService, regraRepo, familiaRepo, usuarioService).carregar();

        // 5) Rotas da API
        Roteador roteador = new Roteador(usuarioRepo);
        new AuthController(usuarioService, alertaRepo).registrar(roteador);
        new DashboardController(dashboardService, categoriaRepo).registrar(roteador);
        new TransacaoController(transacaoService, importacaoService, regraRepo, reclassRepo).registrar(roteador);
        new ContaController(contaRepo).registrar(roteador);
        new MetaController(metaService).registrar(roteador);
        new OrcamentoController(orcamentoService, orcamentoRepo).registrar(roteador);
        new AlertaController(alertaRepo).registrar(roteador);
        new SimulacaoController(simulacaoService).registrar(roteador);
        new RelatorioController(relatorioService).registrar(roteador);
        new FamiliaController(familiaService).registrar(roteador);

        // 6) Servidor HTTP
        HttpServer server = HttpServer.create(new InetSocketAddress(porta), 0);
        server.createContext("/", roteador);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
        System.out.println("FinIA rodando em http://localhost:" + porta + "  (login demo: demo@finia.com / Demo@1234)");
    }
}
