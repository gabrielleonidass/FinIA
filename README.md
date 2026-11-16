# FinIA – Controle de finanças pessoais com categorização automática

Projeto acadêmico (UCSAL, 2026) feito a partir da documentação de requisitos do FinIA.

**Autores:** Gabriel Oliveira Leônidas e Tiago Aragão · **Professor:** Elton Figueiredo da Silva
Back-end em **Java 17**, front-end em **HTML, CSS e JavaScript** e banco de dados **H2** integrado.

## O que o sistema faz

**Telas com back-end completo** (leem e gravam no banco de dados):

| Tela | O que dá para fazer |
|---|---|
| **Login** | Entrar com e-mail e senha |
| **Cadastro** | Criar conta com nome, e-mail, CPF e senha (senha forte, CPF válido, e-mail e CPF únicos) |
| **Início** | Ver receitas, despesas e saldo do mês, dos últimos 3 ou 12 meses ou de um período personalizado, gráfico de gastos por categoria, últimas transações e metas |
| **Transações** | Registrar receitas e despesas; a **IA escolhe a categoria** pela descrição e mostra a confiança (abaixo de 70% pede revisão); corrigir a categoria; depois de 3 correções seguidas do mesmo estabelecimento, o sistema pergunta se deve criar uma regra; excluir |
| **Metas** | Criar metas com valor, prazo e rendimento esperado (opcional), ver quanto guardar por mês e ir guardando dinheiro |

**Telas de protótipo** (front-end completo, com uma faixa amarela indicando que são protótipo):

| Tela | Requisito | Como funciona |
|---|---|---|
| **Importar extrato** | RF02 | Lê CSV (com escolha de colunas) e OFX no navegador, aponta a linha e o motivo de cada erro, ignora duplicadas e salva as válidas pela API de transações; Open Finance ilustrativo, com consentimento |
| **Contas e cartões** | RF10 | Contas de exemplo; criar, renomear, desativar (pausa a sincronização), remover (mantém o histórico) e pagar fatura como transferência (só na tela) |
| **Orçamentos** | RF09 | Orçamento individual ou do grupo familiar; gastos reais do banco; limites guardados no navegador, valem todo mês e não acumulam; alerta em 80% e estouro em 100% |
| **Simulação "E se"** | RF06 | Corte de gastos sobre o período escolhido (mês atual por padrão) e renda extra; "Aplicar cenário" cria um orçamento com o novo limite |
| **Alertas** | RF08 | Alertas de exemplo, preferências por tipo e canal e as regras de cálculo (gasto 200% acima da média de 3 meses, fatura 3 dias antes) |
| **Relatórios** | RF11 | Relatórios mensal, trimestral e anual com números reais, comparação com o período anterior, PDF pela impressão e Excel (.xlsx) |
| **Grupo familiar** | RF12 | Membros de exemplo, convite com aceite, permissões item por item e contas compartilhadas escolhidas por cada membro (só na tela) |

## Como executar

Precisa ter instalado: **Java 17 ou mais novo** e **Maven**.

```bash
mvn package                  # compila, roda os testes e gera target/finia.jar
java -jar target/finia.jar   # liga o sistema
```

Abra **http://localhost:8080** e entre com a conta de teste:

- e-mail: `demo@finia.com`
- senha: `Demo@1234`

Deixe o terminal aberto enquanto usa. Para desligar, aperte **Ctrl+C**.

Na primeira execução o banco `data/finia_simples.mv.db` é criado com dados de exemplo
(dois meses de transações e três metas). Para começar do zero, apague a pasta `data`.

## Como o código está organizado

São 7 classes Java, todas na mesma pasta (`src/main/java/br/ucsal/finia/`). Cada tela tem a sua classe,
com as rotas, as regras e o SQL juntos:

| Classe | O que faz |
|---|---|
| `FinIA.java` | liga o sistema: cria o banco, os dados de exemplo e as rotas |
| `Usuarios.java` | Login e Cadastro: senha forte, CPF válido, hash da senha |
| `Transacoes.java` | Transações (cadastrar, corrigir categoria, regra após 3 correções) e o resumo da tela Início |
| `Categorizador.java` | a "IA" que escolhe a categoria e calcula a confiança |
| `Metas.java` | Metas: criar, guardar dinheiro e calcular quanto guardar por mês |
| `Web.java` | servidor web: entrega as telas, chama as rotas, confere o login e responde em JSON |
| `Banco.java` | conexão com o H2 e os comandos SQL (cada linha do resultado vira um mapa coluna → valor) |

```
src/main/resources/
├── schema.sql      criação das 5 tabelas
└── static/         as 12 telas (HTML, CSS e JavaScript)
```

O caminho de um pedido: a tela chama, por exemplo, `GET /api/metas` → `Web` confere o login e acha a rota →
`Metas` busca no `Banco` → a resposta volta em JSON para a tela.

![Arquitetura](docs/arquitetura.png)

## Como a "IA" escolhe a categoria

1. **Regra do usuário:** depois de 3 correções seguidas do mesmo estabelecimento, o sistema pergunta se deve
   criar uma regra. Com a regra aceita, usa a escolha do usuário (confiança de 98%).
2. **Palavras-chave:** procura palavras conhecidas (ex.: `UBER` → Transporte, `IFOOD` → Alimentação).
   Vence a que aparece primeiro na descrição; empatando, a mais longa (`MERCADO LIVRE` ganha de `MERCADO`).
   Confiança de 90%, ou 60% se a descrição combina com categorias diferentes.
3. **Nada encontrado:** vai para *Outros* (despesa) ou *Outras receitas* (receita), com 30%.

Abaixo de 70% de confiança a transação aparece com o selo **Revisar**.

O código está em `Categorizador.java`.

## Banco de dados

5 tabelas: `usuario`, `categoria`, `transacao`, `meta` e `regra_categoria` (ver `src/main/resources/schema.sql` e `docs/der.png`).

![DER](docs/der.png)

Para olhar o banco por dentro com o sistema ligado, abra outro terminal na pasta do projeto e rode
`java -cp target/finia.jar org.h2.tools.Console`. Na tela do H2 use:

- JDBC URL: `jdbc:h2:file:./data/finia_simples;AUTO_SERVER=TRUE`
- User Name: `sa` · Password: (vazio)

## Testes

`mvn test` roda 20 testes JUnit 5 com banco H2 em memória: cadastro (CPF e senha), login, categorização automática
e confiança, regra criada depois de 3 correções, validações das transações, resumo do mês e de um período e cálculo
das metas (com e sem rendimento).

O limite mínimo de confiança da IA começa em 70% e pode ser ajustado sem mudar o código, pela variável de ambiente
`FINIA_LIMIAR_CONFIANCA` (ex.: `FINIA_LIMIAR_CONFIANCA=80`).

## Documentação

- `docs/historias-de-usuario.md` – histórias de usuário com critérios de aceitação
- `docs/api.md` – rotas da API
- `docs/roteiro-entrevistas.md` – roteiro das entrevistas com usuários
- Trello: https://trello.com/b/NL43b6Hb/finia-processo-de-desenvolvimento
