# DevPilot

DevPilot is a full-stack **RAG (Retrieval-Augmented Generation) codebase assistant**. Connect your GitHub account, sync repositories, index source code into vector embeddings, and chat with your codebase using AI-powered answers with file citations.

## Features

- **GitHub OAuth login** — sign in with GitHub; access tokens stored encrypted
- **Repository sync** — fetch and persist repositories owned by the signed-in GitHub account (not collaborator or organization-only repositories)
- **Async code indexing** — chunk source files, embed with Gemini, store in pgvector
- **RAG chat** — ask questions about your repo with context-aware answers
- **SSE streaming** — token-by-token responses in the chat UI
- **Citations** — answers link back to relevant source files
- **Dashboard** — overview, repository management, settings
- **Course material chat** — import PDF/PPT/PPTX files, group them into courses and units, and ask questions grounded in indexed material
- **GU LMS helper extension** — discover and download supported file links visible on the current GU LMS page without asking DevPilot for university credentials

## Architecture

```text
┌─────────────┐     REST + SSE      ┌──────────────────┐     GitHub API
│  Next.js    │ ◄─────────────────► │  Spring Boot     │ ◄──────────────► GitHub
│  (client)   │   session cookie    │  (backend)       │
└─────────────┘                     └────────┬─────────┘
                                             │
                         ┌───────────────────┼───────────────────┐
                         ▼                   ▼                   ▼
                   PostgreSQL            pgvector              Gemini
                   (app data)         (embeddings)      (chat + embeddings)
```

### RAG pipeline

1. **Index** — fetch repo files from GitHub → chunk → embed → store in pgvector
2. **Retrieve** — embed the user's question → similarity search for top-K code chunks
3. **Generate** — build prompts with retrieved context → stream Gemini reply

Learning materials use the same pgvector store and Gemini embedding model as
repository indexing, but are filtered by a separate course/unit metadata scope.
This prevents course documents from appearing in GitHub repository searches.

## Tech stack

| Layer | Technologies |
|---|---|
| Frontend | Next.js 16, React 19, TanStack Query, Tailwind CSS 4, shadcn/ui, Streamdown |
| Backend | Spring Boot 4.1, Java 25, Spring Security OAuth2, Spring AI 2.0 |
| Database | PostgreSQL 16 + pgvector (Docker) |
| Migrations | Flyway |
| AI | Gemini `gemini-3.8-flash` (chat), `gemini-embedding-001` (embeddings) |

## Prerequisites

