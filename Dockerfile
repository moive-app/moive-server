# ==============================================
# 1단계: 빌드 스테이지
# JDK가 필요한 빌드 작업만 여기서 수행
# ==============================================
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# 의존성 캐시 활용을 위해 소스보다 gradle 파일 먼저 복사
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .
COPY src/ src/

RUN chmod +x gradlew

# CI/CD 파이프라인에서 이미 테스트를 완료했으므로 -x test로 생략
RUN ./gradlew bootJar -x test

# ==============================================
# 2단계: 실행 스테이지
# 빌드 결과물(JAR)만 가져와 JRE 경량 이미지로 실행
# JDK 없이 JRE만 써서 이미지 크기 최소화
# ==============================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080

# /dev/./urandom: SecureRandom 초기화 지연 방지 (Spring Boot 기동 속도 개선)
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
