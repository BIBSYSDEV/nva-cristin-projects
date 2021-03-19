package no.unit.nva.cristin.projects;

import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

/**
 * Handler for requests to Lambda function.
 */
public class FetchCristinProjects extends ApiGatewayHandler<Void, ProjectsWrapper> {

    protected static final String TITLE_MISSING_OR_HAS_ILLEGAL_CHARACTERS = "Parameter 'title' is missing or invalid. "
        + "May only contain alphanumeric characters, dash, comma, period and whitespace";
    protected static final String LANGUAGE_INVALID = "Parameter 'language' has invalid value";

    private static final char CHARACTER_DASH = '-';
    private static final char CHARACTER_COMMA = ',';
    private static final char CHARACTER_PERIOD = '.';
    private static final List<String> VALID_LANGUAGE_CODES = Arrays.asList("nb", "en");

    private static final String CRISTIN_QUERY_PARAMETER_TITLE_KEY = "title";
    private static final String CRISTIN_QUERY_PARAMETER_LANGUAGE_KEY = "lang";
    private static final String CRISTIN_QUERY_PARAMETER_PAGE_KEY = "page";
    private static final String CRISTIN_QUERY_PARAMETER_PAGE_VALUE = "1";
    private static final String CRISTIN_QUERY_PARAMETER_PER_PAGE_KEY = "per_page";
    private static final String CRISTIN_QUERY_PARAMETER_PER_PAGE_VALUE = "5";

    protected static final String LANGUAGE_QUERY_PARAMETER = "language";
    protected static final String TITLE_QUERY_PARAMETER = "title";
    private static final String DEFAULT_LANGUAGE_CODE = "nb";
    private final transient CristinApiClient cristinApiClient;

    @SuppressWarnings("unused")
    @JacocoGenerated
    public FetchCristinProjects() {
        this(new Environment());
    }

    @JacocoGenerated
    public FetchCristinProjects(Environment environment) {
        this(new CristinApiClient(), environment);
    }

    protected FetchCristinProjects(CristinApiClient cristinApiClient, Environment environment) {
        super(Void.class, environment);
        this.cristinApiClient = cristinApiClient;
    }

    @Override
    protected ProjectsWrapper processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        String language = getValidLanguage(requestInfo);
        String title = getValidTitle(requestInfo);

        return getTransformedCristinProjectsUsingWrapperObject(language, title);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, ProjectsWrapper output) {
        return HttpURLConnection.HTTP_OK;
    }

    protected Optional<String> getQueryParam(RequestInfo requestInfo, String queryParameter) {
        return attempt(() -> requestInfo.getQueryParameter(queryParameter)).toOptional();
    }

    private String getValidTitle(RequestInfo requestInfo) throws BadRequestException {
        return getQueryParam(requestInfo, TITLE_QUERY_PARAMETER)
            .filter(this::isValidTitle)
            .orElseThrow(() -> new BadRequestException(TITLE_MISSING_OR_HAS_ILLEGAL_CHARACTERS));
    }

    private String getValidLanguage(RequestInfo requestInfo) throws BadRequestException {
        return Optional.of(getQueryParam(requestInfo, LANGUAGE_QUERY_PARAMETER)
            .orElse(DEFAULT_LANGUAGE_CODE))
            .filter(this::isValidLanguage)
            .orElseThrow(() -> new BadRequestException(LANGUAGE_INVALID));
    }

    private ProjectsWrapper getTransformedCristinProjectsUsingWrapperObject(String language, String title) {
        Map<String, String> cristinQueryParameters = createCristinQueryParameters(title, language);

        return attempt(() ->
            cristinApiClient
                .queryCristinProjectsIntoWrapperObjectWithAdditionalMetadata(cristinQueryParameters, language))
            .orElseThrow();
    }

    private boolean isValidTitle(String str) {
        char[] charArray = str.toCharArray();
        for (char c : charArray) {
            if (!isValidCharacter(c)) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidCharacter(char c) {
        return Character.isWhitespace(c)
            || Character.isLetterOrDigit(c)
            || c == CHARACTER_DASH
            || c == CHARACTER_COMMA
            || c == CHARACTER_PERIOD;
    }

    private boolean isValidLanguage(String language) {
        return VALID_LANGUAGE_CODES.contains(language);
    }

    private Map<String, String> createCristinQueryParameters(String title, String language) {
        Map<String, String> queryParameters = new ConcurrentHashMap<>();
        queryParameters.put(CRISTIN_QUERY_PARAMETER_TITLE_KEY, title);
        queryParameters.put(CRISTIN_QUERY_PARAMETER_LANGUAGE_KEY, language);
        queryParameters.put(CRISTIN_QUERY_PARAMETER_PAGE_KEY, CRISTIN_QUERY_PARAMETER_PAGE_VALUE);
        queryParameters.put(CRISTIN_QUERY_PARAMETER_PER_PAGE_KEY, CRISTIN_QUERY_PARAMETER_PER_PAGE_VALUE);
        return queryParameters;
    }

}
