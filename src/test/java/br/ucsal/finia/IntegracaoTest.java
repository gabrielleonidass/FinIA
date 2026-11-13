package br.ucsal.finia;

import br.ucsal.finia.infra.DadosIniciais;
import br.ucsal.finia.infra.Database;
import br.ucsal.finia.model.ContaFinanceira;
import br.ucsal.finia.model.Enums.*;
import br.ucsal.finia.model.Transacao;
import br.ucsal.finia.model.Usuario;
import br.ucsal.finia.repository.*;
import br.ucsal.finia.service.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Testes de integração com banco H2 em memória (serviços + repositórios). */
class IntegracaoTest {

    static UsuarioRepository usuarios = new UsuarioRepository();
    static CategoriaRepository categorias = new CategoriaRepository();
    static ContaRepository contas = new ContaRepository();
    static TransacaoRepository transacoes = new TransacaoRepository();
    static ReclassificacaoRepository reclass = new ReclassificacaoRepository();
    static RegraRepository regras = new RegraRepository();
    static AlertaRepository alertaRepo = new AlertaRepository();
    static AlertaService alertas;
    static UsuarioService usuarioService;
    static CategorizadorService categorizador;
    static TransacaoService transacaoService;
    static ImportacaoService importacao;
    static long usuarioId, contaId;

