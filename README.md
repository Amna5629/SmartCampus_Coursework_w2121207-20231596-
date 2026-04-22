# Smart Campus Sensor & Room Management API - 5COSC022W Client-ServerArchitectures-Coursework(2025/26)

## API Design Overview

This project is a RESTful API built using JAX-RS (Jersey 2.32) deployed on Apache Tomcat. It simulates a university Smart Campus system that manages Rooms and Sensors across campus buildings.

The API follows REST principles with a logical resource hierarchy that mirrors the physical structure of the campus, rooms contain sensors, and sensors maintain a historical log of readings.

- **Base URL:** 'http://localhost:8080/smart-campus-api/api/v1'
- **Technology:** JAX-RS (Jersey 2.32), Apache Tomcat 9, Maven
- **Data Storage:** In-memory Java 'HashMap' and 'ArrayList' , no database used
- **Error Handling:** Custom exceptions with `ExceptionMapper` classes returning structured JSON
- **Logging:** `ContainerRequestFilter` and `ContainerResponseFilter` for request/response observability

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/` | Discovery — returns API metadata and resource links |
| GET | `/api/v1/rooms` | Get all rooms |
| POST | `/api/v1/rooms` | Create a new room |
| GET | `/api/v1/rooms/{roomId}` | Get a specific room by ID |
| DELETE | `/api/v1/rooms/{roomId}` | Delete a room (blocked if sensors are assigned) |
| GET | `/api/v1/sensors` | Get all sensors (supports `?type=` filter) |
| POST | `/api/v1/sensors` | Register a new sensor (validates roomId exists) |
| GET | `/api/v1/sensors/{sensorId}` | Get a specific sensor by ID |
| GET | `/api/v1/sensors/{sensorId}/readings` | Get all readings for a sensor |
| POST | `/api/v1/sensors/{sensorId}/readings` | Add a new reading (blocked if sensor is MAINTENANCE) |

---

## Build and Run

1. Open Apache NetBeans.
2. Go to **File > Open Project** and select the `smart-campus-api` folder.
3. In the **Services** tab, make sure Apache Tomcat 9 is configured and running.
4. Right-click the project and select **Clean and Build**.
5. Right-click the project and select **Run**.
6. The API will be available at: `http://localhost:8080/smart-campus-api/api/v1/`
7. Test using Postman or the curl commands below.

---

## Sample curl Commands

**1) Discovery**
```bash
curl -X GET http://localhost:8080/smart-campus-api/api/v1/
```

**2) Create Room**
```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"LIB-301\",\"name\":\"Library Quiet Study\",\"capacity\":80}"
```

**3) Create Sensor**
```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"CO2-001\",\"type\":\"CO2\",\"status\":\"ACTIVE\",\"currentValue\":400.0,\"roomId\":\"LIB-301\"}"
```

**4) Filter Sensors by Type**
```bash
curl -X GET "http://localhost:8080/smart-campus-api/api/v1/sensors?type=CO2"
```

**5) Add Sensor Reading**
```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/CO2-001/readings \
  -H "Content-Type: application/json" \
  -d "{\"value\":415.2}"
```

**6) Get Sensor Readings**
```bash
curl -X GET http://localhost:8080/smart-campus-api/api/v1/sensors/CO2-001/readings
```

---

## Report Answers

### Part 1 — Service Architecture & Setup

**Q1: Explain the default lifecycle of a JAX-RS Resource class. Is a new instance created per request or treated as a singleton? How does this impact in-memory data management?**

By default, JAX-RS uses a per-request lifecycle, which means a new instance of each resource class such as `RoomResource`, `SensorResource`, and `SensorReadingResource` is created for every incoming HTTP request. This means instance variables are not shared between requests.

In this project, this means we cannot store data inside resource class fields. Instead, all shared data is stored in a `MockDatabase` class using static collections such as `HashMap<String, Room>`, `HashMap<String, Sensor>`, and `HashMap<String, List<SensorReading>>`. Since these are static, they are shared across all requests, no matter how many resource instances are created.

However, `HashMap` and `ArrayList` are not thread-safe. In a system with multiple requests at the same time, two requests updating the same data can cause data corruption. A better solution would be to use `ConcurrentHashMap` and `Collections.synchronizedList()`, or use a real database with proper transaction handling.

**Q2: Why is Hypermedia (HATEOAS) considered a hallmark of advanced RESTful design? How does it benefit client developers compared to static documentation?**

HATEOAS (Hypermedia as the Engine of Application State) is a principle where API responses include links that guide the client to related actions and resources. Instead of relying on external documentation, the client can discover what to do next directly from the response.

In this project, the Discovery endpoint at `GET /api/v1/` returns the API version, admin contact details, and a map of available resources such as `"rooms"` mapped to `"/api/v1/rooms"` and `"sensors"` mapped to `"/api/v1/sensors"`. When a client first accesses the API, it can identify all available resources without needing separate documentation.

The main advantage is that the API becomes self-describing. If an endpoint changes in a future version, clients that follow these links will still function correctly instead of breaking due to hardcoded URLs. This makes the API easier to maintain and more flexible to evolve.

---

### Part 2 — Room Management

**Q1: What are the implications of returning only IDs versus returning full room objects in a list response?**

Returning only IDs produces smaller responses that use less network bandwidth and are faster to send. However, the client then needs to make separate GET requests for each room ID to get full details, which increases the number of HTTP calls and adds latency.

Returning full room objects provides all information in a single request, reducing the need for multiple calls. The disadvantage is that the response size becomes larger, which can be inefficient when dealing with a large number of rooms.

In this implementation, `GET /api/v1/rooms` returns full `Room` objects including `id`, `name`, `capacity`, and `sensorIds`. This is suitable for a campus management system because users such as facility managers usually need complete information rather than only IDs.

