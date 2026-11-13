package br.ucsal.finia;

import br.ucsal.finia.model.Enums.SituacaoOrcamento;
import br.ucsal.finia.service.MetaService;
import br.ucsal.finia.service.OrcamentoService;
import br.ucsal.finia.util.CpfValidador;
import br.ucsal.finia.util.SenhaUtil;
import br.ucsal.finia.util.Texto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Testes unitários das regras de negócio puras (critérios de aceitação da ERS). */
class RegrasDeNegocioTest {

    @Test
    void rf01_cpfValidoEInvalido() {
        assertTrue(CpfValidador.valido("529.982.247-25"));
        assertTrue(CpfValidador.valido("11144477735"));
        assertFalse(CpfValidador.valido("123.456.789-00"), "dígito verificador errado");
        assertFalse(CpfValidador.valido("111.111.111-11"), "dígitos repetidos");
        assertFalse(CpfValidador.valido("5299822"), "formato incompleto");
    }

    @Test
    void rf01_politicaDeSenha() {
        assertTrue(SenhaUtil.atendePolitica("Senha@2026"));
        assertFalse(SenhaUtil.atendePolitica("senha@2026"), "sem maiúscula");
        assertFalse(SenhaUtil.atendePolitica("Senha2026"), "sem caractere especial");
        assertFalse(SenhaUtil.atendePolitica("Se@1"), "menos de 8 caracteres");
        String salt = SenhaUtil.novoSalt();
        assertTrue(SenhaUtil.confere("Senha@2026", salt, SenhaUtil.hash("Senha@2026", salt)));
        assertFalse(SenhaUtil.confere("Outra@2026", salt, SenhaUtil.hash("Senha@2026", salt)));
    }

    @Test
    void rf05_metaDe12MilEm12MesesSemRendimentoExigeMilPorMes() {
        BigDecimal aporte = MetaService.aporteMensal(new BigDecimal("12000"), BigDecimal.ZERO, 12, null);
        assertEquals(new BigDecimal("1000.00"), aporte);
    }

    @Test
    void rf05_rendimentoReduzOAporte() {
        BigDecimal sem = MetaService.aporteMensal(new BigDecimal("12000"), BigDecimal.ZERO, 12, null);
        BigDecimal com = MetaService.aporteMensal(new BigDecimal("12000"), BigDecimal.ZERO, 12, new BigDecimal("1.0"));
        assertTrue(com.compareTo(sem) < 0);
        assertEquals(Integer.valueOf(12), MetaService.mesesParaAtingir(new BigDecimal("12000"), BigDecimal.ZERO, com, new BigDecimal("1.0")));
    }

    @Test
    void rf09_situacaoDoOrcamento() {
        BigDecimal limite = new BigDecimal("800");
        assertEquals(SituacaoOrcamento.ESTOURADO, OrcamentoService.situacao(new BigDecimal("850"), limite));
        assertEquals(SituacaoOrcamento.EM_ALERTA, OrcamentoService.situacao(new BigDecimal("640"), limite));
        assertEquals(SituacaoOrcamento.EM_ALERTA, OrcamentoService.situacao(new BigDecimal("800"), limite));
        assertEquals(SituacaoOrcamento.DENTRO_DO_LIMITE, OrcamentoService.situacao(new BigDecimal("639.99"), limite));
    }

    @Test
    void rf04_padraoDoEstabelecimento() {
        assertEquals("UBER TRIP", Texto.padraoEstabelecimento("UBER *TRIP 8821"));
        assertEquals("JOSEMARIA SILVA", Texto.padraoEstabelecimento("PAG*JOSEMARIA SILVA"));
        assertEquals("BAR JUAREZ", Texto.padraoEstabelecimento("BAR DO JUAREZ RIO VERMELHO"));
    }
}
