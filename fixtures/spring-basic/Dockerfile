FROM eclipse-temurin:21

ENV DOCKER_APP_MODE=prod DOCKER_FEATURE_ENABLED=true
ENV DOCKER_SERVER_PORT 8080
ENV DOCKER_JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
ENTRYPOINT ["java", "-Ddocker.jvm.mode=prod", "-jar", "app.jar"]
CMD ["--docker.cli.mode=worker", "--docker.cli.limit=5"]