**Q2: Is the DELETE operation idempotent in your implementation? Justify with what happens on repeated calls.**

Yes, DELETE is idempotent in this implementation. Idempotency means that sending the same request multiple times results in the same final state on the server.

In `RoomResource.deleteRoom()`, the first DELETE request to `/api/v1/rooms/LIB-301` checks whether the room exists and has no assigned sensors. It then removes the room from `MockDatabase.ROOMS` and returns `204 No Content`. If the same DELETE request is sent again, the room is no longer in the HashMap, so the method returns `404 Not Found`.

However, the final server state remains the same — the room is already deleted — and no additional changes occur. Therefore, the operation is idempotent even though the response codes differ.

---

### Part 3 — Sensor Operations & Linking

**Q1: What are the technical consequences if a client sends data in a format other than JSON to the POST /sensors endpoint?**

The `addSensor()` method in `SensorResource` is annotated with `@Consumes(MediaType.APPLICATION_JSON)`. This means the JAX-RS runtime allows this method to process only requests with the `Content-Type` set to `application/json`.

If a client sends a request with a different format, such as `text/plain` or `application/xml`, the JAX-RS runtime in Jersey will try to find a matching method based on both the URL path and the media type. Since no method is defined to accept those formats, no match is found. As a result, JAX-RS automatically returns **HTTP 415 Unsupported Media Type** before the method is executed. This ensures the request is rejected at the framework level without any custom handling.

**Q2: Why is @QueryParam generally superior to a path-based approach for filtering sensor collections?**

Using `@QueryParam` for filtering, as implemented in `GET /api/v1/sensors?type=CO2`, correctly treats filtering as a variation of retrieving the same sensor collection. The path `/api/v1/sensors` always represents the full sensors collection, while the query parameter is used only to filter the results.

Using a path like `/api/v1/sensors/type/CO2` would incorrectly suggest that CO2 sensors are a separate resource with their own identity. In reality, they are just a filtered view of the same collection.

Query parameters are also more flexible because multiple filters can be used together easily. For example, `/api/v1/sensors?type=CO2&status=ACTIVE` allows combined filtering. If path parameters were used, each combination would require a separate endpoint, which would make the API harder to manage and maintain.

---

### Part 4 — Deep Nesting with Sub-Resources

**Q1: Discuss the architectural benefits of the Sub-Resource Locator pattern.**

In this project, `SensorResource` contains a sub-resource locator method annotated with `@Path("/{sensorId}/readings")` that returns a new instance of `SensorReadingResource`, passing the `sensorId` through its constructor. The JAX-RS runtime then passes all further request handling and path matching to `SensorReadingResource`.

The main benefit of this approach is the separation of concerns. `SensorResource` is responsible for sensor-related operations, while `SensorReadingResource` handles all reading-related operations for a specific sensor. This keeps each class smaller and easier to understand.

Without this pattern, a single resource class would need to manage all nested routes such as `sensors/{id}`, `sensors/{id}/readings`, and more. This would make the class large and difficult to manage. The sub-resource locator pattern keeps the code modular and makes it easier to add new nested features without changing existing code.

---

### Part 5 — Advanced Error Handling, Exception Mapping & Logging

**Q1: Why is HTTP 422 more semantically accurate than 404 when a sensor references a non-existent room?**

When a client sends `POST /api/v1/sensors` with a `roomId` that does not exist, the request URL is still valid because the `/api/v1/sensors` endpoint exists and can be accessed. The JSON payload is also correctly formatted. The issue is that the `roomId` value inside the request refers to a room that does not exist.

HTTP 404 Not Found means the requested URL or endpoint does not exist, which is not accurate in this case because the endpoint is valid. HTTP 422 Unprocessable Entity is more appropriate because it means the server understood the request and the format, but cannot process it due to invalid data — in this case, a missing referenced room.

In this project, this is handled using `LinkedResourceNotFoundException` and its corresponding mapper `LinkedResourceNotFoundExceptionMapper`, which returns a structured JSON response with HTTP status 422.

**Q2: From a cybersecurity standpoint, what are the risks of exposing Java stack traces to external API consumers?**

Exposing raw Java stack traces creates several serious security risks.

First, stack traces reveal internal package structures and class names such as `com.example.resource.SensorResource` or `com.example.dao.MockDatabase`. This gives attackers a clear view of how the system is built.

Second, they can expose versions of libraries and frameworks being used. Attackers can compare these versions with known vulnerabilities in the CVE (Common Vulnerabilities and Exposures) database to find possible exploits.

Third, exception messages may include sensitive information such as null field names, file paths, or configuration details. This information can help attackers understand the system and plan further attacks.

In this project, `GlobalExceptionMapper` handles all unexpected exceptions and returns a simple `"Internal server error"` message with HTTP status 500. This ensures that no internal system details are exposed to the client.

**Q3: Why is it better to use JAX-RS filters for logging rather than manually adding Logger.info() calls inside every resource method?**

Manually adding logging statements inside every resource method breaks the DRY (Don't Repeat Yourself) principle and spreads a cross-cutting concern across the entire codebase. If the logging format needs to be changed, every method would have to be updated.

JAX-RS filters solve this problem in a cleaner way. In this project, `LoggingFilter` implements both `ContainerRequestFilter` and `ContainerResponseFilter`. The `filter(ContainerRequestContext)` method is used to log the HTTP method and URI for every incoming request. The `filter(ContainerRequestContext, ContainerResponseContext)` method logs the HTTP status code for every outgoing response.

Once registered using the `@Provider` annotation, the filter automatically applies to all requests in the API without modifying any resource class. This keeps resource classes focused only on business logic, ensures consistent logging across the system, and makes logging easier to manage and update in one place.
