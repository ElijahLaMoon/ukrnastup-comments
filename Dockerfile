FROM eclipse-temurin:17.0.9_9-jre-alpine
RUN mkdir -p /opt/app /opt/app/data
VOLUME ["/opt/app/data"]
COPY --chown=daemon:daemon comments/target/docker/0/stage /opt/app
EXPOSE 9000
ENTRYPOINT ["\/opt\/app\/bin\/comments"]