    @BeforeAll
    static void preparar() {
        Database.configurar("jdbc:h2:mem:finia_teste;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Database.inicializarEsquema();
        alertas = new AlertaService(alertaRepo, transacoes, contas);
        usuarioService = new UsuarioService(usuarios, alertas);
        categorizador = new CategorizadorService(categorias, regras, reclass);
        OrcamentoService orc = new OrcamentoService(new OrcamentoRepository(), transacoes, categorias, alertas);
        transacaoService = new TransacaoService(transacoes, contas, categorias, reclass, regras, categorizador, alertas, orc);
        importacao = new ImportacaoService(transacaoService);
        new DadosIniciais(usuarios, categorias, contas, transacaoService, new MetaRepository(), orc, alertaRepo, alertas,
                regras, new FamiliaRepository(), usuarioService).carregar();

        Usuario u = usuarioService.cadastrar("Usuário de Teste", "teste@finia.com", "111.444.777-35", "Teste@123");
        usuarios.atualizarStatus(u.id(), StatusUsuario.ATIVA);
        usuarioId = u.id();
        contaId = contas.inserir(new ContaFinanceira(null, usuarioId, "Conta teste", "Banco X", TipoConta.CONTA_CORRENTE,
                BigDecimal.ZERO, null, true, false));
    }

    @Test
    void rf01_cadastroPendenteNaoAutenticaAteConfirmarEmail() {
        Usuario u = usuarioService.cadastrar("Maria Teste", "maria@finia.com", "935.411.347-80", "Maria@2026");
        assertEquals(StatusUsuario.PENDENTE, u.status());
        assertThrows(RuntimeException.class, () -> usuarioService.autenticar("maria@finia.com", "Maria@2026"));
        usuarioService.confirmarEmail(u.tokenConfirmacao());
        assertEquals(u.id(), usuarioService.autenticar("maria@finia.com", "Maria@2026").id());
    }

    @Test
    void rf01_cpfInvalidoRejeitaCadastro() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> usuarioService.cadastrar("Fulano", "fulano@finia.com", "123.456.789-00", "Fulano@2026"));
        assertTrue(e.getMessage().contains("CPF inválido"));
    }

    @Test
    void rf03_uberTripVaiParaTransporteComConfiancaExibida() {
        CategorizadorService.Resultado r = categorizador.classificar(usuarioId, "UBER TRIP", TipoTransacao.DESPESA);
        assertEquals("Transporte", r.categoria().nome());
        assertTrue(r.confianca() >= CategorizadorService.LIMIAR_CONFIANCA);
        assertEquals(StatusTransacao.CATEGORIZADA, r.status());
    }

    @Test
    void rf03_descricaoDesconhecidaFicaIncerta() {
        CategorizadorService.Resultado r = categorizador.classificar(usuarioId, "PIX ENVIADO JOAO", TipoTransacao.DESPESA);
        assertEquals(StatusTransacao.INCERTA, r.status());
        assertTrue(r.confianca() < CategorizadorService.LIMIAR_CONFIANCA);
    }

    @Test
    void rf02_csvForaDoPadraoInformaLinhaEMotivo() {
        String csv = "data;descricao;valor\n02/11/2026;UBER TRIP;-20,00\n31/02/2026;PADARIA;-10,00\n03/11/2026;;-5,00\n";
        Map<String, Object> r = importacao.importar(usuarioId, contaId, "extrato.csv", csv);
        assertEquals(false, r.get("sucesso"));
        @SuppressWarnings("unchecked") List<String> erros = (List<String>) r.get("erros");
        assertTrue(erros.get(0).startsWith("Linha 3"));
        assertTrue(erros.get(1).startsWith("Linha 4"));
    }

    @Test
    void rf02_transacaoDuplicadaNaoEImportada() {
        String csv = "data;descricao;valor\n05/10/2026;CINEMARK SALVADOR;-40,00\n";
        assertEquals(1, importacao.importar(usuarioId, contaId, "a.csv", csv).get("importadas"));
        Map<String, Object> segunda = importacao.importar(usuarioId, contaId, "a.csv", csv);
        assertEquals(0, segunda.get("importadas"));
        assertEquals(1, segunda.get("duplicadas"));
    }

    @Test
    void rf04_tresCorrecoesIguaisSugeremRegra() {
        long lazer = categorias.buscarPorNome("Lazer").orElseThrow().id();
        Map<String, Object> ultima = null;
        for (int i = 1; i <= 3; i++) {
            Transacao t = transacaoService.registrar(usuarioId, contaId, LocalDate.of(2026, 9, i), "BOTECO DO ZE " + i,
                    new BigDecimal("30"), TipoTransacao.DESPESA, OrigemTransacao.MANUAL);
            ultima = transacaoService.reclassificar(usuarioId, t.id(), lazer);
        }
        assertEquals(true, ultima.get("sugerirRegra"));
        transacaoService.criarRegra(usuarioId, (String) ultima.get("padrao"), lazer);
        assertEquals("Lazer", categorizador.classificar(usuarioId, "BOTECO DO ZE 99", TipoTransacao.DESPESA).categoria().nome());
    }

    @Test
    void rn09_contaDesativadaNaoRecebeNovasTransacoes() {
        long id = contas.inserir(new ContaFinanceira(null, usuarioId, "Cartão velho", "Banco Y", TipoConta.CARTAO_CREDITO,
                BigDecimal.ZERO, 10, true, false));
        transacaoService.registrar(usuarioId, id, LocalDate.of(2026, 8, 1), "NETFLIX.COM", new BigDecimal("55.90"),
                TipoTransacao.DESPESA, OrigemTransacao.MANUAL);
        contas.definirAtiva(id, usuarioId, false);
        assertThrows(RuntimeException.class, () -> transacaoService.registrar(usuarioId, id, LocalDate.of(2026, 8, 2),
                "SPOTIFY", new BigDecimal("21.90"), TipoTransacao.DESPESA, OrigemTransacao.MANUAL));
        assertEquals(1, contas.contarTransacoes(id), "histórico preservado");
    }

    @Test
    void rf12_somenteMembroAtivoComPermissaoEditarPodeEditar() {
        var editor = new br.ucsal.finia.model.MembroGrupo(1L, 1, "A", "a@x.com", PermissaoMembro.EDITAR, StatusMembro.ATIVO);
        var leitor = new br.ucsal.finia.model.MembroGrupo(2L, 1, "B", "b@x.com", PermissaoMembro.VISUALIZAR, StatusMembro.ATIVO);
        var convidado = new br.ucsal.finia.model.MembroGrupo(3L, 1, "C", "c@x.com", PermissaoMembro.EDITAR, StatusMembro.CONVIDADO);
        assertTrue(FamiliaService.podeEditar(editor));
        assertFalse(FamiliaService.podeEditar(leitor));
        assertFalse(FamiliaService.podeEditar(convidado));
        assertFalse(FamiliaService.podeVisualizar(convidado));
    }
}
