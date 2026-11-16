# API do FinIA

As telas enviam os dados como formulário (`campo=valor&campo=valor`) e todas as rotas devolvem JSON. As rotas marcadas com 🔒 exigem login (cookie `FINIA_SESSAO`).
Em caso de erro a resposta é `{"erro": "mensagem"}` com o código HTTP correspondente (400, 401, 404 ou 409).

## Login e cadastro

| Método | Rota | Corpo | O que faz |
|---|---|---|---|
| POST | `/api/auth/cadastro` | `{nome, email, senha}` | Cria a conta e já faz o login |
| POST | `/api/auth/login` | `{email, senha}` | Entra no sistema |
| POST | `/api/auth/sair` | – | Sai do sistema |
| GET 🔒 | `/api/auth/eu` | – | Dados do usuário logado |

## Início

| Método | Rota | O que faz |
|---|---|---|
| GET 🔒 | `/api/inicio?mes=2026-11` | Receitas, despesas, saldo, gastos por categoria, últimas transações e metas |

## Transações

| Método | Rota | Corpo | O que faz |
|---|---|---|---|
| GET 🔒 | `/api/categorias` | – | Lista as categorias |
| GET 🔒 | `/api/transacoes?mes=2026-11` | – | Transações do mês |
| GET 🔒 | `/api/transacoes/sugestao?descricao=Uber&tipo=DESPESA` | – | Mostra a categoria que a IA escolheria |
| POST 🔒 | `/api/transacoes` | `{descricao, valor, tipo, data, categoriaId?}` | Cadastra (sem `categoriaId` a IA escolhe) |
| PUT 🔒 | `/api/transacoes/{id}/categoria` | `{categoriaId}` | Corrige a categoria (a IA aprende) |
| DELETE 🔒 | `/api/transacoes/{id}` | – | Exclui |

## Metas

| Método | Rota | Corpo | O que faz |
|---|---|---|---|
| GET 🔒 | `/api/metas` | – | Lista as metas com %, quanto falta e valor por mês |
| POST 🔒 | `/api/metas` | `{nome, valorAlvo, prazo}` | Cria uma meta |
| POST 🔒 | `/api/metas/{id}/guardar` | `{valor}` | Soma um valor ao que já foi guardado |
| DELETE 🔒 | `/api/metas/{id}` | – | Exclui |
