
package com.amwell.convergeapi.routes;

import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Organization;

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
        .resilience4jConfiguration().timeoutEnabled(true).timeoutDuration(1000).end()
        .to("https://{{fhir.host}}/baseR4/Patient/?_format=json&bridgeEndpoint=true")
        .onFallback().setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503)).end()
        .choice()
        .when(simple("${header.CamelHttpResponseCode} == 503"))
        .process(exchange -> {

          FhirContext ctx = FhirContext.forR4();
          IParser parser = ctx.newJsonParser();

          Patient patient = new Patient();
          HumanName[] familyName = new HumanName[] { new HumanName() };
          familyName[0].setFamily("Doe");
          familyName[0].setGiven(Arrays.asList(new StringType[] { new StringType("John") }));
          patient.setName(Arrays.asList(familyName));

          Organization managOrganization = new Organization();
          managOrganization.setId("51473d33-e152-42e6-bf37-a9dbaecbbdb9");
          Reference ref = new Reference(managOrganization);
          patient.setManagingOrganization(ref);

          String transformedResponse = parser.encodeResourceToString(patient);
          exchange.getMessage().setBody(transformedResponse);
          exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200));

        })
        .otherwise()
        .process(exchange -> {
          String originalResponse = exchange.getIn().getBody(String.class);

          FhirContext ctx = FhirContext.forR4();
          IParser parser = ctx.newJsonParser();
          Patient patient = parser.parseResource(Patient.class, originalResponse);
          patient.setActive(false);

          Organization managOrganization = new Organization();
          managOrganization.setId("51473d33-e152-42e6-bf37-a9dbaecbbdb9");
          Reference ref = new Reference(managOrganization);
          patient.setManagingOrganization(ref);

          String transformedResponse = parser.encodeResourceToString(patient);
          exchange.getMessage().setBody(transformedResponse);
        })
        // .to("direct:authorizeRequest");
        .transform()
        .body();

    from("direct:authorizeRequest")
        .log("Received Message is ${body}")
        .log("Received Message is ${header.Authorization}")
        .setProperty("bearerToken").simple("${header.Authorization}")
        .removeHeader("*")
        .setHeader(Exchange.HTTP_METHOD, constant("POST"))
        .to("http://{{auth.service.host}}/policy/api/public/patient?bridgeEndpoint=true")
        .end();
  }

}