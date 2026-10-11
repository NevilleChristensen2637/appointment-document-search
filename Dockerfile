FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
ENV INFRAI_API_KEY=""
CMD ["sh", "-c", "echo build with your JVM tooling"]
