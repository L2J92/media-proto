# 1) 프론트엔드 빌드
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) 백엔드 빌드 (프론트 결과물을 static 으로 포함)
FROM gradle:8.14-jdk21 AS api
WORKDIR /app
COPY backend/build.gradle backend/settings.gradle ./
RUN gradle dependencies --no-daemon -q > /dev/null 2>&1 || true
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN gradle bootJar --no-daemon -x test

# 3) 실행
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=api /app/build/libs/*.jar app.jar
# Render 무료 인스턴스(512MB)에 맞춘 메모리 설정
ENV JAVA_OPTS="-Xmx300m -Xss512k -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
