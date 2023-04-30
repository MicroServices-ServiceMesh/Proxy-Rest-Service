
package com.amwell.convergeapi.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.hl7.fhir.r4.model.Patient;

import com.amwell.convergeapi.exceptions.PatientApiException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

public class PatientRoute extends RouteBuilder {

  @Override
  public void configure() throws Exception {



    // Accepts a request to read patient with id 591984
    // https://hapi.fhir.org/baseR4/Patient/591984?_format=json
    from("direct:get-patient-by-id")
        .setProperty("patientId").simple("${header.id}")
        .setHeader("Content-Type", constant("application/fhir+json"))
        .setHeader("Accept", constant("application/json"))
        .setHeader(Exchange.HTTP_METHOD, constant("GET"))
        .removeHeader(Exchange.HTTP_PATH)
        .setHeader(Exchange.HTTP_PATH, simple("${exchangeProperty.patientId}"))
        .circuitBreaker()
        .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration(100).end()
        .to("https://{{fhir.host}}/baseR4/Patient/?_format=json&bridgeEndpoint=true")
        .onFallback().setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503)).end()
        .choice()
        .when(simple("${header.CamelHttpResponseCode} == 503"))
        .setBody(constant("Error"))
        .otherwise()
        .process(exchange -> {
          String originalResponse = exchange.getIn().getBody(String.class);

          FhirContext ctx = FhirContext.forR4();
          IParser parser = ctx.newJsonParser();
          Patient patient = parser.parseResource(Patient.class, originalResponse);
          patient.setActive(false);
          String transformedResponse = parser.encodeResourceToString(patient);
          exchange.getMessage().setBody(transformedResponse);

        })
        .transform()
        .body();
  }

}