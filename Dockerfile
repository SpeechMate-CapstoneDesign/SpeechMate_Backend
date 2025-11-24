FROM amazoncorretto:17.0.17-alpine3.22

RUN apt-get update && apt-get install -y ffmpeg

COPY build/libs/*SNAPSHOT.jar /app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
