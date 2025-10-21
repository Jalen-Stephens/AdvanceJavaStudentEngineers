# ---- build app ----
    FROM maven:3.9-eclipse-temurin-17 AS build
    WORKDIR /app
    COPY pom.xml .
    RUN mvn -q -e -DskipTests dependency:go-offline
    COPY src ./src
    RUN mvn -q -DskipTests package
    
    # ---- runtime ----
    FROM eclipse-temurin:17-jre
    # Install curl + jq (jq optional, handy for debugging)
    RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*
    
    # Add c2patool (pin a version)
    ARG C2PA_VERSION=1.6.0
    # x86_64 linux:
    # ADD https://github.com/contentauth/c2patool/releases/download/v${C2PA_VERSION}/c2patool-linux-x64.tar.gz /tmp/c2patool.tar.gz
    # If you deploy to arm64 servers, instead use:
    ADD https://github.com/contentauth/c2patool/releases/download/v${C2PA_VERSION}/c2patool-linux-aarch64.tar.gz /tmp/c2patool.tar.gz
    
    RUN tar -xzf /tmp/c2patool.tar.gz -C /usr/local/bin && \
        chmod +x /usr/local/bin/c2patool && \
        rm -f /tmp/c2patool.tar.gz && \
        /usr/local/bin/c2patool --version
    
    # App
    WORKDIR /app
    COPY --from=build /app/target/*.jar app.jar
    
    # sensible tmp dir
    ENV TMPDIR=/tmp/metadetect
    RUN mkdir -p $TMPDIR
    # (optional) file upload limits for Spring (can also do in application.properties)
    ENV SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=25MB \
        SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=30MB
    
    EXPOSE 8080
    ENTRYPOINT ["java","-jar","/app/app.jar"]
    