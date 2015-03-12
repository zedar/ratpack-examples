import groovy.json.JsonOutput
import health.BarHealthCheck
import health.FooHealthCheck
import health.WithExceptionHealthCheck
import ratpack.health.HealthCheck
import ratpack.health.HealthCheckHandler
import ratpack.health.HealthCheckResults
import ratpack.health.HealthCheckResultsRenderer
import ratpack.render.Renderer

import static ratpack.groovy.Groovy.context
import static ratpack.groovy.Groovy.ratpack

import ratpack.logging.MDCInterceptor

ratpack {
  bindings {
    bind FooHealthCheck
    bind BarHealthCheck
    bindInstance(HealthCheck, HealthCheck.of("foo1") { ec ->
      return ec.promise {f ->
        f.success(HealthCheck.Result.healthy("fooooo1"))
      }
    })
    bind WithExceptionHealthCheck
  }

  handlers {
    register {
      // O
      add(Renderer.of(HealthCheckResults) { ctx, r ->
        if (ctx.getRequest().getHeaders()?.get("Accept") == "application/json") {
          ctx.response.headers
            .add("Cache-Control", "no-cache, no-store, must-revalidate")
            .add("Pragma", "no-cache")
            .add("Expires", 0)
          ctx.render(JsonOutput.toJson(r.getResults()))
        }
        else {
          // no caching headers are set inside default renderer
          new HealthCheckResultsRenderer().render(ctx, r)
        }
      })
      //add new HealthCheckResultsRenderer()
    }
    handler {
      // register interceptor for SLF4J MDC support
      addInterceptor(new MDCInterceptor()) {
        next()
      }
    }

    get("api") {
      render "API"
    }

    get("health-checks", new HealthCheckHandler())

    get("health-checks/:name") { ctx ->
      new HealthCheckHandler(pathTokens["name"]).handle(ctx)
    }
  }
}
