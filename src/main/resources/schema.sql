-- =====================================================================
-- FinIA - Script de criação do banco de dados (H2, compatível com MySQL)
-- Executado automaticamente na inicialização (CREATE ... IF NOT EXISTS).
-- =====================================================================

CREATE TABLE IF NOT EXISTS usuario (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome               VARCHAR(120) NOT NULL,
    email              VARCHAR(150) NOT NULL UNIQUE,
    cpf                CHAR(11)     NOT NULL UNIQUE,
    senha_hash         VARCHAR(128) NOT NULL,
    senha_salt         VARCHAR(64)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,          -- PENDENTE | ATIVA | SUSPENSA
    token_confirmacao  VARCHAR(64),
    prazo_confirmacao  TIMESTAMP,
    criado_em          TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS categoria (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome     VARCHAR(60) NOT NULL UNIQUE,
    cor      CHAR(7)     NOT NULL,
    receita  BOOLEAN     NOT NULL
);

CREATE TABLE IF NOT EXISTS conta_financeira (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id      BIGINT        NOT NULL,
    apelido         VARCHAR(60)   NOT NULL,
    instituicao     VARCHAR(80)   NOT NULL,
    tipo            VARCHAR(20)   NOT NULL,            -- CONTA_CORRENTE | POUPANCA | CARTAO_CREDITO
    saldo_inicial   DECIMAL(14,2) NOT NULL,
    dia_vencimento  INT,
    ativa           BOOLEAN       NOT NULL,
    open_finance    BOOLEAN       NOT NULL,
    criada_em       TIMESTAMP     NOT NULL,
    CONSTRAINT fk_conta_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

CREATE TABLE IF NOT EXISTS transacao (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id    BIGINT        NOT NULL,
    conta_id      BIGINT        NOT NULL,
    data          DATE          NOT NULL,
    descricao     VARCHAR(200)  NOT NULL,
    valor         DECIMAL(14,2) NOT NULL,
    tipo          VARCHAR(10)   NOT NULL,              -- RECEITA | DESPESA
    categoria_id  BIGINT,
    confianca     DOUBLE        NOT NULL,
    status        VARCHAR(20)   NOT NULL,              -- CATEGORIZADA | INCERTA | RECLASSIFICADA
    origem        VARCHAR(20)   NOT NULL,              -- CSV | OFX | MANUAL | OPEN_FINANCE
    criada_em     TIMESTAMP     NOT NULL,
    CONSTRAINT fk_transacao_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario(id),
    CONSTRAINT fk_transacao_conta     FOREIGN KEY (conta_id)     REFERENCES conta_financeira(id),
    CONSTRAINT fk_transacao_categoria FOREIGN KEY (categoria_id) REFERENCES categoria(id)
);
CREATE INDEX IF NOT EXISTS idx_transacao_usuario_data ON transacao(usuario_id, data);

CREATE TABLE IF NOT EXISTS reclassificacao (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    transacao_id           BIGINT       NOT NULL,
    usuario_id             BIGINT       NOT NULL,
    categoria_anterior_id  BIGINT,
    categoria_nova_id      BIGINT       NOT NULL,
    padrao                 VARCHAR(120) NOT NULL,
    data_hora              TIMESTAMP    NOT NULL,
    CONSTRAINT fk_reclass_transacao FOREIGN KEY (transacao_id) REFERENCES transacao(id),
    CONSTRAINT fk_reclass_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario(id),
    CONSTRAINT fk_reclass_cat_ant   FOREIGN KEY (categoria_anterior_id) REFERENCES categoria(id),
    CONSTRAINT fk_reclass_cat_nova  FOREIGN KEY (categoria_nova_id)     REFERENCES categoria(id)
);

CREATE TABLE IF NOT EXISTS regra_categorizacao (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id    BIGINT       NOT NULL,
    padrao        VARCHAR(120) NOT NULL,
    categoria_id  BIGINT       NOT NULL,
    criada_em     TIMESTAMP    NOT NULL,
    CONSTRAINT uk_regra UNIQUE (usuario_id, padrao),
    CONSTRAINT fk_regra_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario(id),
    CONSTRAINT fk_regra_categoria FOREIGN KEY (categoria_id) REFERENCES categoria(id)
);

CREATE TABLE IF NOT EXISTS meta (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id              BIGINT        NOT NULL,
    nome                    VARCHAR(80)   NOT NULL,
    valor_alvo              DECIMAL(14,2) NOT NULL,
    valor_acumulado         DECIMAL(14,2) NOT NULL,
    data_limite             DATE          NOT NULL,
    taxa_rendimento_mensal  DECIMAL(6,3),
    status                  VARCHAR(20)   NOT NULL,    -- ATIVA | EM_RISCO | PAUSADA | CONCLUIDA | CANCELADA
    criada_em               TIMESTAMP     NOT NULL,
    CONSTRAINT fk_meta_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

CREATE TABLE IF NOT EXISTS orcamento (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id      BIGINT        NOT NULL,
    categoria_id    BIGINT        NOT NULL,
    limite          DECIMAL(14,2) NOT NULL,
    mes_referencia  CHAR(7)       NOT NULL,            -- AAAA-MM
    CONSTRAINT uk_orcamento UNIQUE (usuario_id, categoria_id, mes_referencia),
    CONSTRAINT fk_orc_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario(id),
    CONSTRAINT fk_orc_categoria FOREIGN KEY (categoria_id) REFERENCES categoria(id)
);

CREATE TABLE IF NOT EXISTS alerta (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL,
    tipo        VARCHAR(30)  NOT NULL,
    mensagem    VARCHAR(300) NOT NULL,
    lido        BOOLEAN      NOT NULL,
    criado_em   TIMESTAMP    NOT NULL,
    CONSTRAINT fk_alerta_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

CREATE TABLE IF NOT EXISTS configuracao_alerta (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL,
    tipo        VARCHAR(30) NOT NULL,
    ativo       BOOLEAN     NOT NULL,
    canal       VARCHAR(10) NOT NULL,                  -- PUSH | EMAIL | AMBOS
    CONSTRAINT uk_config_alerta UNIQUE (usuario_id, tipo),
    CONSTRAINT fk_config_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);

CREATE TABLE IF NOT EXISTS grupo_familiar (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome        VARCHAR(80) NOT NULL,
    admin_id    BIGINT      NOT NULL,
    criado_em   TIMESTAMP   NOT NULL,
    CONSTRAINT fk_grupo_admin FOREIGN KEY (admin_id) REFERENCES usuario(id)
);

CREATE TABLE IF NOT EXISTS membro_grupo (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    grupo_id    BIGINT       NOT NULL,
    nome        VARCHAR(120) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    permissao   VARCHAR(20)  NOT NULL,                 -- VISUALIZAR | EDITAR
    status      VARCHAR(20)  NOT NULL,                 -- CONVIDADO | ATIVO
    convidado_em TIMESTAMP   NOT NULL,
    CONSTRAINT uk_membro UNIQUE (grupo_id, email),
    CONSTRAINT fk_membro_grupo FOREIGN KEY (grupo_id) REFERENCES grupo_familiar(id)
);
