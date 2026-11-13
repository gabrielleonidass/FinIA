# Histórias de usuário – FinIA

Cada história deriva de um requisito funcional da documentação de transição (RF01–RF12). Pontos em escala Fibonacci.

| ID | História | RF | Prioridade | Pontos | Sprint |
|---|---|---|---|---|---|
| US01 | Cadastro e login | RF01 | Alta | 5 | 1 |
| US02 | Importar extratos CSV/OFX e conectar Open Finance | RF02 | Alta | 8 | 1 |
| US03 | Categorização automática | RF03 | Alta | 8 | 2 |
| US04 | Reclassificar e aprender | RF04 | Alta | 5 | 2 |
| US05 | Metas com simulação de aporte | RF05 | Alta | 5 | 2 |
| US06 | Simulação "E se" | RF06 | Média | 3 | 3 |
| US07 | Dashboard consolidado | RF07 | Alta | 5 | 1 |
| US08 | Alertas inteligentes | RF08 | Média | 5 | 3 |
| US09 | Orçamento por categoria | RF09 | Alta | 3 | 2 |
| US10 | Contas e cartões | RF10 | Média | 3 | 1 |
| US11 | Relatórios PDF e XLSX | RF11 | Média | 5 | 3 |
| US12 | Grupo familiar com permissões | RF12 | Média | 5 | 3 |

**Total:** 60 pontos em 3 sprints.

## US01 – Cadastro e login (RF01)

**Como** usuário individual, **quero** criar uma conta com nome, e-mail, CPF e senha e entrar com minhas credenciais, **para** acessar minhas finanças com segurança.

**Critérios de aceitação**

- CPF inválido (formato ou dígito verificador) é rejeitado com mensagem específica
- Senha exige 8+ caracteres, maiúscula, número e caractere especial
- Conta fica pendente até a confirmação do e-mail; sem confirmação em 24 h é suspensa
- Com cadastro completo e e-mail confirmado, o login funciona

**Tarefas**

- [x] Tela de login e cadastro
- [x] Validador de CPF
- [x] Hash PBKDF2 da senha
- [x] Sessão por cookie HttpOnly
- [x] Link de confirmação

## US02 – Importar extratos CSV/OFX e conectar Open Finance (RF02)

**Como** usuário individual, **quero** importar extratos CSV/OFX ou conectar minha conta via Open Finance, **para** não precisar digitar cada gasto.

**Critérios de aceitação**

- Arquivo fora do padrão é rejeitado informando a linha e o motivo
- Transações com mesma data, valor e descrição não são importadas de novo
- Conexão Open Finance só ocorre com consentimento explícito, escopo e prazo definidos

**Tarefas**

- [x] Leitor CSV
- [x] Leitor OFX
- [x] Detecção de duplicadas
- [x] Modal de consentimento Open Finance

## US03 – Categorização automática (RF03)

**Como** usuário individual, **quero** que cada transação importada já venha com uma categoria sugerida, **para** economizar tempo na organização dos gastos.

**Critérios de aceitação**

- "UBER TRIP" é classificada como Transporte com a confiança exibida
- Confiança abaixo de 70% marca a transação como incerta e a destaca para revisão
- Correções anteriores do usuário têm peso maior na classificação

**Tarefas**

- [x] Modelo de palavras-chave ponderadas
- [x] Cálculo de confiança
- [x] Destaque visual de incertas

## US04 – Reclassificar e aprender (RF04)

**Como** usuário individual, **quero** corrigir a categoria de uma transação, **para** que o sistema aprenda com minhas correções.

**Critérios de aceitação**

- Cada correção é registrada com data, categoria anterior e nova
- A nova categoria aparece imediatamente no dashboard e nos relatórios
- Três correções seguidas do mesmo estabelecimento sugerem uma regra permanente

**Tarefas**

- [x] Tabela de auditoria
- [x] Sugestão de regra
- [x] Tela de regras e histórico

## US05 – Metas com simulação de aporte (RF05)

