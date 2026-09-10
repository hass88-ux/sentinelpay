package com.sentinelpay.backend.pipeline;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tools.jackson.databind.ObjectMapper;

class IncidentIntegrationTest {
    @Test void historicalDemoForecastsCrossingAndExposesEvidenceWithAiFallback() throws Exception {
        try(var runtime=new TestPipeline()) {
            var sample=IncidentDemo.seed(runtime);
            var mapper=new ObjectMapper();
            String forecasts="/api/intelligence/forecasts?currency=USD&asOf="+sample.forecastAsOf();
            var response=request(runtime,"GET",forecasts);
            assertEquals(200,response.statusCode());
            var projections=mapper.readTree(response.body());
            assertEquals("THRESHOLD_RISK",projections.get(0).get("state").asText());
            assertEquals(28,projections.get(0).get("projectedValue").asDouble(),0.00001);
            assertEquals(1250,projections.get(1).get("projectedValue").asDouble(),0.00001);
            String path="/api/intelligence/incidents/"+sample.caseId();
            var report=request(runtime,"GET",path);
            assertEquals(200,report.statusCode());
            assertEquals("no-store",report.headers().firstValue("Cache-Control").orElseThrow());
            var parsed=mapper.readTree(report.body());
            assertEquals("CRITICAL",parsed.get("incident").get("severity").asText());
            assertEquals(3,parsed.get("hypotheses").size());
            assertEquals(3,parsed.get("evidence").size());
            assertEquals(1,mapper.readTree(request(runtime,"GET","/api/intelligence/incidents").body()).size());
            var explanation=request(runtime,"POST",path+"/explanation");
            assertEquals(200,explanation.statusCode());
            assertEquals("DETERMINISTIC",mapper.readTree(explanation.body()).get("mode").asText());
            assertEquals(400,request(runtime,"GET","/api/intelligence/incidents?limit=101").statusCode());
            assertEquals(400,request(runtime,"GET","/api/intelligence/forecasts?currency=bad").statusCode());
            assertEquals(400,request(runtime,"GET","/api/intelligence/forecasts?asOf=2200-01-01T00:00:00Z").statusCode());
            assertEquals(400,request(runtime,"GET","/api/intelligence/forecasts?asOf="+sample.forecastAsOf().plusSeconds(1)).statusCode());
            assertEquals(404,request(runtime,"GET","/api/intelligence/incidents/00000000-0000-0000-0000-000000000000").statusCode());
            assertEquals(400,request(runtime,"GET","/api/intelligence/incidents/not-a-uuid").statusCode());
            assertEquals("INSUFFICIENT_DATA",mapper.readTree(request(runtime,"GET",
                    "/api/intelligence/forecasts?currency=EUR&asOf="+sample.forecastAsOf()).body()).get(0).get("state").asText());
        }
    }
    private HttpResponse<String> request(TestPipeline runtime,String method,String path) throws Exception {
        String port=runtime.context.getEnvironment().getProperty("local.server.port");
        try(var client=HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                    .timeout(Duration.ofSeconds(35)).method(method,HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
