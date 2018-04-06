package de.mbausch.test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.ThymeleafTemplateEngine;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Set;

@Slf4j
public class FileServerVerticle extends AbstractThymeleafVerticle {

    private HttpServer httpServer;

    public void start(Future<Void> startFuture) {

        log.info("start file http server...");

        final ThymeleafTemplateEngine engine = ThymeleafTemplateEngine.create();
        final Router router = Router.router(vertx);

        init(router);

        // register upload view
        router.route(HttpMethod.GET, "/upload").handler(ctx -> {
            log.info("call file upload page");

            ctx.put("titleInfo", "Now Your'e able to upload your Files!!");

            engine.render(ctx, "WEB-INF/templates/", "upload.html", templateResult -> {
                if (templateResult.succeeded()) {
                    ctx.response().end(templateResult.result());
                } else {
                    ctx.fail(templateResult.cause());
                }
            });
        });

        // register upload post
        router.post("/upload").handler(BodyHandler.create().setMergeFormAttributes(true));
        router.route(HttpMethod.POST, "/upload").handler(ctx -> {
            log.info("call upload post");

            ctx.fileUploads().forEach(file -> {
                String uploadedFileName = file.fileName();
                Buffer uploadedFile = fileSystem.readFileBlocking(file.uploadedFileName());

                log.info("received uploaded file {]", uploadedFileName);

                String savePath = "D:\\temp\\test\\" + uploadedFileName;
                fileSystem.createFileBlocking(savePath);

                fileSystem.writeFile(savePath, uploadedFile, writeRes -> {
                    if (writeRes.failed()) {
                        log.error("could not save file", writeRes.cause());
                        ctx.response().setStatusCode(500);
                        ctx.response().end(writeRes.cause().getMessage());
                    } else {
                        log.info("file saved successfully");
                        ctx.response().end("file Received and saved successfully");
                    }
                });
            });

        });

        // start a HTTP web server on port 8282
        httpServer = vertx.createHttpServer();
        httpServer.requestHandler(router::accept).listen(8282);

        startFuture.complete();
    }

    public void stop(Future<Void> stopFuture) {
        log.info("stop file http server...");
        httpServer.close(shutdownResult -> {
            if (shutdownResult.failed()) {
                log.error("could not stop file http Server", shutdownResult.cause());
            }
            stopFuture.complete();
        });
    }
}
