package br.ucsal.finia;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Testes das regras principais usando um banco H2 em memória. */
class FinIaTest {

    static long usuarioId;

    @BeforeAll
    static void preparar() {
        Banco.usar("jdbc:h2:mem:finia_teste;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Banco.criarTabelas();
        FinIA.criarCategorias();
        usuarioId = (Long) Usuarios.cadastrar("Teste da Silva", "teste@finia.com", "529.982.247-25", "Teste@123").get("id");
    }

    static String categoria(String descricao, String tipo) {
        return (String) Categorizador.sugerir(usuarioId, descricao, tipo).categoria().get("nome");
    }

    @SuppressWarnings("unchecked")
    static String categoriaDe(Map<String, Object> transacao) {
        return (String) ((Map<String, Object>) transacao.get("categoria")).get("nome");
    }

    static long idDaCategoria(String nome) {
        return (Long) Banco.linha("SELECT id FROM categoria WHERE nome = ?", nome).get("id");
    }

    static BigDecimal valor(Map<String, Object> resumo, String campo) {
        return (BigDecimal) resumo.get(campo);
    }

    // ------------------------------------------------------------ cadastro e login

    @Test
    void naoAceitaSenhaFraca() {
        Web.Erro e = assertThrows(Web.Erro.class, () -> Usuarios.cadastrar("Ana", "ana@x.com", "11144477735", "123456"));
        assertEquals(400, e.status);
    }

    @Test
    void naoAceitaEmailRepetido() {
        Web.Erro e = assertThrows(Web.Erro.class,
                () -> Usuarios.cadastrar("Outro", "TESTE@finia.com", "11144477735", "Outra@123"));
        assertEquals(409, e.status);
    }

    @Test
    void cpfEObrigatorioValidoEUnico() {
        assertEquals(400, assertThrows(Web.Erro.class,
                () -> Usuarios.cadastrar("Bia", "bia@x.com", "123.456.789-00", "Bia@1234")).status);
        assertThrows(Web.Erro.class, () -> Usuarios.cadastrar("Bia", "bia@x.com", "", "Bia@1234"));
        assertEquals(409, assertThrows(Web.Erro.class,
                () -> Usuarios.cadastrar("Bia", "bia@x.com", "52998224725", "Bia@1234")).status);
    }

    @Test
    void loginFuncionaSoComSenhaCerta() {
        assertEquals("Teste da Silva", Usuarios.entrar("teste@finia.com", "Teste@123").get("nome"));
        assertThrows(Web.Erro.class, () -> Usuarios.entrar("teste@finia.com", "Errada@123"));
    }

    // ------------------------------------------------------------ categorização automática

    @Test
    void categorizaPorPalavraChave() {
        assertEquals("Transporte", categoria("Uber trabalho", "DESPESA"));
        assertEquals("Alimentação", categoria("iFood*Pedido 123", "DESPESA"));
        assertEquals("Saúde", categoria("Farmácia Pague Menos", "DESPESA"));
        assertEquals("Salário", categoria("Salário empresa", "RECEITA"));
    }

    @Test
    void palavraMaisLongaVence() {
        assertEquals("Compras", categoria("Mercado Livre teclado", "DESPESA"));
        assertEquals("Alimentação", categoria("Mercado do bairro", "DESPESA"));
    }

    @Test
    void palavraQueApareceAntesVence() {
        assertEquals("Transporte", categoria("Uber para a faculdade", "DESPESA"));
    }

    @Test
    void semPalavraConhecidaVaiParaOutros() {
        assertEquals("Outros", categoria("XPTO 123", "DESPESA"));
        assertEquals("Outras receitas", categoria("XPTO 123", "RECEITA"));
    }

    @Test
    void mostraConfiancaEMarcaParaRevisao() {
        Categorizador.Sugestao certa = Categorizador.sugerir(usuarioId, "Uber trabalho", "DESPESA");
        assertEquals(90, certa.confianca());
        assertFalse(certa.revisar());
        // "Uber" (Transporte) e "faculdade" (Educação): ambígua, abaixo de 70%
        Categorizador.Sugestao ambigua = Categorizador.sugerir(usuarioId, "Uber para a faculdade", "DESPESA");
        assertEquals(60, ambigua.confianca());
        assertTrue(ambigua.revisar());
        assertTrue(Categorizador.sugerir(usuarioId, "XPTO", "DESPESA").revisar());
    }

    @Test
    void reconheceOMesmoEstabelecimentoComNumerosDiferentes() {
        assertEquals("UBER TRIP", Categorizador.padraoDoEstabelecimento("UBER *TRIP 123"));
        assertEquals("UBER TRIP", Categorizador.padraoDoEstabelecimento("Uber *trip 456"));
    }

    @Test
    void sugereRegraSoDepoisDeTresCorrecoesSeguidas() {
        long lazer = idDaCategoria("Lazer");
        Map<String, Object> ultima = null;
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> t = Transacoes.registrar(usuarioId, "Loja do Zé " + i, new BigDecimal("40"), "DESPESA",
                    LocalDate.now(), null);
            assertEquals("Compras", categoriaDe(t), "ainda não aprendeu na correção " + i);
            ultima = Transacoes.trocarCategoria(usuarioId, (Long) t.get("id"), lazer);
            if (i < 3) assertNull(Transacoes.sugestaoDeRegra(usuarioId, ultima));
        }
        Map<String, Object> sugestao = Transacoes.sugestaoDeRegra(usuarioId, ultima);
        assertNotNull(sugestao);
        assertEquals("LOJA DO ZE", sugestao.get("padrao"));

        // Só passa a usar depois que o usuário aceita
        Transacoes.responderRegra(usuarioId, "LOJA DO ZE", lazer, true);
        Map<String, Object> nova = Transacoes.registrar(usuarioId, "Loja do Zé 99", new BigDecimal("25"), "DESPESA",
                LocalDate.now(), null);
        assertEquals("Lazer", categoriaDe(nova));
        assertEquals(98, nova.get("confianca"));
        assertNull(Transacoes.sugestaoDeRegra(usuarioId, ultima)); // já respondida, não pergunta de novo
    }

