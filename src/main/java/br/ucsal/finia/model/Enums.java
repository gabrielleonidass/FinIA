package br.ucsal.finia.model;

/**
 * Enumerações do domínio, derivadas dos diagramas de estados da
 * documentação do FinIA (Figuras 15 e 16).
 */
public final class Enums {

    private Enums() {
    }

    /** Estados da conta de usuário (RF01). */
    public enum StatusUsuario { PENDENTE, ATIVA, SUSPENSA }

    /** Tipos de conta financeira (RF10). */
    public enum TipoConta {
        CONTA_CORRENTE("Conta corrente"), POUPANCA("Poupança"), CARTAO_CREDITO("Cartão de crédito");
        public final String rotulo;
        TipoConta(String r) { rotulo = r; }
    }

    public enum TipoTransacao { RECEITA, DESPESA }

    /** Estados da transação (RF02-RF04). */
    public enum StatusTransacao { CATEGORIZADA, INCERTA, RECLASSIFICADA }

    public enum OrigemTransacao { CSV, OFX, MANUAL, OPEN_FINANCE }

    /** Estados de meta financeira (RF05). */
    public enum StatusMeta { ATIVA, EM_RISCO, PAUSADA, CONCLUIDA, CANCELADA }

    /** Situação do orçamento por categoria (RF09 / RN08). */
    public enum SituacaoOrcamento { DENTRO_DO_LIMITE, EM_ALERTA, ESTOURADO }

    /** Tipos de alerta (RF08). */
    public enum TipoAlerta {
        GASTO_ATIPICO("Gasto atípico"),
        ORCAMENTO_EM_ALERTA("Orçamento atingiu 80%"),
        ORCAMENTO_ESTOURADO("Orçamento estourado"),
        VENCIMENTO_FATURA("Vencimento de fatura"),
        META_EM_RISCO("Meta em risco");
        public final String rotulo;
        TipoAlerta(String r) { rotulo = r; }
    }

    public enum CanalAlerta { PUSH, EMAIL, AMBOS }

    /** Permissões do membro no grupo familiar (RF12). */
    public enum PermissaoMembro { VISUALIZAR, EDITAR }

    public enum StatusMembro { CONVIDADO, ATIVO }
}
