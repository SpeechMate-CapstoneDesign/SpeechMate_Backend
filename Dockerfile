FROM amazoncorretto:17.0.17-alpine3.22

RUN apk add --no-cache ffmpeg


COPY build/libs/*SNAPSHOT.jar /app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
