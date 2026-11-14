package br.ucsal.finia;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.DadosIniciais;
import br.ucsal.finia.infra.Database;
import br.ucsal.finia.model.Meta;
import br.ucsal.finia.model.Transacao;
import br.ucsal.finia.model.Usuario;
import br.ucsal.finia.repository.CategoriaRepository;
import br.ucsal.finia.repository.MetaRepository;
import br.ucsal.finia.repository.TransacaoRepository;
import br.ucsal.finia.repository.UsuarioRepository;
import br.ucsal.finia.service.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static br.ucsal.finia.model.TipoTransacao.DESPESA;
import static br.ucsal.finia.model.TipoTransacao.RECEITA;
import static org.junit.jupiter.api.Assertions.*;

/** Testes das regras principais usando um banco H2 em memória. */
class FinIaTest {

    static UsuarioRepository usuarioRepo = new UsuarioRepository();
    static CategoriaRepository categoriaRepo = new CategoriaRepository();
    static TransacaoRepository transacaoRepo = new TransacaoRepository();
    static MetaRepository metaRepo = new MetaRepository();
    static UsuarioService usuarios;
    static CategorizadorService categorizador;
    static TransacaoService transacoes;
    static MetaService metas;
    static InicioService inicio;
    static long usuarioId;

    @BeforeAll
    static void preparar() {
        Database.configurar("jdbc:h2:mem:finia_teste;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Database.inicializarEsquema();
        usuarios = new UsuarioService(usuarioRepo);
        categorizador = new CategorizadorService(categoriaRepo, transacaoRepo);
        transacoes = new TransacaoService(transacaoRepo, categoriaRepo, categorizador);
        metas = new MetaService(metaRepo);
        inicio = new InicioService(transacaoRepo, metaRepo);
        new DadosIniciais(categoriaRepo, usuarioRepo, transacaoRepo, metaRepo, usuarios, transacoes).criarCategorias();
        usuarioId = usuarios.cadastrar("Teste da Silva", "teste@finia.com", "Teste@123").id();
    }

    // ------------------------------------------------------------ cadastro e login

    @Test
    void naoAceitaSenhaFraca() {
        ApiException e = assertThrows(ApiException.class, () -> usuarios.cadastrar("Ana", "ana@x.com", "123456"));
        assertEquals(400, e.getStatus());
    }

    @Test
    void naoAceitaEmailRepetido() {
        ApiException e = assertThrows(ApiException.class,
                () -> usuarios.cadastrar("Outro", "TESTE@finia.com", "Outra@123"));
        assertEquals(409, e.getStatus());
    }

    @Test
    void loginFuncionaSoComSenhaCerta() {
        Usuario u = usuarios.entrar("teste@finia.com", "Teste@123");
        assertEquals("Teste da Silva", u.nome());
        assertThrows(ApiException.class, () -> usuarios.entrar("teste@finia.com", "Errada@123"));
    }

    // ------------------------------------------------------------ categorização automática

    @Test
    void categorizaPorPalavraChave() {
        assertEquals("Transporte", categorizador.sugerir(usuarioId, "Uber trabalho", DESPESA).categoria().nome());
        assertEquals("Alimentação", categorizador.sugerir(usuarioId, "iFood*Pedido 123", DESPESA).categoria().nome());
        assertEquals("Saúde", categorizador.sugerir(usuarioId, "Farmácia Pague Menos", DESPESA).categoria().nome());
        assertEquals("Salário", categorizador.sugerir(usuarioId, "Salário empresa", RECEITA).categoria().nome());
    }

    @Test
    void palavraMaisLongaVence() {
        // "MERCADO LIVRE" (Compras) ganha de "MERCADO" (Alimentação)
        assertEquals("Compras", categorizador.sugerir(usuarioId, "Mercado Livre teclado", DESPESA).categoria().nome());
        assertEquals("Alimentação", categorizador.sugerir(usuarioId, "Mercado do bairro", DESPESA).categoria().nome());
    }

    @Test
    void palavraQueApareceAntesVence() {
        assertEquals("Transporte", categorizador.sugerir(usuarioId, "Uber para a faculdade", DESPESA).categoria().nome());
    }

    @Test
    void semPalavraConhecidaVaiParaOutros() {
        assertEquals("Outros", categorizador.sugerir(usuarioId, "XPTO 123", DESPESA).categoria().nome());
        assertEquals("Outras receitas", categorizador.sugerir(usuarioId, "XPTO 123", RECEITA).categoria().nome());
    }

    @Test
    void aprendeComACorrecaoDoUsuario() {
        Transacao t = transacoes.registrar(usuarioId, "Loja do Zé", new BigDecimal("40"), DESPESA, LocalDate.now(), null);
        assertEquals("Compras", t.categoria().nome());
        assertTrue(t.categoriaAutomatica());

        long lazer = categoriaRepo.buscarPorNome("Lazer").orElseThrow().id();
        transacoes.trocarCategoria(usuarioId, t.id(), lazer);

        Transacao nova = transacoes.registrar(usuarioId, "Loja do Zé", new BigDecimal("25"), DESPESA, LocalDate.now(), null);
        assertEquals("Lazer", nova.categoria().nome());
    }

    // ------------------------------------------------------------ transações

    @Test
    void naoAceitaValorZeroOuSemDescricao() {
        assertThrows(ApiException.class, () ->
                transacoes.registrar(usuarioId, "Teste", BigDecimal.ZERO, DESPESA, LocalDate.now(), null));
        assertThrows(ApiException.class, () ->
                transacoes.registrar(usuarioId, " ", BigDecimal.TEN, DESPESA, LocalDate.now(), null));
    }

    @Test
    void naoAceitaCategoriaDeReceitaEmDespesa() {
        long salario = categoriaRepo.buscarPorNome("Salário").orElseThrow().id();
        assertThrows(ApiException.class, () ->
                transacoes.registrar(usuarioId, "Teste", BigDecimal.TEN, DESPESA, LocalDate.now(), salario));
    }

    @Test
    void resumoCalculaSaldoDoMes() {
        long outro = usuarios.cadastrar("Saldo", "saldo@finia.com", "Saldo@123").id();
        LocalDate dia = LocalDate.of(2026, 3, 10);
        transacoes.registrar(outro, "Salário", new BigDecimal("3000.00"), RECEITA, dia, null);
        transacoes.registrar(outro, "Aluguel", new BigDecimal("1200.00"), DESPESA, dia, null);
        transacoes.registrar(outro, "Uber", new BigDecimal("50.50"), DESPESA, dia, null);

        Map<String, Object> r = inicio.resumo(outro, YearMonth.of(2026, 3));
        assertEquals(0, new BigDecimal("3000.00").compareTo((BigDecimal) r.get("receitas")));
        assertEquals(0, new BigDecimal("1250.50").compareTo((BigDecimal) r.get("despesas")));
        assertEquals(0, new BigDecimal("1749.50").compareTo((BigDecimal) r.get("saldo")));
    }

    @Test
    void resumoDeMesSemTransacoesDaZero() {
        Map<String, Object> r = inicio.resumo(usuarioId, YearMonth.of(2020, 1));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) r.get("saldo")));
    }

    // ------------------------------------------------------------ metas

    @Test
    void metaCalculaQuantoGuardarPorMes() {
        Meta m = new Meta(1L, 1L, "Viagem", new BigDecimal("3000"), new BigDecimal("600"), LocalDate.of(2027, 5, 1));
        LocalDate hoje = LocalDate.of(2026, 11, 14);
        assertEquals(6, m.mesesRestantes(hoje));
        assertEquals(0, new BigDecimal("400.00").compareTo(m.valorPorMes(hoje)));
        assertEquals(20, m.percentual());
    }

    @Test
    void guardarDinheiroAtualizaAMeta() {
        Meta m = metas.criar(usuarioId, "Notebook", new BigDecimal("1000"), LocalDate.now().plusMonths(4));
        Meta depois = metas.guardar(usuarioId, m.id(), new BigDecimal("1000"));
        assertTrue(depois.concluida());
        assertEquals(100, depois.percentual());
    }

    @Test
    void metaPrecisaDePrazoFuturo() {
        assertThrows(ApiException.class, () ->
                metas.criar(usuarioId, "Antiga", new BigDecimal("100"), LocalDate.now().minusDays(1)));
    }
}
