package de.mbausch.test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.templ.ThymeleafTemplateEngine;

public class ServerVerticle extends AbstractVerticle {

    private Router router;
    private HttpServer httpServer;

    public void start(Future<Void> startFuture) {
       router = Router.router(vertx);

        final ThymeleafTemplateEngine engine = ThymeleafTemplateEngine.create();

        router.get().handler(ctx -> {
            // we define a hardcoded title for our application
            ctx.put("welcome", "Hi there!");
            // and now delegate to the engine to render it.
            engine.render(ctx, "WEB-INF/templates", "index.html", res -> {
                if (res.succeeded()) {
                    ctx.response().end(res.result());
                } else {
                    ctx.fail(res.cause());
                }
            });
        });
        // start a HTTP web server on port 8080
        httpServer = vertx.createHttpServer();
        httpServer.requestHandler(router::accept).listen(8080);

        startFuture.complete();
    }

}
