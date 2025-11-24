FROM openjdk:17.0.2-slim-buster

RUN apt-get update && apt-get install -y ffmpeg

COPY build/libs/*SNAPSHOT.jar /app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
