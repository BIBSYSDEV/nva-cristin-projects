package no.unit.nva.cristin.projects;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import nva.commons.core.Environment;

public class CristinApiClientStub extends CristinApiClient {

    private static final String CRISTIN_QUERY_PROJECTS_RESPONSE_JSON_FILE = "/cristinQueryProjectsResponse.json";
    private static final String CRISTIN_GET_PROJECT_RESPONSE_JSON_FILE = "/cristinGetProjectResponse.json";

    public CristinApiClientStub(Environment environment) {
        super(environment);
    }

    @Override
    protected InputStreamReader fetchQueryResults(URL url) {
        return mockQueryResponseReader();
    }

    @Override
    protected InputStreamReader fetchGetResult(URL url) {
        return mockGetResponseReader();
    }

    private InputStreamReader mockGetResponseReader() {
        InputStream getResultAsStream = CristinApiClientStub.class
            .getResourceAsStream(CRISTIN_GET_PROJECT_RESPONSE_JSON_FILE);
        return new InputStreamReader(getResultAsStream);
    }

    private InputStreamReader mockQueryResponseReader() {
        InputStream queryResultsAsStream = CristinApiClientStub.class
            .getResourceAsStream(CRISTIN_QUERY_PROJECTS_RESPONSE_JSON_FILE);
        return new InputStreamReader(queryResultsAsStream);
    }
}
