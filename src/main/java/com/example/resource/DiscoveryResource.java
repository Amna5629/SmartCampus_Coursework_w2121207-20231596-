package com.example.resource;

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/")
public class DiscoveryResource {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getApiInfo() {
        Map<String, Object> info = new HashMap<>();
        Map<String, String> resources = new HashMap<>();
        resources.put("rooms", "/api/v1/rooms");
        resources.put("sensors", "/api/v1/sensors");

        info.put("api", "Smart Campus Sensor & Room Management API");
        info.put("version", "v1");
        info.put("adminContact", "smartcampus@westminster.ac.uk");
        info.put("resources", resources);
        return info;
    }
}
