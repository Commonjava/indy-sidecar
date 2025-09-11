# Indy Sidecar

A Quarkus-based sidecar service that optimizes build performance by pre-downloading and caching dependency artifacts from previous builds. Deployed within builder pods, it proxies download/upload requests to remote repositories while serving cached artifacts locally to accelerate builds.

## Architecture

- **Proxy Service**: Routes HTTP requests (GET/POST/PUT/DELETE/HEAD) to configured remote services
- **Archive Retrieval**: Downloads artifact zip files from the external Archive Service for each build config
- **Tracking Service**: External service that records artifact information sent by the sidecar

## Workflow

1. **Archive Generation**: After a build completes, users trigger artifact zip file generation via REST API to the Archive Service
2. **Zip Download**: The sidecar downloads the latest artifact zip file for the current build from the Archive Service
3. **Local Caching**: Downloaded artifacts are cached locally and served directly to current build
4. **Proxy Fallback**: For artifacts not in cache (new dependencies), requests are proxied to remote repositories
5. **Records Collection**: All download/upload operations are sent to the external Tracking Service for recording and observability

## Configuration

Configure via `application.yaml`:

```yaml
sidecar:
  archive-api: http://localhost:8081/api/archive
  local-repository: ${user.home}/preSeedRepo

proxy:
  read-timeout: 30m
  retry:
    count: 3
    interval: 3000
  services:
    - host: your-repo-host
      port: 80
      path-pattern: /api/.+
```

## Build & Deploy

**JVM Image:**
```bash
./mvnw clean package -Dquarkus.container-image.build=true
```

**Native Executable:**
```bash
./mvnw package -Pnative -Dquarkus.native.container-build=true
docker build -f src/main/docker/Dockerfile.native -t indy-sidecar:native .
```

**Pre-built Images:**
- `quay.io/kaine/indy-sidecar:latest` (JVM)
- `quay.io/kaine/indy-sidecar:native-latest` (Native)
