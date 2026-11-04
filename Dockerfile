# Build em duas etapas: o JDK compila, e só o JRE (mais o jar) vai para a imagem final.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app
# as dependências antes do código: a camada só é refeita quando o pom muda
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src src
# os testes rodam no CI e em ./mvnw verify; a imagem só empacota
RUN ./mvnw -B -ntp -o -DskipTests package && cp target/room-booking-api-*.jar app.jar

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=build /app/app.jar app.jar
# sem root dentro do contêiner (UID numérico: o Kubernetes consegue verificar runAsNonRoot)
USER 10001:10001
# 8080: a API. 8081: Actuator (sondas e métricas), que NÃO deve ser publicada para fora.
EXPOSE 8080 8081
# percentual da memória do contêiner (e não do host) para o heap
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