**Como** usuário individual, **quero** cadastrar metas com valor-alvo e prazo, **para** saber quanto preciso guardar por mês.

**Critérios de aceitação**

- Meta de R$ 12.000,00 em 12 meses sem rendimento indica aporte de R$ 1.000,00
- Projeção é recalculada quando o padrão de gastos muda; meta vira "em risco" se não couber
- Meta pode ser pausada, editada ou cancelada

**Tarefas**

- [x] Cálculo de aporte com e sem juros
- [x] Status ativa/em risco/pausada/concluída
- [x] Tela de metas

## US06 – Simulação "E se" (RF06)

**Como** usuário individual, **quero** simular a redução de gastos em uma categoria, **para** ver o impacto nas minhas metas antes de decidir.

**Critérios de aceitação**

- Simulação de -20% em uma categoria recalcula a data de conclusão de cada meta
- Comparação lado a lado entre cenário atual e hipotético
- Nenhum dado real é alterado

**Tarefas**

- [x] Serviço de simulação
- [x] Tela comparativa

## US07 – Dashboard consolidado (RF07)

**Como** usuário individual ou familiar, **quero** ver saldo, receitas, despesas, gastos por categoria e metas em um painel, **para** entender minha situação financeira em segundos.

**Critérios de aceitação**

- Com três contas, mostra o saldo total consolidado e o detalhe por conta
- Filtro por mês atual, 3 meses, 12 meses e período customizado

**Tarefas**

- [x] Endpoint de resumo
- [x] Gráficos Chart.js
- [x] Filtro de período

## US08 – Alertas inteligentes (RF08)

**Como** usuário individual, **quero** receber alertas de gastos atípicos, orçamento, faturas e metas, **para** agir antes de perder o controle.

**Critérios de aceitação**

- Gasto 250% acima da média da categoria gera alerta imediato
- Fatura avisada com 3 dias de antecedência
- Tipo de alerta desligado não gera notificação; canal push, e-mail ou ambos

**Tarefas**

- [x] Regras de alerta
- [x] Central de alertas
- [x] Preferências por tipo e canal

## US09 – Orçamento por categoria (RF09)

**Como** usuário individual, **quero** definir limites mensais por categoria, **para** controlar meus gastos durante o mês.

**Critérios de aceitação**

- Ao atingir 80% a categoria fica em alerta
- Orçamento de R$ 800,00 com gasto de R$ 850,00 aparece como excedido

**Tarefas**

- [x] CRUD de limites
- [x] Barra de progresso com cores
- [x] Alerta de 80% e 100%

## US10 – Contas e cartões (RF10)

**Como** usuário individual, **quero** cadastrar, editar e desativar contas e cartões com apelido, **para** organizar de onde vem cada transação.

**Critérios de aceitação**

- Cartão desativado não recebe novas transações e preserva o histórico
- Cada conta tem apelido customizável exibido nos relatórios

**Tarefas**

- [x] CRUD de contas
- [x] Saldo por conta
- [x] Desativar/reativar

## US11 – Relatórios PDF e XLSX (RF11)

**Como** usuário individual ou consultor, **quero** exportar relatórios mensais, trimestrais e anuais, **para** analisar ou compartilhar meus números.

**Critérios de aceitação**

- Relatório tem saldo, gastos por categoria, comparativo com o período anterior e metas
- Relatório anual traz todos os meses com totais corretos
- Exportação em PDF e XLSX

**Tarefas**

- [x] Gerador de PDF
- [x] Gerador de XLSX
- [x] Tela de relatórios

## US12 – Grupo familiar com permissões (RF12)

**Como** administrador do grupo familiar, **quero** convidar membros e definir se podem só ver ou também editar, **para** compartilhar o planejamento da casa com segurança.

**Critérios de aceitação**

- Somente o administrador convida, remove e altera permissões
- Membro só acessa após aceitar o convite
- Membro com permissão de visualizar não consegue editar orçamento ou meta

**Tarefas**

- [x] Tabelas de grupo e membros
- [x] Convite e aceite
- [x] Tela de permissões
