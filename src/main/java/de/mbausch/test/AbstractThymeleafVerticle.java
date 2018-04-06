package de.mbausch.test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractThymeleafVerticle extends AbstractVerticle {

    protected FileSystem fileSystem;

    protected void init(final Router router) {
        fileSystem = vertx.fileSystem();

        // register resource loaders
        router.route(HttpMethod.GET, "/WEB-INF/images/:imageFile").handler(ctx -> {
            loadFile(ctx, "imageFile", "WEB-INF/images/");
        });
        router.route(HttpMethod.GET, "/WEB-INF/css/:cssFile").handler(ctx -> {
            loadFile(ctx, "cssFile", "WEB-INF/css/");
        });
    }

    protected void loadFile(RoutingContext ctx, final String paramName, String sourcePath) {
        String fileName = ctx.request().getParam(paramName);
        // Read a file
        fileSystem.readFile(sourcePath + fileName, fileResult -> {
            if (fileResult.failed()) {
                log.error("could not load file", fileResult.cause());
                ctx.fail(500);
                return;
            }
            // return file
            ctx.response().end(fileResult.result());
        });
    }
}
