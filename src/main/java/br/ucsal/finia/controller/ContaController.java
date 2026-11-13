package br.ucsal.finia.controller;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.Requisicao;
import br.ucsal.finia.infra.Roteador;
import br.ucsal.finia.model.ContaFinanceira;
import br.ucsal.finia.model.Enums.TipoConta;
import br.ucsal.finia.repository.ContaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Tela de contas e cartões (RF10 / UC10). Desativar/remover apenas interrompe
 * novas sincronizações, preservando o histórico (RN09).
 */
public class ContaController {

    private final ContaRepository contas;

    public ContaController(ContaRepository contas) {
        this.contas = contas;
    }

    public void registrar(Roteador r) {
        r.get("/api/contas", this::listar);
        r.post("/api/contas", req -> salvar(req, null));
        r.put("/api/contas/{id}", req -> salvar(req, req.idRota("id")));
        r.post("/api/contas/{id}/desativar", req -> ativar(req, false));
        r.post("/api/contas/{id}/reativar", req -> ativar(req, true));
        r.post("/api/contas/open-finance", this::conectarOpenFinance);
    }

    private List<Map<String, Object>> listar(Requisicao req) {
        return contas.listar(req.usuario().id()).stream()
                .map(c -> c.paraJson(contas.saldoAtual(c), contas.contarTransacoes(c.id()))).toList();
    }

    private Object salvar(Requisicao req, Long id) {
        String apelido = req.textoObrigatorio("apelido", "apelido");
        String inst = req.textoObrigatorio("instituicao", "instituição");
        TipoConta tipo;
        try {
            tipo = TipoConta.valueOf(req.textoObrigatorio("tipo", "tipo"));
        } catch (IllegalArgumentException e) {
            throw ApiException.requisicaoInvalida("Tipo de conta inválido.");
        }
        BigDecimal saldo = req.decimal("saldoInicial");
        Integer venc = tipo == TipoConta.CARTAO_CREDITO ? req.inteiro("diaVencimento") : null;
        if (tipo == TipoConta.CARTAO_CREDITO && (venc == null || venc < 1 || venc > 31))
            throw ApiException.requisicaoInvalida("Informe o dia de vencimento da fatura (1 a 31).");
        long usuario = req.usuario().id();
        if (id == null) {
            return Map.of("id", contas.inserir(new ContaFinanceira(null, usuario, apelido, inst, tipo,
                    saldo == null ? BigDecimal.ZERO : saldo, venc, true, false)));
        }
        ContaFinanceira atual = contas.buscar(id, usuario).orElseThrow(() -> ApiException.naoEncontrado("Conta não encontrada."));
        contas.atualizar(new ContaFinanceira(id, usuario, apelido, inst, tipo, saldo == null ? atual.saldoInicial() : saldo,
                venc, atual.ativa(), atual.openFinance()));
        return Map.of("id", id);
    }

    private Object ativar(Requisicao req, boolean ativa) {
        long id = req.idRota("id");
        contas.buscar(id, req.usuario().id()).orElseThrow(() -> ApiException.naoEncontrado("Conta não encontrada."));
        contas.definirAtiva(id, req.usuario().id(), ativa);
        return null;
    }

    /**
     * Simula a conexão via Open Finance: exige consentimento explícito com escopo
     * e prazo de validade (RF02). Integração real depende de credenciamento no ecossistema.
     */
    private Object conectarOpenFinance(Requisicao req) {
        if (!req.booleano("consentimento"))
            throw ApiException.requisicaoInvalida("É necessário consentir explicitamente com o compartilhamento de dados.");
        Integer prazo = req.inteiro("prazoMeses");
        if (prazo == null || prazo < 1 || prazo > 12) throw ApiException.requisicaoInvalida("Prazo do consentimento deve ser de 1 a 12 meses.");
        String inst = req.textoObrigatorio("instituicao", "instituição");
        long id = contas.inserir(new ContaFinanceira(null, req.usuario().id(), inst + " (Open Finance)", inst,
                TipoConta.CONTA_CORRENTE, BigDecimal.ZERO, null, true, true));
        return Map.of("id", id, "mensagem", "Consentimento registrado por " + prazo + " meses. A sincronização dos últimos 90 dias foi agendada.");
    }
}