    // ------------------------------------------------------------ transações e Início

    @Test
    void naoAceitaValorZeroOuSemDescricao() {
        assertThrows(Web.Erro.class, () ->
                Transacoes.registrar(usuarioId, "Teste", BigDecimal.ZERO, "DESPESA", LocalDate.now(), null));
        assertThrows(Web.Erro.class, () ->
                Transacoes.registrar(usuarioId, null, BigDecimal.TEN, "DESPESA", LocalDate.now(), null));
    }

    @Test
    void naoAceitaCategoriaDeReceitaEmDespesa() {
        long salario = idDaCategoria("Salário");
        assertThrows(Web.Erro.class, () ->
                Transacoes.registrar(usuarioId, "Teste", BigDecimal.TEN, "DESPESA", LocalDate.now(), salario));
    }

    @Test
    void resumoCalculaSaldoDoMes() {
        long outro = (Long) Usuarios.cadastrar("Saldo", "saldo@finia.com", "11144477735", "Saldo@123").get("id");
        LocalDate dia = LocalDate.of(2026, 3, 10);
        Transacoes.registrar(outro, "Salário", new BigDecimal("3000.00"), "RECEITA", dia, null);
        Transacoes.registrar(outro, "Aluguel", new BigDecimal("1200.00"), "DESPESA", dia, null);
        Transacoes.registrar(outro, "Uber", new BigDecimal("50.50"), "DESPESA", dia, null);

        Map<String, Object> r = Transacoes.resumo(outro, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "2026-03");
        assertEquals(0, new BigDecimal("3000.00").compareTo(valor(r, "receitas")));
        assertEquals(0, new BigDecimal("1250.50").compareTo(valor(r, "despesas")));
        assertEquals(0, new BigDecimal("1749.50").compareTo(valor(r, "saldo")));
    }

    @Test
    void resumoDeUmPeriodoPersonalizado() {
        long outro = (Long) Usuarios.cadastrar("Periodo", "periodo@finia.com", "39053344705", "Periodo@1").get("id");
        Transacoes.registrar(outro, "Salário", new BigDecimal("1000"), "RECEITA", LocalDate.of(2026, 1, 5), null);
        Transacoes.registrar(outro, "Salário", new BigDecimal("1000"), "RECEITA", LocalDate.of(2026, 2, 5), null);
        Transacoes.registrar(outro, "Salário", new BigDecimal("1000"), "RECEITA", LocalDate.of(2026, 4, 5), null);
        Map<String, Object> r = Transacoes.resumo(outro, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "personalizado");
        assertEquals(0, new BigDecimal("2000").compareTo(valor(r, "receitas")));
    }

    @Test
    void resumoDeMesSemTransacoesDaZero() {
        Map<String, Object> r = Transacoes.resumo(usuarioId, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31), "2020-01");
        assertEquals(0, BigDecimal.ZERO.compareTo(valor(r, "saldo")));
    }

    // ------------------------------------------------------------ metas

    @Test
    void metaCalculaQuantoGuardarPorMes() {
        LocalDate hoje = LocalDate.of(2026, 11, 14), prazo = LocalDate.of(2027, 5, 1);
        BigDecimal alvo = new BigDecimal("3000"), guardado = new BigDecimal("600");
        assertEquals(6, Metas.mesesRestantes(prazo, hoje));
        assertEquals(0, new BigDecimal("400.00").compareTo(Metas.valorPorMes(alvo, guardado, prazo, null, hoje)));
        assertEquals(20, Metas.percentual(alvo, guardado));
    }

    @Test
    void rendimentoDiminuiOQueGuardarPorMes() {
        LocalDate hoje = LocalDate.of(2026, 11, 14), prazo = LocalDate.of(2027, 11, 1);
        BigDecimal alvo = new BigDecimal("12000");
        assertEquals(0, new BigDecimal("1000.00").compareTo(Metas.valorPorMes(alvo, BigDecimal.ZERO, prazo, null, hoje)));
        // 12 meses a 1% ao mês: parcela = 12000 x 0,01 / (1,01^12 - 1) = 946,19
        assertEquals(0, new BigDecimal("946.19").compareTo(
                Metas.valorPorMes(alvo, BigDecimal.ZERO, prazo, BigDecimal.ONE, hoje)));
    }

    @Test
    void guardarDinheiroAtualizaAMeta() {
        Map<String, Object> m = Metas.criar(usuarioId, "Notebook", new BigDecimal("1000"), LocalDate.now().plusMonths(4), null);
        Map<String, Object> depois = Metas.guardar(usuarioId, (Long) m.get("id"), new BigDecimal("1000"));
        assertEquals(true, depois.get("concluida"));
        assertEquals(100, depois.get("percentual"));
    }

    @Test
    void metaPrecisaDePrazoFuturo() {
        assertThrows(Web.Erro.class, () ->
                Metas.criar(usuarioId, "Antiga", new BigDecimal("100"), LocalDate.now().minusDays(1), null));
    }
}
