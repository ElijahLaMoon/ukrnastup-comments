FROM eclipse-temurin:17.0.9_9-jre-alpine
RUN mkdir -p /opt/app/data
VOLUME ["/opt/app/data"]
COPY --chown=daemon:daemon comments/target/universal/stage /opt/app
WORKDIR /opt/app 
EXPOSE 9000
ENTRYPOINT ["\/opt\/app\/bin\/comments"]