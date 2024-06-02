FROM openjdk:11
EXPOSE 8088
ADD target/*.jar app.jar
ENV JAVA_OPTS="-Xss:16m"
ENTRYPOINT ["sh", "-c", "java -jar /app.jar"]