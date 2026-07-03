# Base image: a JDK, nothing else. We don't need Maven or Gradle images
# since this project intentionally compiles with plain javac.
FROM eclipse-temurin:21-jdk

# All app files live here inside the container.
WORKDIR /app

# Copy everything (source files + the public/ frontend folder) into
# the image. .dockerignore keeps .env, .git, and .class files out.
COPY . .

# Compile all Java source files the same way you do locally.
RUN javac *.java

# Documents which port the app listens on by default. Render overrides
# this at runtime via the PORT environment variable, which Server.java
# already reads (see main()).
EXPOSE 8080

# Start the server. OPENAQ_API_KEY is supplied as an environment
# variable through Render's dashboard, not from a .env file.
CMD ["java", "Server"]