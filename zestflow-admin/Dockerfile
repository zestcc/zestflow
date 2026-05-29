FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="zestflow"

WORKDIR /app

COPY target/zestflow-admin-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=${SPRING_PROFILE:prod}"]
