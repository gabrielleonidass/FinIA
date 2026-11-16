-- =====================================================================
-- FinIA - Banco de dados (5 tabelas)
-- Executado automaticamente quando o sistema inicia.
-- =====================================================================

-- Pessoas que usam o sistema
CREATE TABLE IF NOT EXISTS usuario (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    senha_hash  VARCHAR(64)  NOT NULL, -- senha nunca é salva em texto puro
    senha_salt  VARCHAR(32)  NOT NULL,
    cpf         VARCHAR(11),               -- só números; obrigatório no cadastro (índice único abaixo)
    criado_em   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- Categorias de gasto/ganho (Alimentação, Transporte, Salário...)
CREATE TABLE IF NOT EXISTS categoria (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome  VARCHAR(50) NOT NULL UNIQUE,
    tipo  VARCHAR(10) NOT NULL, -- RECEITA ou DESPESA
    cor   VARCHAR(7)  NOT NULL  -- cor usada no gráfico
);

-- Cada entrada ou saída de dinheiro
CREATE TABLE IF NOT EXISTS transacao (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id            BIGINT        NOT NULL,
    categoria_id          BIGINT        NOT NULL,
    descricao             VARCHAR(150)  NOT NULL,
    valor                 DECIMAL(12,2) NOT NULL,
    tipo                  VARCHAR(10)   NOT NULL, -- RECEITA ou DESPESA
    data                  DATE          NOT NULL,
    categoria_automatica  BOOLEAN       NOT NULL, -- true = escolhida pela IA
    confianca             INT,                    -- confiança da IA (0 a 100); vazio = escolhida pelo usuário
    CONSTRAINT fk_transacao_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_transacao_categoria FOREIGN KEY (categoria_id) REFERENCES categoria(id)
);

-- Metas de economia (ex.: reserva de emergência, viagem)
CREATE TABLE IF NOT EXISTS meta (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id      BIGINT        NOT NULL,
    nome            VARCHAR(100)  NOT NULL,
    valor_alvo      DECIMAL(12,2) NOT NULL,
    valor_guardado  DECIMAL(12,2) NOT NULL DEFAULT 0,
    prazo           DATE          NOT NULL,
    taxa_rendimento DECIMAL(6,3),             -- rendimento esperado em % ao mês (opcional)
    CONSTRAINT fk_meta_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
);

-- Regras de categoria criadas pelo usuário (RF04): depois de 3 correções seguidas
-- do mesmo estabelecimento, o sistema pergunta se deve criar a regra.
-- aceita = FALSE guarda que o usuário recusou, para não perguntar de novo.
CREATE TABLE IF NOT EXISTS regra_categoria (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id    BIGINT       NOT NULL,
    padrao        VARCHAR(150) NOT NULL, -- estabelecimento sem números (ex.: UBER TRIP)
    categoria_id  BIGINT       NOT NULL,
    aceita        BOOLEAN      NOT NULL,
    criada_em     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_regra UNIQUE (usuario_id, padrao),
    CONSTRAINT fk_regra_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_regra_categoria FOREIGN KEY (categoria_id) REFERENCES categoria(id)
);

-- Atualiza bancos criados antes destas colunas existirem
ALTER TABLE usuario   ADD COLUMN IF NOT EXISTS cpf VARCHAR(11);
ALTER TABLE transacao ADD COLUMN IF NOT EXISTS confianca INT;
ALTER TABLE meta      ADD COLUMN IF NOT EXISTS taxa_rendimento DECIMAL(6,3);
CREATE UNIQUE INDEX IF NOT EXISTS ux_usuario_cpf ON usuario(cpf);
