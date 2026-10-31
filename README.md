# service-email

Servidor de emails para PDV.

Serviço de envio de emails (Java 21 + Quarkus). Outros serviços chamam uma API REST e ele entrega
a mensagem via SMTP (Gmail, SendGrid, Amazon SES, etc.) usando a extensão `quarkus-mailer`.

Ele **só envia** email. Não gera tokens de recuperação de senha nem guarda usuários: quem chama
(o backend do site) monta o link e envia aqui. A única exceção é a [página de teste](#página-de-teste-demo),
que cria contas em memória só para exercitar os emails.

## Como o frontend deve usar

Kotlin ou React Native **não devem chamar este serviço direto**. A chave `X-API-Key` ficaria
dentro do app/site, e qualquer pessoa poderia extraí-la e disparar emails em massa. O fluxo
recomendado é:

```
App (Kotlin / React Native) ──► Backend Quarkus do site ──► service-email ──► SMTP
```

O backend do site chama a API abaixo (por exemplo, ao cadastrar um usuário ou pedir recuperação
de senha), e a chave fica só nos servidores.

## Requisitos

- Java 21
- Maven 3.9+
- Docker (opcional, só para o Mailpit local)

## Rodando localmente

```bash
cp .env.example .env     # já vem configurado para o Mailpit
docker compose up -d     # SMTP de mentira em localhost:1025, interface em http://localhost:8025
mvn quarkus:dev
```

Em modo dev o Swagger UI fica em <http://localhost:8080/q/swagger-ui> e o contrato OpenAPI em
`/q/openapi`. Health checks em `/q/health`.

Sem `QUARKUS_MAILER_MOCK=false`, o mailer roda em modo mock em dev e em teste: nada é enviado,
o email só aparece no log.

## Página de teste (`/demo`)

Com o servidor rodando em modo dev, abra <http://localhost:8080/demo>. Ela testa, de ponta a
ponta, os quatro fluxos que mais usam email:

| Aba | O que faz | Email enviado |
|---|---|---|
| Criar conta | Cadastra nome, email e senha | Boas-vindas |
| Login | Confere email e senha | Nenhum |
| Esqueci a senha | Gera um link de uso único (vale 30 min) | Link de redefinição. Ao abrir, aparece a aba "Nova senha" |
| Suporte | Manda uma mensagem à equipe | Para `SUPPORT_EMAIL`, com o visitante em `Reply-To` |

Uma faixa no topo da página mostra se os emails estão em modo **mock** (só aparecem no terminal)
ou saindo por **SMTP** de verdade.

### Testando com Gmail

1. Na conta Google, ative a verificação em duas etapas e crie uma **senha de app**
   (Conta Google > Segurança > Senhas de app). A senha normal não funciona.
2. No `.env`, use o bloco "Opção B: Gmail" do `.env.example` (host `smtp.gmail.com`, porta 587,
   `QUARKUS_MAILER_MOCK=false`, `MAIL_USERNAME`/`MAIL_FROM` com o seu Gmail e `MAIL_PASSWORD` com
   a senha de app). Defina `SUPPORT_EMAIL` com o endereço que deve receber as mensagens de suporte.
3. Rode `mvn quarkus:dev` e abra `/demo`. A faixa do topo deve mostrar `smtp.gmail.com`.
4. Crie uma conta com um email seu e confira a caixa de entrada (e o spam).

O Gmail grava sempre a conta autenticada como remetente (`From`), independente do `MAIL_FROM`.
Por isso a aba Suporte coloca o email de quem escreveu em `Reply-To`.

### O que a demo é e o que não é

É uma ferramenta para exercitar o envio de email. As contas ficam **só na memória** e somem ao
reiniciar, e não há sessão: o "login" apenas confere a senha. As senhas são guardadas com
PBKDF2-HMAC-SHA256 e salt, e o token de redefinição é aleatório, de uso único e guardado só como
hash. Mesmo assim, não é um sistema de autenticação para usar em produção.

A página vem **desligada fora do modo dev** (`/demo` responde 404), porque deixaria qualquer
visitante criar contas e disparar emails pelo seu SMTP. Só ligue de propósito, com
`DEMO_ENABLED=true`, em um ambiente que não seja público.

## Configuração (variáveis de ambiente)

| Variável | Padrão | Descrição |
|---|---|---|
| `MAIL_HOST` | `localhost` | Servidor SMTP |
| `MAIL_PORT` | `587` | Porta SMTP |
| `MAIL_USERNAME` | vazio | Usuário SMTP |
| `MAIL_PASSWORD` | vazio | Senha SMTP |
| `MAIL_FROM` | `no-reply@example.com` | Remetente. Precisa ser aceito pelo provedor |
| `MAIL_START_TLS` | `REQUIRED` | `REQUIRED`, `OPTIONAL` ou `DISABLED` (só para SMTP local) |
| `QUARKUS_MAILER_MOCK` | `true` em dev/teste | `false` para enviar de verdade |
| `APP_NAME` | `Service Email` | Nome do site nos assuntos e templates |
| `EMAIL_API_KEY` | vazio | Se definida, exige o header `X-API-Key` em `/api/**` |
| `DEMO_ENABLED` | `true` em dev/teste, `false` no resto | Liga a página `/demo` e a API `/demo/api/**` |
| `APP_BASE_URL` | `http://localhost:8080` | Endereço da aplicação; base do link de redefinição da demo |
| `SUPPORT_EMAIL` | o valor de `MAIL_FROM` | Quem recebe as mensagens da aba Suporte |
| `PORT` | `8080` | Porta HTTP |

**Em produção, defina sempre `EMAIL_API_KEY`.** Sem ela, qualquer um que alcance o serviço
consegue usá-lo para mandar spam pelo seu SMTP. Gere uma chave com `openssl rand -hex 32`.

### Provedores SMTP

| Provedor | `MAIL_HOST` | `MAIL_PORT` | `MAIL_USERNAME` | `MAIL_PASSWORD` |
|---|---|---|---|---|
| Gmail | `smtp.gmail.com` | `587` | seu email | **senha de app** (exige verificação em duas etapas) |
| SendGrid | `smtp.sendgrid.net` | `587` | `apikey` | a API key do SendGrid |
| Amazon SES | `email-smtp.<região>.amazonaws.com` | `587` | usuário SMTP do SES | senha SMTP do SES |

Com SendGrid e SES, o `MAIL_FROM` precisa ser um remetente/domínio verificado no provedor.
O Gmail limita o volume diário e reescreve o remetente para a conta autenticada, então serve
bem para testes, mas para produção prefira SendGrid ou SES.

## API

Todas as rotas são `POST`, recebem e devolvem JSON. Sucesso devolve `202` com
`{"status":"accepted"}` (o servidor SMTP aceitou a mensagem; a entrega final não é garantida).

| Rota | Corpo | Para quê |
|---|---|---|
| `/api/emails/send` | `to`, `subject`, `text`, `html` (opcional) | Email genérico |
| `/api/emails/welcome` | `to`, `name` | Boas-vindas após cadastro |
| `/api/emails/password-reset` | `to`, `name`, `resetLink` | Recuperação de senha |
| `/api/emails/notification` | `to`, `title`, `message` | Notificação (`title` vira o assunto) |

```bash
curl -X POST http://localhost:8080/api/emails/welcome \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $EMAIL_API_KEY" \
  -d '{"to":"maria@example.com","name":"Maria"}'

curl -X POST http://localhost:8080/api/emails/password-reset \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $EMAIL_API_KEY" \
  -d '{"to":"maria@example.com","name":"Maria","resetLink":"https://meusite.com/redefinir?token=abc123"}'
```

### Erros

| Status | Quando | Corpo |
|---|---|---|
| `400` | Campo ausente/inválido (email malformado, assunto com quebra de linha, link que não é `http(s)`) | Lista de violações do Bean Validation |
| `401` | `X-API-Key` ausente ou errada (quando `EMAIL_API_KEY` está definida) | `{"code":"UNAUTHORIZED","message":"..."}` |
| `502` | O servidor SMTP recusou ou não respondeu | `{"code":"EMAIL_SEND_FAILED","message":"..."}` |

## Templates

Ficam em `src/main/resources/templates/email/` (`welcome.html`, `password-reset.html`,
`notification.html`), em [Qute](https://quarkus.io/guides/qute-reference). Os valores
inseridos (`{name}`, `{message}` etc.) são escapados automaticamente, então HTML enviado
pelo usuário não é interpretado. Para mudar o visual, edite o HTML; para um novo tipo de
email, crie um template e um método em `EmailService`.

## Testes

```bash
mvn test
```

Usam o `MockMailbox` do Quarkus: nenhum email real é enviado. Cobrem envio, validações
(inclusive tentativa de injeção de cabeçalho pelo assunto), escape de HTML, autenticação por
API key e falha de SMTP (502).

## Estrutura

```
src/main/java/br/com/serviceemail/
  api/          EmailResource, DTOs (records com validação), tratamento de erros
  service/      EmailService (renderiza templates e envia), EmailSendException
  security/     ApiKeyFilter (X-API-Key)
src/main/resources/
  application.properties
  templates/email/*.html
```

## Limitações conhecidas

O envio é síncrono e não há fila, retry nem rate limit: se o SMTP falhar, a chamada devolve
`502` e quem chama decide se tenta de novo. Para volume alto, vale colocar uma fila
(por exemplo Kafka ou RabbitMQ) na frente.
