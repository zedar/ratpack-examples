import health.BarHealthCheck
import health.FooHealthCheck
import ratpack.health.HealthCheck
import ratpack.health.HealthCheckHandler
import ratpack.health.HealthCheckResultsRenderer

import static ratpack.groovy.Groovy.context
import static ratpack.groovy.Groovy.ratpack

import ratpack.logging.MDCInterceptor

ratpack {
  bindings {
    bind HealthCheckResultsRenderer
    bind FooHealthCheck
    bind BarHealthCheck
    bindInstance(HealthCheck, HealthCheck.of("foo1") { ec ->
      return ec.promise {f ->
        f.success(HealthCheck.Result.healthy("fooooo1"))
      }
    })
  }

  handlers {
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