- **Java 25+**
- **Node.js 20+**
- **Docker Desktop** (for PostgreSQL)
- **GitHub account** + OAuth App
- **Google Gemini API key** ([create one](https://aistudio.google.com/app/apikey))

## Project structure

```text
devPilot/
├── backend/          # Spring Boot API
├── client/           # Next.js frontend
├── docker/           # Postgres init scripts
└── docker-compose.yml
```

## Getting started

### 1. Clone and start the database

```bash
git clone <your-repo-url>
cd devPilot
docker compose up -d
```

Postgres runs on **host port `5433`** with database `devpilot`.

Verify:

```bash
docker compose exec postgres psql -U postgres -d devpilot -c "SELECT 1;"
```

### 2. Create a GitHub OAuth App

Go to [GitHub Developer Settings → OAuth Apps](https://github.com/settings/developers) and create a new app:

| Field | Value |
|---|---|
| Homepage URL | `http://localhost:3000` |
| Authorization callback URL | `http://localhost:8080/login/oauth2/code/github` |

Note the **Client ID** and **Client Secret**.

### 3. Configure the backend

Copy `backend/.env.example` to `backend/.env` (this file is gitignored) and fill in your credentials:

```env
GEMINI_API_KEY=your-gemini-api-key
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
```

Alternatively, set these as environment variables. Never commit API keys or OAuth secrets.

```bash
export GEMINI_API_KEY=your-gemini-api-key
export GITHUB_CLIENT_ID=your-github-client-id
export GITHUB_CLIENT_SECRET=your-github-client-secret
```

### 4. Configure the frontend

Create `client/.env.local`:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
```

### 5. Run the backend

```bash
cd backend
./mvnw spring-boot:run        # Linux / macOS
.\mvnw.cmd spring-boot:run    # Windows
```

Backend starts at **http://localhost:8080**.

### 6. Run the frontend

```bash
cd client
npm install
npm run dev
```

Frontend starts at **http://localhost:3000**.

### 7. Use the app

1. Open **http://localhost:3000**
2. Click **Continue with GitHub**
3. Go to **Dashboard → Repositories**
4. Sync your owned repositories, then click **Index** on a repository
5. Wait until status is **READY**
6. Open **Chat** and ask questions about your codebase

For class materials, open **My courses**, download a PDF or PowerPoint from GU
LMS, upload it with a course and unit name, and wait for indexing to complete.
You can chat with one unit or with all indexed units in a course.

### GU LMS browser helper (optional)

The unpacked Manifest V3 extension in `extension/` supports Chrome and Edge:

1. Open `chrome://extensions` or `edge://extensions` and enable **Developer mode**.
2. Choose **Load unpacked** and select the `devPilot/extension` folder.
3. In Chrome, open the **Extensions** puzzle-piece menu and click the pin
   beside **DevPilot GU Course File Helper**. Chrome may hide an unpinned
   extension's toolbar button; the popup opens from that pinned toolbar button.
4. Sign in directly at
   [GU LMS login](https://gulms.galgotiasuniversity.org/login/index.php), open
   [My courses](https://gulms.galgotiasuniversity.org/my/courses.php), then open
   the course page containing the materials. Course pages may have URLs such as
   `https://gulms.galgotiasuniversity.org/course/view.php?id=25623`; the course
   ID is part of the LMS page URL, and the helper scans the page currently open.
5. Click the pinned **DevPilot GU Course File Helper** toolbar button, select
   **Scan this page**, and choose the file links to download. Scanning runs only
   after you press the button; it supports direct PDF/PPT/PPTX links and Moodle
   resource links such as `/mod/resource/view.php?id=...`.
6. In DevPilot, open **Dashboard → My courses**, enter the GU course name,
   select the downloaded files together, and choose **Upload and index**. Each
   file becomes a separately indexed unit under one course card; open that card
   to check indexing and chat with the course materials.

If the toolbar button is missing, open the puzzle-piece menu and pin the helper.
If the popup still does not open, visit `chrome://extensions`, confirm the
helper is enabled and loaded from the `devPilot/extension` folder, then select
**Reload** on its card and pin it again. Check Chrome's **Errors** on that
extension card if it still fails. If Chrome does not scan the page, confirm the
helper has site access to `gulms.galgotiasuniversity.org` and reload the LMS tab.
After updating the helper, click **Reload** on its extension card so Chrome
applies its updated permissions.

The helper only inspects visible links on the active
`gulms.galgotiasuniversity.org` page when you request a scan. It does not read
passwords, store LMS cookies, crawl courses, or automatically navigate.

## Environment variables

### Backend

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/devpilot` | Postgres JDBC URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `GEMINI_API_KEY` | — | Google Gemini API key (required for chat and indexing) |
| `GITHUB_CLIENT_ID` | — | GitHub OAuth client ID |
| `GITHUB_CLIENT_SECRET` | — | GitHub OAuth client secret |
| `FRONTEND_URL` | `http://localhost:3000` | OAuth redirect target |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Allowed CORS origins |
| `APP_UPLOAD_MAX_FILE_SIZE` | `25MB` | Maximum one course material upload |
| `APP_UPLOAD_MAX_REQUEST_SIZE` | `26MB` | Maximum multipart request size |
| `APP_LEARNING_MAX_FILE_BYTES` | `26214400` | Backend upload size check in bytes |

### Frontend

| Variable | Default | Description |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | Backend API base URL |

## API overview

All `/api/**` routes require authentication (session cookie), except where noted.

### Auth

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/auth/me` | Current user |
| `GET` | `/api/auth/login-url` | GitHub OAuth URL |
| `POST` | `/api/auth/logout` | End session |
| `GET` | `/oauth2/authorization/github` | Start GitHub login |

### Repositories

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/repos?refresh=true` | Sync from GitHub and list repos |
| `GET` | `/api/repos/{id}` | Get one repo |
| `POST` | `/api/repos/{id}/index` | Start indexing (async) |
| `GET` | `/api/repos/{id}/status` | Indexing progress |

### Chat

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/chat/sessions` | Create chat session |
| `GET` | `/api/chat/sessions?repositoryId=` | List sessions |
| `GET` | `/api/chat/sessions/{id}` | Get message history |
| `POST` | `/api/chat/sessions/{id}/messages` | Send message (SSE stream) |

### Learning materials

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/learning/courses` | List the signed-in user's courses |
| `GET` | `/api/learning/courses/{id}` | Get course units and indexing status |
| `DELETE` | `/api/learning/courses/{id}` | Delete a course, its units/chats, and indexed vectors |
| `DELETE` | `/api/learning/courses/{courseId}/units/{unitId}` | Delete one unit, its unit chat sessions, and indexed vectors |
| `POST` | `/api/learning/units` | Upload and index a PDF/PPT/PPTX (multipart: `courseName`, `unitName`, `file`) |
| `POST` | `/api/chat/course-sessions` | Create a course- or unit-scoped chat |
| `GET` | `/api/chat/course-sessions?courseId=` | List chats for a course (optional `unitId`) |

## Development

### Backend

```bash
cd backend
./mvnw test
./mvnw compile
```

- Schema migrations live in `backend/src/main/resources/db/migration/`
- JPA uses `ddl-auto=validate` — Flyway owns the schema
- Vector store table is auto-created by Spring AI pgvector
- Gemini embeddings use a separate `gemini_vector_store` table (768 dimensions); existing OpenAI vectors are left untouched. Re-index repositories to populate Gemini embeddings.

### Frontend

```bash
cd client
npm run lint
npm run build
```

### Common issues

**`Unable to determine Dialect` / HikariCP connection error**

Postgres is not running. Start it first:

```bash
docker compose up -d
```

**OAuth redirect fails**

Ensure the GitHub OAuth callback URL is exactly:

```text
http://localhost:8080/login/oauth2/code/github
```

**Chat returns "Repository must be indexed"**

Wait for indexing to finish (`indexStatus: READY`) before creating a chat session.

## Security notes

- Never commit `.env`, `application-local.properties`, or API keys
- GitHub access tokens are encrypted at rest before saving to the database
- DevPilot never asks for or stores GU LMS usernames/passwords; the helper uses
  the student's existing browser session only to download a file they select
- Extracted document text is sent to the configured Gemini API for embeddings
  and answers, and vectors are stored in the configured pgvector database
- Learning uploads are restricted to PDF/PPT/PPTX and 25 MB; indexing failures
  are shown on the unit card and logged by the backend
- Session cookies are HttpOnly; CSRF is disabled for the SPA + cookie session pattern
- Use strong values for `TOKEN_ENCRYPTOR_PASSWORD` and `TOKEN_ENCRYPTOR_SALT` in production

## License

This project is for educational purposes.
