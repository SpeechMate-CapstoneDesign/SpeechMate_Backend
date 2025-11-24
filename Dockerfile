FROM eclipse-temurin:17-jdk-slim

RUN apt-get update && apt-get install -y ffmpeg

COPY build/libs/*SNAPSHOT.jar /app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
