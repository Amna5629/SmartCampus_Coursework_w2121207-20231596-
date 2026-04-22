package com.example.resource;

import com.example.dao.MockDatabase;
import com.example.exception.SensorUnavailableException;
import com.example.model.Sensor;
import com.example.model.SensorReading;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("")
public class SensorReadingResource {
    private final String sensorId;

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public List<SensorReading> getReadings() {
        List<SensorReading> readings = MockDatabase.SENSOR_READINGS.get(sensorId);
        if (readings == null) {
            return new ArrayList<>();
        }
        return readings;
    }

    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addReading(SensorReading reading) {
        Sensor sensor = MockDatabase.SENSORS.get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException("Sensor " + sensorId + " is in MAINTENANCE and cannot accept readings.");
        }

        if (reading.getId() == null || reading.getId().trim().isEmpty()) {
            reading.setId(UUID.randomUUID().toString());
        }
        if (reading.getTimestamp() == 0) {
            reading.setTimestamp(System.currentTimeMillis());
        }

        List<SensorReading> readings = MockDatabase.SENSOR_READINGS.get(sensorId);
        if (readings == null) {
            readings = new ArrayList<>();
            MockDatabase.SENSOR_READINGS.put(sensorId, readings);
        }
        readings.add(reading);
        sensor.setCurrentValue(reading.getValue());

        return Response.status(Response.Status.CREATED).entity(reading).build();
    }
}
