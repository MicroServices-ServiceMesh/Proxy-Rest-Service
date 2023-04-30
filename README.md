# Proxy-Rest-Service
Service that acts as an proxy to a REST Service

# Build and run the project
```
./mvnw clean compile quarkus:dev

```
# Open a new terminal (Unix/Linux/MacOs) and execute this command
```
curl -X GET http://localhost:8080/v1/Patient/591984

```

# Swagger UI 
Swagger UI is available at [http://localhost:8080/q/camel/openapi.json/](http://localhost:8080/q/camel/openapi.json/)
