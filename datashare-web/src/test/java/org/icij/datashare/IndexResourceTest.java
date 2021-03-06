package org.icij.datashare;

import net.codestory.http.WebServer;
import net.codestory.http.filters.basic.BasicAuthFilter;
import net.codestory.http.misc.Env;
import net.codestory.http.security.Users;
import net.codestory.rest.FluentRestTest;
import org.icij.datashare.session.LocalUserFilter;
import org.icij.datashare.session.HashMapUser;
import org.icij.datashare.text.indexing.Indexer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.HashMap;

import static org.icij.datashare.IndexResource.getQueryAsString;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

public class IndexResourceTest implements FluentRestTest {
    private static WebServer server = new WebServer() {
        @Override
        protected Env createEnv() {
            return Env.prod();
        }
    }.startOnRandomPort();
    private static WebServer mockElastic = new WebServer() {
        @Override
        protected Env createEnv() {
            return Env.prod();
        }
    }.startOnRandomPort();
    @Mock Indexer mockIndexer;
    @Override public int port() { return server.port();}

    @Test
    public void test_no_auth_get_forward_request_to_elastic() {
        get("/api/index/search/foo/bar").should().respond(200)
                .contain("I am elastic GET")
                .contain("uri=foo/bar");
    }
    @Test
    public void test_no_auth_post_forward_request_to_elastic_with_body() {
        String body = "{\"body\": \"es\"}";
        post("/api/index/search/foo/bar", body).should().respond(200)
                        .contain("I am elastic POST")
                        .contain("uri=foo/bar")
                        .contain(body);
    }
    @Test
    public void test_auth_forward_request_with_user_login_as_index_prefix() {
        server.configure(routes -> routes.add(new IndexResource(new PropertiesProvider(new HashMap<String, String>() {{
                    put("elasticsearchAddress", "http://localhost:" + mockElastic.port());
                }}), mockIndexer)).filter(new BasicAuthFilter("/", "icij", Users.singleUser("cecile","pass"))));

        get("/api/index/search/index_name/foo/bar?routing=baz").withPreemptiveAuthentication("cecile", "pass").should().respond(200)
                .contain("uri=index_name/foo/bar?routing=baz");
        post("/api/index/search/index_name/foo/bar").withPreemptiveAuthentication("cecile", "pass").should().respond(200)
                .contain("uri=index_name/foo/bar");

        get("/api/index/search/datashare/foo/bar?routing=baz").withPreemptiveAuthentication("cecile", "pass").should().respond(200)
                .contain("uri=cecile-datashare/foo/bar?routing=baz");
        post("/api/index/search/datashare/foo/bar").withPreemptiveAuthentication("cecile", "pass").should().respond(200)
                .contain("uri=cecile-datashare/foo/bar");
    }
    @Test
    public void test_delete_should_return_method_not_allowed() {
        delete("/api/index/search/foo/bar").should().respond(405);
    }

    @Test
    public void test_put_create_local_index_in_local_mode() throws Exception {
        put("/api/index/create").should().respond(200);
        verify(mockIndexer).createIndex("local-datashare");
    }

    @Test
    public void test_get_returns_backend_status_405() {
        server.configure(routes -> routes.add(new IndexResource(new PropertiesProvider(new HashMap<String, String>() {{
                                    put("elasticsearchAddress", "http://localhost:" + mockElastic.port());
                                }}), mockIndexer)).filter(new BasicAuthFilter("/", "icij", HashMapUser.singleUser("cecile"))));

        put("/api/index/search/unknown").withPreemptiveAuthentication("cecile", "pass").should().respond(405);
    }

    @Test
    public void test_put_createIndex_calls_indexer() throws Exception {
        server.configure(routes -> routes.add(new IndexResource(new PropertiesProvider(new HashMap<String, String>() {{
                            put("elasticsearchAddress", "http://localhost:" + mockElastic.port());
                        }}), mockIndexer)).filter(new BasicAuthFilter("/", "icij", HashMapUser.singleUser("cecile"))));
        put("/api/index/create").withPreemptiveAuthentication("cecile", "pass").should().respond(200);
        verify(mockIndexer).createIndex("cecile-datashare");
    }

    @Before
    public void setUp() {
        initMocks(this);
        server.configure(routes -> routes.add(new IndexResource(new PropertiesProvider(new HashMap<String, String>() {{
            put("elasticsearchAddress", "http://localhost:" + mockElastic.port());
        }}), mockIndexer)).filter(new LocalUserFilter(new PropertiesProvider())));
        mockElastic.configure(routes -> routes
            .get("/:uri", (context, uri) -> "I am elastic GET uri=" + uri + "?" + getQueryAsString(context.query()))
            .post("/:uri", (context, uri) -> "I am elastic POST uri=" + uri + " " + new String(context.request().contentAsBytes()))
        );
    }
}

