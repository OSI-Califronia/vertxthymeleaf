package de.mbausch.test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.templ.ThymeleafTemplateEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.*;


@Slf4j
public class ServerVerticle extends AbstractVerticle {

    private Map<UUID, JsonObject> loggedInUsers = new HashMap<>();

    private HttpServer httpServer;
    private FileSystem fileSystem;

    public void start(Future<Void> startFuture) {
        log.info("start http server...");

        fileSystem = vertx.fileSystem();

        final ThymeleafTemplateEngine engine = ThymeleafTemplateEngine.create();
        final Router router = Router.router(vertx);

        // register resource loaders
        router.route(HttpMethod.GET, "/WEB-INF/images/:imageFile").handler(ctx -> {
            loadFile(ctx, "imageFile", "WEB-INF/images/");
        });
        router.route(HttpMethod.GET, "/WEB-INF/css/:cssFile").handler(ctx -> {
            loadFile(ctx, "cssFile", "WEB-INF/css/");
        });

        router.route().handler(CookieHandler.create());

        router.route(HttpMethod.GET, "/oauth/authorize").handler(ctx -> {
            log.info("call authorize!!!");
            String responseType = ctx.request().getParam("response_type");
            String clientId =  ctx.request().getParam("client_id");
            String redirectUri = ctx.request().getParam("redirect_uri");

            Cookie sessionCookie = ctx.getCookie("JSESSIONID");
            // handle already logged in user.
            if (null != sessionCookie && loggedInUsers.containsKey(UUID.fromString(sessionCookie.getValue()))) {
                JsonObject authObj =  loggedInUsers.get(UUID.fromString(sessionCookie.getValue()));
                log.info("already logged in!!! {}", authObj.getString("user"));
                ctx.response().setStatusCode(303);
                ctx.response().headers().add("Location", redirectUri);
                ctx.response().end("already Logged in! " + authObj.getString("user"));
                return;
            }

            UUID sessionId = UUID.randomUUID();
            loggedInUsers.put(sessionId, new JsonObject().put("redirectUri", redirectUri));
            ctx.addCookie(Cookie.cookie("JSESSIONID", sessionId.toString()));

            log.info("user not logged in goto login page !!!");
            ctx.response().setStatusCode(303);
            ctx.response().headers().add("Location", "/oauth/login");
            ctx.response().end("handled!!");
        });

        // register login page
        router.route(HttpMethod.GET, "/oauth/login").handler(ctx -> {
            log.info("call login page");
             // and now delegate to the engine to render it.
            engine.render(ctx, "WEB-INF/templates/", "login.html", templateResult -> {
                if (templateResult.succeeded()) {
                    ctx.response().end(templateResult.result());
                } else {
                    ctx.fail(templateResult.cause());
                }
            });
        });

        // register login submit endpoint
        router.route(HttpMethod.POST, "/oauth/login").handler(ctx -> {
            log.info("submit login called!!");

            HttpServerRequest request = ctx.request();
            request.setExpectMultipart(true);
            request.endHandler(req -> {
                String username = request.getFormAttribute("username");
                String password = request.getFormAttribute("password");
                log.info("user {} is now logged in with pw {} ", username, password);

                // TODO to authentication ^^

                JsonObject authObj = null;
                Cookie sessionCookie = ctx.getCookie("JSESSIONID");
                if (sessionCookie != null && loggedInUsers.containsKey(UUID.fromString(sessionCookie.getValue()))) {
                    authObj =  loggedInUsers.get(UUID.fromString(sessionCookie.getValue()));
                    ctx.response().setStatusCode(303);
                    ctx.response().headers().add("Location", authObj.getString("redirectUri"));
                }

                if (authObj == null) {
                    Map.Entry<UUID, JsonObject> entry = findEntryByName("user",username);
                    if (entry != null) {
                        authObj = entry.getValue();
                    }
                }
                authObj.put("user", username).put("token", UUID.randomUUID().toString());

                ctx.response().end(authObj.encode());
            });
        });

        router.route(HttpMethod.GET, "/oauth/logout").handler(ctx -> {
            log.info("logout called!!!");

            UUID authKey = null;
            Cookie sessionCookie = ctx.getCookie("JSESSIONID");
            if (sessionCookie != null) {
                authKey = UUID.fromString(sessionCookie.getValue());
            }

            String token = ctx.request().getParam("token");
            if (authKey == null) {
                Map.Entry<UUID, JsonObject> entry = findEntryByName("token",token);
                if (entry != null) {
                    authKey = entry.getKey();
                }
            }

            if (authKey != null) {
                JsonObject authObj = loggedInUsers.remove(authKey);
                log.info("Logout! {}", authObj.getString("user"));
                ctx.response().end("Successfully Logout!");
                return;
            }

            ctx.response().setStatusCode(500).end();
         });

        // start a HTTP web server on port 8080
        httpServer = vertx.createHttpServer();
        httpServer.requestHandler(router::accept).listen(8080);

        startFuture.complete();
    }


    public void stop(Future<Void> stopFuture) {
        log.info("stop http server...");
        httpServer.close(shutdownResult -> {
            if (shutdownResult.failed()) {
                log.error("could not stop http Server", shutdownResult.cause());
                return;
            }
            stopFuture.complete();
        });
    }


    private void loadFile(RoutingContext ctx, final String paramName, String sourcePath) {
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

    private Map.Entry<UUID, JsonObject> findEntryByName(final String key, final String name) {
      return  loggedInUsers.entrySet().stream()
                .filter(entry -> entry.getValue().getString(key).equals(name))
                .findFirst().orElse(null);
    }
}
