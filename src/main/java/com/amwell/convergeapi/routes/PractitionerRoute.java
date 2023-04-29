package com.amwell.convergeapi.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;

import com.amwell.convergeapi.exceptions.PatientApiException;

public class PractitionerRoute extends RouteBuilder{
  
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
    from("direct:get-practitioner-by-id")
        .setProperty("patientId").simple("${header.id}")
        .setHeader("Content-Type", constant("application/json"))
        .setHeader("Accept", constant("application/json"))
        .setHeader(Exchange.HTTP_METHOD, constant("GET"))
        .removeHeader(Exchange.HTTP_PATH)
        .setHeader(Exchange.HTTP_PATH, simple("${exchangeProperty.patientId}"))
        .doTry()
        .to("https://{{fhir.host}}/baseR4/Patient/?_format=json&bridgeEndpoint=true")
        .doCatch(HttpOperationFailedException.class)
        .throwException(new PatientApiException())
        .transform()
        .body();
  }

}
