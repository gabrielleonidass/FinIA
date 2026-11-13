# API REST do FinIA

Todas as rotas (exceto `auth`) exigem o cookie de sessão `FINIA_SESSAO`. Corpo e respostas em JSON.

| Método | Rota | Descrição | RF |
|---|---|---|---|
| POST | /api/auth/cadastro | Cria conta (pendente de confirmação) | RF01 |
| POST | /api/auth/confirmar | Confirma o e-mail pelo token | RF01 |
| POST | /api/auth/login | Autentica e cria a sessão | RF01 |
| POST | /api/auth/logout | Encerra a sessão | RF01 |
| GET | /api/auth/eu | Usuário logado e alertas não lidos | – |
| GET | /api/dashboard?periodo=mes\|3m\|12m\|custom | Visão consolidada | RF07 |
| GET | /api/categorias | Categorias | RF03 |
| GET | /api/transacoes | Lista com filtros (periodo, conta, categoria, status, busca) | RF02 |
| POST | /api/transacoes | Lançamento manual (categorizado automaticamente) | RF03 |
| POST | /api/transacoes/importar | Importa CSV/OFX | RF02 |
| PUT | /api/transacoes/{id}/categoria | Reclassifica | RF04 |
| POST | /api/transacoes/{id}/confirmar | Confirma categoria sugerida | RF04 |
| GET/POST/DELETE | /api/regras | Regras automáticas e histórico | RF04 |
| GET/POST/PUT | /api/contas | Contas e cartões | RF10 |
| POST | /api/contas/{id}/desativar \| reativar | Desativa/reativa preservando histórico | RF10 |
| POST | /api/contas/open-finance | Registra consentimento Open Finance | RF02 |
| GET/POST/PUT | /api/metas | Metas | RF05 |
| POST | /api/metas/simular | Calcula o aporte mensal | RF05 |
| POST | /api/metas/{id}/aporte \| status | Aporte, pausar, retomar, cancelar | RF05 |
| GET/POST/DELETE | /api/orcamentos | Limites por categoria | RF09 |
| POST | /api/simulacoes | Cenário "E se" (somente leitura) | RF06 |
| GET | /api/alertas | Alertas e preferências | RF08 |
| POST | /api/alertas/{id}/lido, /api/alertas/lidos | Marca como lido | RF08 |
| PUT | /api/alertas/configuracoes | Liga/desliga tipo e canal | RF08 |
| GET | /api/relatorios?tipo=&referencia= | Dados do relatório | RF11 |
| GET | /api/relatorios/exportar?formato=pdf\|xlsx | Download | RF11 |
| GET/POST | /api/familia | Grupo familiar | RF12 |
| POST/PUT/DELETE | /api/familia/membros[/{id}] | Convidar, permissão, remover | RF12 |
