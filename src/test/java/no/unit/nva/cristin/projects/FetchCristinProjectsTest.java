package no.unit.nva.cristin.projects;

import static no.unit.nva.cristin.projects.Constants.DEFAULT_NUMBER_OF_RESULTS;
import static no.unit.nva.cristin.projects.Constants.FIRST_PAGE;
import static no.unit.nva.cristin.projects.Constants.LANGUAGE;
import static no.unit.nva.cristin.projects.Constants.NUMBER_OF_RESULTS;
import static no.unit.nva.cristin.projects.Constants.OBJECT_MAPPER;
import static no.unit.nva.cristin.projects.Constants.PAGE;
import static no.unit.nva.cristin.projects.Constants.TITLE;
import static no.unit.nva.cristin.projects.ErrorMessages.ERROR_MESSAGE_BACKEND_FETCH_FAILED;
import static no.unit.nva.cristin.projects.ErrorMessages.ERROR_MESSAGE_LANGUAGE_INVALID;
import static no.unit.nva.cristin.projects.ErrorMessages.ERROR_MESSAGE_NUMBER_OF_RESULTS_VALUE_INVALID;
import static no.unit.nva.cristin.projects.ErrorMessages.ERROR_MESSAGE_PAGE_VALUE_INVALID;
import static no.unit.nva.cristin.projects.ErrorMessages.ERROR_MESSAGE_SERVER_ERROR;
import static no.unit.nva.cristin.projects.ErrorMessages.ERROR_MESSAGE_TITLE_MISSING_OR_HAS_ILLEGAL_CHARACTERS;
import static nva.commons.apigateway.ApiGatewayHandler.APPLICATION_PROBLEM_JSON;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import no.unit.nva.cristin.projects.model.cristin.CristinProject;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.apigateway.exceptions.BadGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class FetchCristinProjectsTest {

    private static final String LANGUAGE_NB = "nb";
    private static final String INVALID_LANGUAGE = "ru";
    private static final String RANDOM_TITLE = "reindeer";
    private static final String TITLE_ILLEGAL_CHARACTERS = "abc123- ,-?";
    private static final String INVALID_JSON = "This is not valid JSON!";
    private static final String EMPTY_LIST_STRING = "[]";
    private static final String SECOND_PAGE = "2";
    private static final String TEN_RESULTS = "10";
    private static final String URI_WITH_PAGE_NUMBER_VALUE_OF_TWO =
        "https://api.dev.nva.aws.unit.no/project/?language=nb&page=2&results=5&title=reindeer";
    private static final String URI_WITH_TEN_NUMBER_OF_RESULTS =
        "https://api.dev.nva.aws.unit.no/project/?language=nb&page=1&results=10&title=reindeer";

    private static final String ALLOW_ALL_ORIGIN = "*";
    private static final String API_RESPONSE_NON_ENRICHED_PROJECTS_JSON = "api_response_non_enriched_projects.json";
    private static final String API_QUERY_RESPONSE_NO_PROJECTS_FOUND_JSON = "api_query_response_no_projects_found.json";
    private static final String QUERY_CRISTIN_PROJECTS_EXAMPLE_URI =
        "https://api.cristin.no/v2/projects/?lang=nb&page=1&per_page=5&title=reindeer";
    private CristinApiClient cristinApiClientStub;
    private final Environment environment = new Environment();
    private Context context;
    private ByteArrayOutputStream output;
    private FetchCristinProjects handler;

    @BeforeEach
    void setUp() {
        cristinApiClientStub = new CristinApiClientStub();
        context = mock(Context.class);
        output = new ByteArrayOutputStream();
        handler = new FetchCristinProjects(cristinApiClientStub, environment);
    }

    @ParameterizedTest
    @ArgumentsSource(TestPairProvider.class)
    void handlerReturnsExpectedBodyWhenRequestInputIsValid(String expected) throws IOException {
        String actual = sendDefaultQuery().getBody();
        assertEquals(OBJECT_MAPPER.readTree(expected), OBJECT_MAPPER.readTree(actual));
    }

    @Test
    void handlerIgnoresErrorsWhenTryingToEnrichProjectInformation() throws Exception {
        cristinApiClientStub = spy(cristinApiClientStub);
        doThrow(new BadGatewayException(ERROR_MESSAGE_BACKEND_FETCH_FAILED)).when(cristinApiClientStub)
            .getProject(any(), any());
        handler = new FetchCristinProjects(cristinApiClientStub, environment);

        GatewayResponse<ProjectsWrapper> gatewayResponse = sendDefaultQuery();

        assertEquals(HttpURLConnection.HTTP_OK, gatewayResponse.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, gatewayResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void handlerReturnsOkWhenInputContainsTitleAndLanguage() throws Exception {
        GatewayResponse<ProjectsWrapper> response = sendDefaultQuery();
        assertEquals(HttpURLConnection.HTTP_OK, response.getStatusCode());
    }

    @Test
    void handlerThrowsInternalErrorWhenQueryingProjectsFails() throws Exception {
        cristinApiClientStub = spy(cristinApiClientStub);
        doThrow(RuntimeException.class).when(cristinApiClientStub).getEnrichedProjectsUsingQueryResponse(any(), any());
        handler = new FetchCristinProjects(cristinApiClientStub, environment);

        GatewayResponse<ProjectsWrapper> gatewayResponse = sendDefaultQuery();

        assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, gatewayResponse.getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, gatewayResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE));
        assertTrue(gatewayResponse.getBody().contains(ERROR_MESSAGE_SERVER_ERROR));
    }

    @Test
    void handlerReturnsNonEnrichedBodyWhenEnrichingFails() throws Exception {
        cristinApiClientStub = spy(cristinApiClientStub);
        doThrow(new BadGatewayException(ERROR_MESSAGE_BACKEND_FETCH_FAILED)).when(cristinApiClientStub)
            .getProject(any(), any());
        handler = new FetchCristinProjects(cristinApiClientStub, environment);
        GatewayResponse<ProjectsWrapper> gatewayResponse = sendDefaultQuery();
        String expected = getBodyFromResource(API_RESPONSE_NON_ENRICHED_PROJECTS_JSON);

        assertEquals(OBJECT_MAPPER.readTree(expected), OBJECT_MAPPER.readTree(gatewayResponse.getBody()));
    }

    @Test
    void handlerThrowsBadRequestWhenMissingTitleQueryParameter() throws IOException {
        InputStream input = requestWithQueryParameters(Map.of(LANGUAGE, LANGUAGE_NB));

        handler.handleRequest(input, output, context);
        GatewayResponse<ProjectsWrapper> gatewayResponse = GatewayResponse.fromOutputStream(output);

        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, gatewayResponse.getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, gatewayResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE));
        assertTrue(gatewayResponse.getBody().contains(ERROR_MESSAGE_TITLE_MISSING_OR_HAS_ILLEGAL_CHARACTERS));
    }

    @Test
    void handlerSetsDefaultValueForMissingOptionalLanguageParameterAndReturnOk() throws Exception {
        InputStream input = requestWithQueryParameters(Map.of(TITLE, RANDOM_TITLE));

        handler.handleRequest(input, output, context);
        GatewayResponse<ProjectsWrapper> gatewayResponse = GatewayResponse.fromOutputStream(output);

        assertEquals(HttpURLConnection.HTTP_OK, gatewayResponse.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, gatewayResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void handlerReceivesAllowOriginHeaderValueFromEnvironmentAndPutsItOnResponse() throws Exception {
        GatewayResponse<ProjectsWrapper> gatewayResponse = sendDefaultQuery();
        assertEquals(ALLOW_ALL_ORIGIN, gatewayResponse.getHeaders().get(ApiGatewayHandler.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void handlerReturnsBadRequestWhenTitleQueryParamIsEmpty() throws Exception {
        InputStream input = requestWithQueryParameters(Map.of(TITLE, EMPTY_STRING));

        handler.handleRequest(input, output, context);
        GatewayResponse<ProjectsWrapper> gatewayResponse = GatewayResponse.fromOutputStream(output);

        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, gatewayResponse.getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, gatewayResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE));
        assertTrue(gatewayResponse.getBody().contains(ERROR_MESSAGE_TITLE_MISSING_OR_HAS_ILLEGAL_CHARACTERS));
    }

    @Test
    void handlerReturnsBadRequestWhenReceivingTitleQueryParamWithIllegalCharacters() throws Exception {
        InputStream input = requestWithQueryParameters(Map.of(TITLE, TITLE_ILLEGAL_CHARACTERS));

        handler.handleRequest(input, output, context);
        GatewayResponse<ProjectsWrapper> gatewayResponse = GatewayResponse.fromOutputStream(output);

        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, gatewayResponse.getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, gatewayResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE));
        assertTrue(gatewayResponse.getBody().contains(ERROR_MESSAGE_TITLE_MISSING_OR_HAS_ILLEGAL_CHARACTERS));
    }

    @Test
    void handlerReturnsBadRequestWhenReceivingInvalidLanguageQueryParam() throws Exception {
        InputStream input = requestWithQueryParameters(Map.of(TITLE, RANDOM_TITLE,
            LANGUAGE, INVALID_LANGUAGE));

        handler.handleRequest(input, output, context);
        GatewayResponse<ProjectsWrapper> gatewayResponse = GatewayResponse.fromOutputStream(output);

        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, gatewayResponse.getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, gatewayResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE));
        assertTrue(gatewayResponse.getBody().contains(ERROR_MESSAGE_LANGUAGE_INVALID));
    }

    @Test
    void readerThrowsIoExceptionWhenReadingInvalidJson() {
        Executable action = () -> CristinApiClient.fromJson(INVALID_JSON, CristinProject.class);
        assertThrows(IOException.class, action);
    }

    @Test
    void handlerReturnsProjectsWrapperWithAllMetadataButEmptyHitsArrayWhenNoMatchesAreFoundInCristin()
        throws Exception {

        cristinApiClientStub = spy(cristinApiClientStub);
        HttpResponse<String> emptyResponse = new HttpResponseStub(EMPTY_LIST_STRING);
        doReturn(emptyResponse)
            .when(cristinApiClientStub).fetchQueryResults(any(URI.class));
        String expected = getBodyFromResource(API_QUERY_RESPONSE_NO_PROJECTS_FOUND_JSON);

        handler = new FetchCristinProjects(cristinApiClientStub, environment);
        GatewayResponse<ProjectsWrapper> gatewayResponse = sendDefaultQuery();

        assertEquals(OBJECT_MAPPER.readTree(expected), OBJECT_MAPPER.readTree(gatewayResponse.getBody()));
    }

    @Test
    void getsCorrectUriWhenCallingQueryProjectsUriBuilder() throws Exception {
        Map<String, String> params = Map.of(
            TITLE, RANDOM_TITLE,
            LANGUAGE, LANGUAGE_NB,
            PAGE, FIRST_PAGE,
            NUMBER_OF_RESULTS, DEFAULT_NUMBER_OF_RESULTS);
        URI uri = new URI(QUERY_CRISTIN_PROJECTS_EXAMPLE_URI);

        assertEquals(uri, cristinApiClientStub.generateQueryProjectsUrl(params));
    }

    @Test
    void handlerReturnsServerErrorExceptionWhenBackendThrowsGenericException() throws Exception {
        cristinApiClientStub = spy(cristinApiClientStub);

        doThrow(RuntimeException.class).when(cristinApiClientStub).generateQueryProjectsUrl(any());
        handler = new FetchCristinProjects(cristinApiClientStub, environment);
        GatewayResponse<ProjectsWrapper> gatewayResponse = sendDefaultQuery();

        assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, gatewayResponse.getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, gatewayResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE));
        assertTrue(gatewayResponse.getBody().contains(ERROR_MESSAGE_SERVER_ERROR));
    }

    @Test
    void handlerThrowsBadGatewayExceptionWhenThereIsThrownIoExceptionWhenReadingFromJson() throws Exception {
        cristinApiClientStub = spy(cristinApiClientStub);

        doReturn(new HttpResponseStub(EMPTY_STRING))
            .when(cristinApiClientStub).fetchQueryResults(any(URI.class));
        handler = new FetchCristinProjects(cristinApiClientStub, environment);
        GatewayResponse<ProjectsWrapper> gatewayResponse = sendDefaultQuery();

        assertEquals(HttpURLConnection.HTTP_BAD_GATEWAY, gatewayResponse.getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, gatewayResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE));
        assertTrue(gatewayResponse.getBody().contains(ERROR_MESSAGE_BACKEND_FETCH_FAILED));
    }

    @Test
    void handlerReturnsInternalErrorWhenUriCreationFails() throws Exception {
        cristinApiClientStub = spy(cristinApiClientStub);

        doThrow(URISyntaxException.class).when(cristinApiClientStub).generateQueryProjectsUrl(any());

        handler = new FetchCristinProjects(cristinApiClientStub, environment);
        GatewayResponse<ProjectsWrapper> gatewayResponse = sendDefaultQuery();

        assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, gatewayResponse.getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, gatewayResponse.getHeaders().get(HttpHeaders.CONTENT_TYPE));
        assertTrue(gatewayResponse.getBody().contains(ERROR_MESSAGE_SERVER_ERROR));
    }

    @Test
    void handlerReturnsCristinProjectsWhenQueryContainsPageParameter() throws Exception {
        InputStream input = requestWithQueryParameters(
            Map.of(TITLE, RANDOM_TITLE, LANGUAGE, LANGUAGE_NB, PAGE, SECOND_PAGE));
        handler.handleRequest(input, output, context);
        GatewayResponse<ProjectsWrapper> gatewayResponse = GatewayResponse.fromOutputStream(output);

        assertEquals(HttpURLConnection.HTTP_OK, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getBody().contains(URI_WITH_PAGE_NUMBER_VALUE_OF_TWO));
    }

    @Test
    void handlerThrowsBadRequestWhenQueryHasInvalidPageParameter() throws Exception {
        InputStream input = requestWithQueryParameters(
            Map.of(TITLE, RANDOM_TITLE, LANGUAGE, LANGUAGE_NB, PAGE, TITLE_ILLEGAL_CHARACTERS));
        handler.handleRequest(input, output, context);
        GatewayResponse<ProjectsWrapper> gatewayResponse = GatewayResponse.fromOutputStream(output);

        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getBody().contains(ERROR_MESSAGE_PAGE_VALUE_INVALID));
    }

    @Test
    void handlerReturnsCristinProjectsWhenQueryContainsResultsParameter() throws Exception {
        InputStream input = requestWithQueryParameters(
            Map.of(TITLE, RANDOM_TITLE, LANGUAGE, LANGUAGE_NB, NUMBER_OF_RESULTS, TEN_RESULTS));
        handler.handleRequest(input, output, context);
        GatewayResponse<ProjectsWrapper> gatewayResponse = GatewayResponse.fromOutputStream(output);

        assertEquals(HttpURLConnection.HTTP_OK, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getBody().contains(URI_WITH_TEN_NUMBER_OF_RESULTS));
    }

    @Test
    void handlerThrowsBadRequestWhenQueryHasInvalidResultsParameter() throws Exception {
        InputStream input = requestWithQueryParameters(
            Map.of(TITLE, RANDOM_TITLE, LANGUAGE, LANGUAGE_NB,
                NUMBER_OF_RESULTS, TITLE_ILLEGAL_CHARACTERS));
        handler.handleRequest(input, output, context);
        GatewayResponse<ProjectsWrapper> gatewayResponse = GatewayResponse.fromOutputStream(output);

        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getBody().contains(ERROR_MESSAGE_NUMBER_OF_RESULTS_VALUE_INVALID));
    }

    private GatewayResponse<ProjectsWrapper> sendDefaultQuery() throws IOException {
        InputStream input = requestWithQueryParameters(Map.of(TITLE, RANDOM_TITLE,
            LANGUAGE, LANGUAGE_NB));
        handler.handleRequest(input, output, context);
        return GatewayResponse.fromOutputStream(output);
    }

    private InputStream requestWithQueryParameters(Map<String, String> map) throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(OBJECT_MAPPER)
            .withBody(null)
            .withQueryParameters(map)
            .build();
    }

    private String getBodyFromResource(String resource) {
        return IoUtils.stringFromResources(Path.of(resource));
    }
}
