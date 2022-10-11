package ok.dht.test.kurdyukov.server;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.iq80.leveldb.impl.Iq80DBFactory.bytes;

public class HttpServerDao extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(HttpServerDao.class);
    private static final int AWAIT_TERMINATE_SECONDS = 1;
    private static final String ENDPOINT = "/v0/entity";
    private static final Set<Integer> supportMethods = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );

    private final DB levelDB;
    private final ExecutorService executorService;

    public HttpServerDao(
            HttpServerConfig config,
            DB levelDB,
            ExecutorService executorService,
            Object... routers
    ) throws IOException {
        super(config, routers);
        this.levelDB = levelDB;
        this.executorService = executorService;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!request.getPath().equals(ENDPOINT)) {
            session.sendResponse(responseEmpty(Response.BAD_REQUEST));
            return;
        }

        int method = request.getMethod();

        if (!supportMethods.contains(method)) {
            session.sendResponse(responseEmpty(Response.METHOD_NOT_ALLOWED));
            return;
        }

        String id = request.getParameter("id=");

        if (id == null || id.isBlank()) {
            session.sendResponse(responseEmpty(Response.BAD_REQUEST));
            return;
        }

        try {
            executorService.execute(() -> {
                        try {
                            session.sendResponse(handle(request, method, id));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
            );
        } catch (RejectedExecutionException e) {
            logger.warn("Reject request", e);
            session.sendResponse(responseEmpty(Response.SERVICE_UNAVAILABLE));
        }
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            thread.selector.forEach(Session::close);
        }

        super.stop();
        executorService.shutdown();

        try {
            if (executorService.awaitTermination(AWAIT_TERMINATE_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Fail stopping thread pool workers", e);
            Thread.currentThread().interrupt();
        }

        try {
            levelDB.close();
        } catch (IOException e) {
            logger.error("Fail db close.", e);
        }
    }

    private Response handle(Request request, int method, String id) {
        return switch (method) {
            case Request.METHOD_GET -> handleGet(id);
            case Request.METHOD_PUT -> handlePut(request, id);
            case Request.METHOD_DELETE -> handleDelete(id);
            default -> throw new IllegalArgumentException("Unsupported method!");
        };
    }

    private Response handleGet(String id) {
        byte[] value;

        try {
            value = levelDB.get(bytes(id));
        } catch (DBException e) {
            logger.error("Fail on get method with id: " + id, e);

            return responseEmpty(Response.INTERNAL_ERROR);
        }

        if (value == null) {
            return responseEmpty(Response.NOT_FOUND);
        } else {
            return new Response(Response.OK, value);
        }
    }

    private Response handlePut(Request request, String id) {
        try {
            levelDB.put(bytes(id), request.getBody());
            return responseEmpty(Response.CREATED);
        } catch (DBException e) {
            logger.error("Fail on put method with id: " + id, e);
            return responseEmpty(Response.INTERNAL_ERROR);
        }
    }

    private Response handleDelete(String id) {
        try {
            levelDB.delete(bytes(id));
            return responseEmpty(Response.ACCEPTED);
        } catch (DBException e) {
            logger.error("Fail on delete method with id: " + id, e);
            return responseEmpty(Response.INTERNAL_ERROR);
        }
    }

    private static Response responseEmpty(String status) {
        return new Response(status, Response.EMPTY);
    }
}
