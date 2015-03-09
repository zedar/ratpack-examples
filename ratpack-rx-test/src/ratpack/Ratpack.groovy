import m4.exec.AsyncRx
import ratpack.logging.MDCInterceptor
import ratpack.rx.RxRatpack

import static ratpack.groovy.Groovy.ratpack

ratpack {
  RxRatpack.initialize()

  bindings {
    bind AsyncRx.class
  }

  handlers {
    handler {
      addInterceptor(new MDCInterceptor()) {
        next()
      }
    }
    get("api/async", AsyncRx.class)
        
    assets "public"
  }
}
