# Indy sidecar

## build image

**JVM image**

```shell
./mvnw clean package -Dquarkus.container-image.build=true
```

use `src/main/resources/application.properties` to control image build plugin, by default, indy-sidecar use docker as builder.

---

**Native executable**


```shell
./mvnw package -Pnative -Dquarkus.native.container-build=true
docker build -f src/main/docker/Dockerfile.native -t indy-sidecar:native .
```


Our build: `quay.io/kaine/indy-sidecar` tag:`latest` `native-latest`