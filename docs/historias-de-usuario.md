# Histórias de usuário – FinIA

Esta versão do FinIA implementa 7 histórias de usuário, derivadas dos requisitos funcionais da documentação
de transição (RF01, RF02, RF03, RF04, RF05 e RF07). Pontos em escala Fibonacci.

| ID | História | RF | Tela | Prioridade | Pontos | Sprint | Status |
|---|---|---|---|---|---|---|---|
| US01 | Criar conta | RF01 | Cadastro | Alta | 3 | 1 | Concluída |
| US02 | Entrar e sair do sistema | RF01 | Login | Alta | 2 | 1 | Concluída |
| US03 | Registrar receitas e despesas | RF02 | Transações | Alta | 3 | 1 | Concluída |
| US04 | Categorização automática (IA) | RF03 | Transações | Alta | 5 | 2 | Concluída |
| US05 | Corrigir categoria e o sistema aprender | RF04 | Transações | Alta | 3 | 2 | Concluída |
| US06 | Resumo do mês (tela Início) | RF07 | Início | Alta | 5 | 2 | Concluída |
| US07 | Metas de economia | RF05 | Metas | Média | 5 | 3 | Concluída |

**Total:** 26 pontos em 3 sprints.

## US01 – Criar conta (RF01)

**Como** pessoa que quer organizar o dinheiro, **quero** criar uma conta com nome, e-mail e senha, **para** ter meus dados guardados só para mim.

**Critérios de aceitação**

- Nome, e-mail e senha são obrigatórios
- E-mail já cadastrado é recusado com a mensagem "Este e-mail já está cadastrado"
- Senha precisa ter 8+ caracteres, letra maiúscula, número e símbolo
- A senha é salva com hash PBKDF2 + salt, nunca em texto puro
- Depois de criar a conta, a pessoa já entra no sistema

**Tarefas**

- [x] Tela de cadastro
- [x] Tabela usuario
- [x] UsuarioService.cadastrar
- [x] Testes de senha fraca e e-mail repetido

## US02 – Entrar e sair do sistema (RF01)

**Como** usuário cadastrado, **quero** entrar com e-mail e senha e sair quando terminar, **para** acessar minhas finanças com segurança.

**Critérios de aceitação**

- E-mail ou senha errados mostram "E-mail ou senha incorretos"
- A sessão fica num cookie HttpOnly e expira após 2 horas sem uso
- Quem não está logado e abre uma tela interna volta para o login
- O botão Sair encerra a sessão

**Tarefas**

- [x] Tela de login
- [x] Sessões com cookie
- [x] Proteção das rotas da API

## US03 – Registrar receitas e despesas (RF02)

**Como** usuário, **quero** registrar o que entrou e o que saiu, com descrição, valor, tipo e data, **para** saber para onde vai o meu dinheiro.

**Critérios de aceitação**

- Descrição e data são obrigatórias e o valor precisa ser maior que zero
- A lista mostra as transações do mês escolhido, da mais nova para a mais antiga
- Receitas aparecem em verde com "+" e despesas em laranja com "-"
- É possível excluir uma transação

**Tarefas**

- [x] Tela de transações
- [x] Tabela transacao
- [x] TransacaoService
- [x] Filtro por mês

## US04 – Categorização automática (IA) (RF03)

**Como** usuário, **quero** que a categoria de cada transação seja escolhida sozinha pela descrição, **para** não perder tempo organizando gasto por gasto.

**Critérios de aceitação**

- "Uber" vira Transporte, "iFood" vira Alimentação, "Netflix" vira Lazer
- Se duas palavras combinam, vence a que aparece primeiro; empatando, a mais longa ("Mercado Livre" = Compras)
- Sem palavra conhecida, vai para "Outros" (despesa) ou "Outras receitas" (receita)
- Enquanto digita, a tela mostra qual categoria a IA vai escolher e por quê
- Transações classificadas pela IA aparecem com o selo "IA"

**Tarefas**

- [x] CategorizadorService
- [x] Endpoint de sugestão
- [x] Dica na tela
- [x] Testes de palavras-chave

## US05 – Corrigir categoria e o sistema aprender (RF04)

**Como** usuário, **quero** trocar a categoria quando a IA errar, **para** que o sistema acerte da próxima vez.

**Critérios de aceitação**

- A categoria pode ser trocada direto na lista de transações
- Só aparecem categorias do mesmo tipo (receita ou despesa)
- Depois da correção, uma nova transação com a mesma descrição já vem na categoria escolhida
- A correção aparece na hora no resumo e no gráfico

**Tarefas**

- [x] PUT /api/transacoes/{id}/categoria
- [x] Consulta da última correção
- [x] Teste de aprendizado

## US06 – Resumo do mês (tela Início) (RF07)

**Como** usuário, **quero** ver num só lugar quanto recebi, quanto gastei e o saldo do mês, **para** entender rápido como estão minhas finanças.

**Critérios de aceitação**

- Mostra receitas, despesas e saldo do mês (saldo negativo em laranja)
- Gráfico de rosca com os gastos por categoria
- Mostra as 5 últimas transações e o progresso das metas
- É possível escolher outro mês

**Tarefas**

- [x] Tela Início
- [x] InicioService
- [x] Consultas SUM e GROUP BY
- [x] Gráfico com Chart.js

## US07 – Metas de economia (RF05)

**Como** usuário, **quero** criar metas com valor e prazo e ir guardando dinheiro nelas, **para** saber quanto preciso juntar por mês.

**Critérios de aceitação**

- Nome, valor maior que zero e prazo futuro são obrigatórios
- Mostra a % concluída, quanto falta e quanto guardar por mês até o prazo
- Ex.: faltando R$ 2.400,00 em 6 meses, sugere R$ 400,00 por mês
- Ao chegar em 100% a meta aparece como "Concluída"
- É possível excluir uma meta

**Tarefas**

- [x] Tela de metas
- [x] Tabela meta
- [x] MetaService
- [x] Cálculo do valor por mês

## Backlog (próximas versões)

Requisitos da documentação que não fazem parte desta entrega:

- **RF02** – Importar extratos CSV/OFX e conectar via Open Finance
- **RF06** – Simulação "E se" (cortar gastos e ver o efeito nas metas)
- **RF08** – Alertas de gasto atípico, orçamento e vencimento de fatura
- **RF09** – Orçamento (limite de gasto) por categoria
- **RF10** – Cadastro de contas bancárias e cartões
- **RF11** – Relatórios com exportação em PDF e Excel
- **RF12** – Grupo familiar com permissões
