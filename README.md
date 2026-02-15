# Icon Pack Generator

Icon Pack Generator is a full-stack application for creating production-ready visual assets with AI:
- icon packs
- vector/SVG exports
- animated GIF icons
- cohesive illustrations
- UI mockups
- branded labels

It combines a Spring Boot backend with a Next.js frontend and a coin-based generation/export model.

## Application Context (Landing Page + Product Positioning)

The app is positioned as an end-to-end design asset workflow, not just a single icon generator. The landing page highlights:
- consistent icon style across full packs
- fast generation cycles
- export-ready outputs (PNG/WebP/ICO/SVG)
- model variations (classic + newest model)
- illustration generation
- animated GIF icon generation
- UI mockup generation
- label generation

From the user perspective: describe a visual direction once, generate assets, iterate with variations, then export in delivery-ready formats.

## Core Flows

### 1) Icon Generation Flow

Frontend mode: `icons`.

Main path:
1. User picks input type (`text` or `image`).
2. User provides general description + optional per-icon descriptions.
3. User chooses model strategy:
   - base generation model: `standard` or `pro`
   - optional additional variation model: `standard` or `pro`
4. Frontend starts streaming generation and subscribes to SSE updates.
5. Backend generates icons, crops the 3x3 grid into separate icons, persists outputs, and returns grouped results.

Important model mapping (from code):
- `standard` => `GptModelService`
- `pro` => `Gpt15ModelService`

Reference-image behavior:
- For icon image-to-image generation, frontend forces base and variation to `pro`, so reference-based icon generation goes through the pro path.

Primary endpoints:
- `POST /generate-stream`
- `GET /stream/{requestId}`
- `GET /status/{requestId}`
- `POST /generate-more`

### 2) SVG Export Flow

Icon export endpoint:
- `POST /export`

What export supports:
- raster outputs: PNG, WebP, ICO (multiple sizes)
- SVG package entries
- optional vectorized SVG pipeline (`vectorized-svg/` in ZIP)
- optional HQ upscale path (adds larger raster size)

Export coin behavior:
- vectorization and HQ upscale are premium options with additional coin costs.

### 3) GIF Icon Making Flow

GIF flow is asynchronous and streamed:
1. User selects generated icons.
2. Backend validates ownership and deducts coins.
3. Each icon is animated (video generation) and converted to GIF.
4. Progress is streamed by SSE.

Endpoints:
- `POST /api/icons/gif/start`
- `GET /api/icons/gif/stream/{gifRequestId}`
- gallery GIF export: `POST /api/gallery/export-gifs`

Cost model in code:
- `2` coins per icon for GIF generation.

### 4) Regular vs Pro Icon Model Flow

This is explicitly supported in dashboard UI and backend orchestration.

Regular model flow:
- uses `GptModelService` (service id `gpt`, UI label `Standard`)

Pro model flow:
- uses `Gpt15ModelService` (service id `gpt15`, UI label `Pro`)

When variations are enabled:
- generation 1 uses selected base model
- generation 2 uses selected variation model

### 5) Illustration Generation Flow

Frontend mode: `illustrations`.

Behavior:
- generates illustration sets (4 by default in UI)
- supports text or reference image input
- uses streaming generation with service grouping

Endpoints:
- `POST /api/illustrations/generate/stream/start`
- `GET /api/illustrations/generate/stream/{requestId}`
- `GET /api/illustrations/generate/status/{requestId}`
- `POST /api/illustrations/generate/more`
- export: `POST /api/illustrations/export`

### 6) UI Mockups Generation Flow

Frontend mode: `mockups`.

Behavior:
- generates UI mockups (single mockup request shape, with variation generation path)
- supports text and reference-image-driven generation
- uses streaming updates, persistence, and export ZIP creation

Endpoints:
- `POST /api/mockups/generate/stream/start`
- `GET /api/mockups/generate/stream/{requestId}`
- `GET /api/mockups/generate/status/{requestId}`
- `POST /api/mockups/generate/more`
- export: `POST /api/mockups/export`

### 7) Label Generation Flow

Frontend mode: `labels`.

Behavior:
- generates style-consistent labels from `labelText` plus theme/reference context
- supports streaming generation and status recovery
- supports optional vectorized export path

Endpoints:
- `POST /api/labels/generate/stream/start`
- `GET /api/labels/generate/stream/{requestId}`
- `GET /api/labels/generate/status/{requestId}`
- export: `POST /api/labels/export`

## Trial and Coins Model

The app uses coin accounting for generation and premium export features.

In current UI/backend behavior:
- base generation typically costs `1` coin
- additional variations increase generation cost
- GIF generation costs `2` coins per selected icon
- premium export options (vectorization/HQ) consume extra coins
- trial coin mode exists; trial generations are watermarked and have storage limitations

## Technical Architecture

### Backend
- Java 21
- Spring Boot 3.5
- Spring Security + OAuth2 login
- Spring Data JPA + PostgreSQL
- Liquibase migrations
- SSE streaming for live generation updates

### Frontend
- Next.js 14 (App Router)
- React 18
- Tailwind CSS
- Dashboard-driven multi-mode generator UI (`icons`, `illustrations`, `mockups`, `labels`)

### AI/Media Services in Project
- `GptModelService` (regular icon model)
- `Gpt15ModelService` (pro icon model)
- `MinimaxVideoModelService` (GIF/video generation pipeline)
- SVG vectorization and raster export services

## Project Structure

- `frontend/` - Next.js frontend
- `src/main/java/...` - Spring Boot backend
- `src/main/resources/application.yaml` - main config
- `static/` - generated asset storage roots
- `docker-compose.yml` - local containerized stack

## Local Development

### Prerequisites
- Java 21+
- Node.js 18+ (for frontend local work)
- PostgreSQL 15+
- API keys for enabled providers

### Key Environment Variables
- `FAL_KEY`
- `OPENAI_API_KEY`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `APP_BASE_URL`
- Stripe variables (if payments enabled)
- SendGrid variables (if email enabled)

See `src/main/resources/application.yaml` and `docker-compose.yml` for complete configuration.

### Run with Docker Compose

```bash
docker compose up --build
```

Default app endpoint from compose:
- `http://localhost:8060`

### Run Backend Locally

```bash
./gradlew bootRun
```

Default backend endpoint:
- `http://localhost:8080`

### Run Frontend Locally (optional separate dev loop)

```bash
cd frontend
npm install
npm run dev
```

## API Docs

OpenAPI/Swagger UI is available at:
- `/swagger-ui.html`

## Notes

- The README intentionally documents product workflows (not only infra), so new contributors understand how users move through generation, iteration, and export.
- If you add a new generation mode or export path, update both the flow section and endpoint list in this file.
