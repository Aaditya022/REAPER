package com.stackd.ignition.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for {@link HealthCheckService} using mocked HTTP responses: no real
 * server, no network, no tokens.
 */
class HealthCheckServiceTest {

    private static final String LIVE_URL = "https://backend-demo-prg1.zerops.app";

    private final HttpClient client = mock(HttpClient.class);
    private final HealthCheckService service = new HealthCheckService(client);

    private HttpResponse<Void> response(int status) {
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        return response;
    }

    @Test
    void accepts2xxResponse() throws Exception {
        doReturn(response(200)).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        service.verify(LIVE_URL, Duration.ofSeconds(5));
    }

    @Test
    void issuesGetRequestToUrlRootWithBoundedTimeout() throws Exception {
        doReturn(response(200)).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        service.verify(LIVE_URL, Duration.ofMillis(250));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertEquals("GET", request.method());
        assertEquals(URI.create(LIVE_URL), request.uri());
        assertEquals(Optional.of(Duration.ofMillis(250)), request.timeout());
    }

    @Test
    void rejectsNon2xxResponse() throws Exception {
        doReturn(response(503)).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        HealthCheckException ex = assertThrows(HealthCheckException.class,
                () -> service.verify(LIVE_URL, Duration.ofSeconds(5)));
        assertEquals(HealthCheckException.HEALTH_CHECK_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("503"));
    }

    @Test
    void rejectsOnReadTimeout() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("read timed out"));

        HealthCheckException ex = assertThrows(HealthCheckException.class,
                () -> service.verify(LIVE_URL, Duration.ofMillis(100)));
        assertEquals(HealthCheckException.HEALTH_CHECK_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    void rejectsOnConnectionFailure() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        HealthCheckException ex = assertThrows(HealthCheckException.class,
                () -> service.verify(LIVE_URL, Duration.ofSeconds(5)));
        assertEquals(HealthCheckException.HEALTH_CHECK_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("unreachable"));
    }

    @Test
    void rejectsMalformedUrlWithoutSending() throws Exception {
        HealthCheckException ex = assertThrows(HealthCheckException.class,
                () -> service.verify("not a url", Duration.ofSeconds(5)));
        assertEquals(HealthCheckException.HEALTH_CHECK_FAILED, ex.getErrorCode());
        verify(client, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void requiresHttpsScheme() throws Exception {
        for (String url : new String[] {"http://example.com/", "ftp://example.com/", "file:///etc/passwd"}) {
            HealthCheckException ex = assertThrows(HealthCheckException.class,
                    () -> service.verify(url, Duration.ofSeconds(5)));
            assertEquals(HealthCheckException.HEALTH_CHECK_FAILED, ex.getErrorCode());
        }
        verify(client, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void rejectsBlankAndHostlessUrls() throws Exception {
        assertThrows(HealthCheckException.class, () -> service.verify("", Duration.ofSeconds(5)));
        assertThrows(HealthCheckException.class, () -> service.verify("https://", Duration.ofSeconds(5)));
        assertThrows(HealthCheckException.class, () -> service.verify(null, Duration.ofSeconds(5)));
        verify(client, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
