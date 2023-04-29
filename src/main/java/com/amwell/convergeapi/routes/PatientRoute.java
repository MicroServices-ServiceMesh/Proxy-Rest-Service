
package com.amwell.convergeapi.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.hl7.fhir.r4.model.Patient;

import com.amwell.convergeapi.exceptions.PatientApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

public class PatientRoute extends RouteBuilder {

  @Override
  public void configure() throws Exception {

    onException(
        PatientApiException.class)
        .handled(true)
        .to("direct:fix-the-error")
        .maximumRedeliveries(3)
        .useOriginalMessage()
        .to("direct:send-request-no-retry")
        .end();

    // Accepts a request to read patient with id 591984
    // https://hapi.fhir.org/baseR4/Patient/591984?_format=json
    from("direct:get-patient-by-id")
        .setProperty("patientId").simple("${header.id}")
        .setHeader("Content-Type", constant("application/json"))
        .setHeader("Accept", constant("application/json"))
        .setHeader(Exchange.HTTP_METHOD, constant("GET"))
        .removeHeader(Exchange.HTTP_PATH)
        .setHeader(Exchange.HTTP_PATH, simple("${exchangeProperty.patientId}"))
        // .circuitBreaker()
        .to("https://{{fhir.host}}/baseR4/Patient/?_format=json&bridgeEndpoint=true")
        // .onFallback().transform().constant("Fallback message")
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