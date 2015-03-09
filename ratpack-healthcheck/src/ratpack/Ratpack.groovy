import static ratpack.groovy.Groovy.ratpack

import ratpack.logging.MDCInterceptor

ratpack {
  bindings {
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
  }
}
