# syntax=docker/dockerfile:1
# =====================================================================
# Gestión Documental - Imagen de la aplicación (Java 25, Spring Boot)
#
# Dos etapas: la primera compila con el JDK y separa el .jar en capas; la segunda
# lleva solo el JRE y las capas. Así la imagen no trae el compilador ni el código
# fuente, y una versión nueva solo reemplaza la capa de la aplicación.
#
# Construir (en el servidor de desarrollo, con salida a internet):
#     docker build -t gestion-documental:$(git rev-parse --short HEAD) .
#
# Las pruebas no corren aquí: corren antes, en el PR (.\mvnw.cmd test). Por eso -DskipTests.
# Antes del piloto conviene fijar las imágenes base por su digest (@sha256:...).
# =====================================================================

ARG IMAGEN_JDK=eclipse-temurin:25-jdk
ARG IMAGEN_JRE=eclipse-temurin:25-jre

# ---------- 1. Compilación ----------
FROM ${IMAGEN_JDK} AS construccion
WORKDIR /fuente

# Primero solo lo que define las dependencias: si no cambia, Docker reutiliza esta capa
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
 && cp target/gestion-documental-*.jar /tmp/aplicacion.jar \
 && java -Djarmode=tools -jar /tmp/aplicacion.jar extract --layers --launcher --destination /capas

# ---------- 2. Ejecución ----------
FROM ${IMAGEN_JRE}

# El mismo número de usuario que tiene la carpeta de PDF en el servidor (ver infra/rocky/01-preparar-host.sh)
ARG UID_APP=10001
RUN groupadd --system --gid ${UID_APP} gestion \
 && useradd --system --uid ${UID_APP} --gid ${UID_APP} --no-create-home --shell /usr/sbin/nologin gestion \
 && install -d -o gestion -g gestion -m 750 /var/lib/gestion-documental/pdf

WORKDIR /app
COPY --from=construccion /capas/dependencies/ ./
COPY --from=construccion /capas/spring-boot-loader/ ./
COPY --from=construccion /capas/snapshot-dependencies/ ./
COPY --from=construccion /capas/application/ ./

# La aplicación guarda fechas con la hora local (LocalDateTime) y cuenta días hábiles:
# tiene que correr en hora de Asunción aunque el servidor esté en UTC.
ENV TZ=America/Asuncion \
    APP_UPLOAD_DIR=/var/lib/gestion-documental/pdf \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Duser.timezone=America/Asuncion -Djava.security.egd=file:/dev/urandom"

USER ${UID_APP}:${UID_APP}
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
