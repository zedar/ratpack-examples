package ratpack.health

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import ratpack.exec.ExecControl
import ratpack.exec.Promise
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.http.MediaType
import ratpack.render.Renderer
import ratpack.test.embed.EmbeddedApp
import ratpack.test.http.TestHttpClient
import spock.lang.IgnoreRest
import spock.lang.Specification

import java.util.concurrent.CountDownLatch

class HealthCheckFooHealthy implements HealthCheck {
  String getName() { return "foo" }

  Promise<HealthCheck.Result> check(ExecControl execControl) throws Exception {
    return execControl.promise { f ->
      f.success(HealthCheck.Result.healthy())
    }
  }
}

class HealthCheckBarHealthy implements HealthCheck {
  String getName() { return "bar" }

  Promise<HealthCheck.Result> check(ExecControl execControl) throws Exception {
    return execControl.promise { f ->
      f.success(HealthCheck.Result.healthy())
    }
  }
}


class HealthCheckFooUnhealthy implements HealthCheck {
  String getName() { return "foo" }

  Promise<HealthCheck.Result> check(ExecControl execControl) throws Exception {
    return execControl.promise { f ->
      f.success(HealthCheck.Result.unhealthy("EXECUTION TIMEOUT"))
    }
  }
}

class HealthCheckFooUnhealthy2 implements HealthCheck {
  String getName() { return "foo"}

  Promise<HealthCheck.Result> check(ExecControl execControl) throws Exception {
    throw new Exception("EXCEPTION PROMISE CREATION")
  }
}

class HealthCheckParallel implements HealthCheck {
  private final String name
  private CountDownLatch waitingFor
  private CountDownLatch finalized
  private List<String> output

  HealthCheckParallel(String name, CountDownLatch waitingFor, CountDownLatch finalized, List<String> output) {
    this.name = name
    this.waitingFor = waitingFor
    this.finalized = finalized
    this.output = output
  }

  String getName() { return this.name }

  Promise<HealthCheck.Result> check(ExecControl execControl) throws Exception {
    return execControl.promise { f ->
      if (waitingFor) {
        println "WAITING: $name"
        waitingFor.await()
      }
      f.success(HealthCheck.Result.healthy())
      if (finalized) {
        println "FINALIZED: $name"
        output << name
        finalized.countDown()
      }
    }
  }
}

class HealthCheckHandlerSpec extends Specification {
  def "render healthy check"() {
    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {
      bindings {
        bind HealthCheckFooHealthy
      }
      handlers {
        register {
          add new HealthCheckResultsRenderer()
        }
        get("health-checks", new HealthCheckHandler())
      }
    }

    then:
    app.test { TestHttpClient httpClient ->
      def result = httpClient.getText("health-checks")
      assert result.startsWith("foo")
      assert result.contains("HEALTHY")
    }
  }

  def "render unhealthy check"() {
    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {
      bindings {
        bind HealthCheckFooUnhealthy
      }
      handlers {
        register {
          add new HealthCheckResultsRenderer()
        }
        get("health-checks", new HealthCheckHandler())
      }
    }

    then:
    app.test { TestHttpClient httpClient ->
      def result = httpClient.getText("health-checks")
      assert result.startsWith("foo")
      assert result.contains("UNHEALTHY")
      assert result.contains("EXECUTION TIMEOUT")
    }
  }

  def "render unhealthy check while promise itself throwning exception"() {
    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {
      bindings {
        bindInstance(HealthCheck, HealthCheck.of("bar") { execControl ->
          execControl.promise { f ->
            throw new Exception("EXCEPTION FROM PROMISE")
          }
        })
      }
      handlers {
        register {
          add new HealthCheckResultsRenderer()
        }
        get("health-checks", new HealthCheckHandler())
      }
    }

    then:
    app.test { TestHttpClient httpClient ->
      def result = httpClient.getText("health-checks")
      assert result.startsWith("bar")
      assert result.contains("UNHEALTHY")
      assert result.contains("EXCEPTION FROM PROMISE")
      assert result.contains("Exception")
    }
  }

  def "render unhealthy check while promise creation throwning exception"() {
    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {
      bindings {
        bind HealthCheckFooUnhealthy2
      }
      handlers {
        register {
          add new HealthCheckResultsRenderer()
        }
        get("health-checks", new HealthCheckHandler())
      }
    }

    then:
    app.test { TestHttpClient httpClient ->
      def result = httpClient.getText("health-checks")
      assert result.startsWith("foo")
      assert result.contains("UNHEALTHY")
      assert result.contains("EXCEPTION PROMISE CREATION")
    }
  }

  def "render nothing if no health check in registry"() {
    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {
      handlers {
        register {
          add new HealthCheckResultsRenderer()
        }
        get("health-checks", new HealthCheckHandler())
      }
    }

    then:
    app.test { TestHttpClient httpClient ->
      assert httpClient.getText("health-checks").isEmpty()
    }
  }

  def "render healthy check results for more health checks"() {
    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {
      bindings {
        bind HealthCheckFooHealthy
        bind HealthCheckBarHealthy
      }
      handlers {
        register {
          add new HealthCheckResultsRenderer()
          add HealthCheck.of("baz") { ec ->
            ec.promise { f ->
              f.success(HealthCheck.Result.healthy())
            }
          }
          add HealthCheck.of("quux") { ec ->
            ec.promise { f ->
              f.success(HealthCheck.Result.healthy())
            }
          }
        }
        get("health-checks", new HealthCheckHandler())
        get("health-checks/:name") { ctx ->
          new HealthCheckHandler(pathTokens["name"]).handle(ctx)
        }
      }
    }

    then:
    app.test { TestHttpClient httpClient ->
      def result = httpClient.getText("health-checks")
      //assert result == "zzz"
      String[] results = result.split("\n")
      assert results.length == 4
      assert results[0].startsWith("bar")
      assert results[0].contains("HEALTHY")
      assert results[1].startsWith("baz")
      assert results[1].contains("HEALTHY")
      assert results[2].startsWith("foo")
      assert results[2].contains("HEALTHY")
      assert results[3].startsWith("quux")
      assert results[3].contains("HEALTHY")
    }
  }

  def "health checks run in parallel"() {
    given:
    CountDownLatch latch = new CountDownLatch(1)

    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {
      handlers {
        register {
          add new HealthCheckResultsRenderer()
          add HealthCheck.of("baz") { ec ->
            ec.promise { f ->
              latch.await()
              f.success(HealthCheck.Result.healthy())
            }
          }
          add HealthCheck.of("quux") { ec ->
            ec.promise { f ->
              latch.countDown()
              f.success(HealthCheck.Result.healthy())
            }
          }
        }
        get("health-checks", new HealthCheckHandler())
      }
    }
    then:
    app.test { TestHttpClient httpClient ->
      def result = httpClient.getText("health-checks")
      String[] results = result.split("\n")
      assert results[0].startsWith("baz")
      assert results[0].contains("HEALTHY")
      assert results[1].startsWith("quux")
      assert results[1].contains("HEALTHY")
    }
  }

  def "duplicated health checks renders only once"() {
    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {
      bindings {
        bind HealthCheckFooHealthy
        bind HealthCheckFooHealthy
      }
      handlers {
        register {
          add new HealthCheckResultsRenderer()
          add HealthCheck.of("foo") { ec ->
            ec.promise { f ->
              latch.await()
              f.success(HealthCheck.Result.unhealthy("Unhealthy"))
            }
          }
        }
        get("health-checks", new HealthCheckHandler())
      }
    }
    then:
    app.test { TestHttpClient httpClient ->
      def result = httpClient.getText("health-checks")
      String[] results = result.split("\n")
      assert results.size() == 1
      assert results[0].startsWith("foo")
      assert results[0].contains("HEALTHY")
    }
  }

  def "render health check by token if more health checks in registry"() {
    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {
      bindings {
        bind HealthCheckFooHealthy
        bind HealthCheckBarHealthy
      }
      handlers {
        register {
          add new HealthCheckResultsRenderer()
          add HealthCheck.of("baz") { ec ->
            ec.promise { f ->
              f.success(HealthCheck.Result.unhealthy("Unhealthy"))
            }
          }
          add HealthCheck.of("quux") { ec ->
            ec.promise { f ->
              f.success(HealthCheck.Result.healthy())
            }
          }
        }
        get("health-checks/:name") { ctx ->
          new HealthCheckHandler(pathTokens["name"]).handle(ctx)
        }
      }
    }

    then:
    app.test { TestHttpClient httpClient ->
      def result = httpClient.getText("health-checks/foo")
      String[] results = result.split("\n")
      assert results.length == 1
      assert results[0].startsWith("foo")
      assert results[0].contains("HEALTHY")

      result = httpClient.getText("health-checks/baz")
      results = result.split("\n")
      assert results[0].startsWith("baz")
      assert results[0].contains("UNHEALTHY")
    }
  }

  def "render json health check results for custom renderer"() {
    given:
    def json = new JsonSlurper()

    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {
      bindings {
        bind HealthCheckFooHealthy
        bind HealthCheckBarHealthy
      }
      handlers {
        register {
          add(Renderer.of(HealthCheckResults) { ctx, r ->
            def headers = ctx.request.headers
            if (headers?.get("Accept") == "application/json" || headers?.get("Content-Type") == "application/json") {
              ctx.response.headers
                      .add("Cache-Control", "no-cache, no-store, must-revalidate")
                      .add("Pragma", "no-cache")
                      .add("Expires", 0)
                      .add("Content-Type", "application/json")
              ctx.render(JsonOutput.toJson(r.getResults()))
            }
            else {
              // no caching headers are set inside default renderer
              new HealthCheckResultsRenderer().render(ctx, r)
            }
          })
          add HealthCheck.of("baz") { ec ->
            ec.promise { f ->
              f.success(HealthCheck.Result.unhealthy("Unhealthy"))
            }
          }
        }
        get("health-checks", new HealthCheckHandler())
      }
    }

    then:
    app.test{TestHttpClient httpClient ->
      httpClient.requestSpec{ spec ->
        spec.headers.add("Accept", "application/json")
      }
      def result = httpClient.get("health-checks")
      assert result.body.contentType.toString() == MediaType.APPLICATION_JSON
      def results = json.parse(result.body.inputStream)
      assert results.foo.healthy == true
      assert results.bar.healthy == true
      assert results.baz.healthy == false
      assert results.baz.message == "Unhealthy"
    }
  }

  @IgnoreRest
  def "ordered (by name) health checks run in parallel"() {
    given:
    CountDownLatch cdlFoo1 = new CountDownLatch(1)
    CountDownLatch cdlFoo2 = new CountDownLatch(1)
    CountDownLatch cdlFoo3 = new CountDownLatch(1)
    CountDownLatch cdlFoo4 = new CountDownLatch(1)
    CountDownLatch cdlFoo5 = new CountDownLatch(1)
    CountDownLatch cdlFoo6 = new CountDownLatch(1)
    CountDownLatch cdlFoo7 = new CountDownLatch(1)
    CountDownLatch cdlFoo8 = new CountDownLatch(1)
    CountDownLatch cdlFoo9 = new CountDownLatch(1)
    def output = []

    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {

      handlers {
        register {
          add new HealthCheckResultsRenderer()
          add new HealthCheckParallel("foo1", cdlFoo9, cdlFoo1, output)
          add new HealthCheckParallel("foo2", cdlFoo8, cdlFoo2, output)
          add new HealthCheckParallel("foo3", cdlFoo7, cdlFoo3, output)
          add new HealthCheckParallel("foo4", cdlFoo6, cdlFoo4, output)
          add new HealthCheckParallel("foo5", cdlFoo5, cdlFoo5, output)
          add new HealthCheckParallel("foo6", cdlFoo4, cdlFoo6, output)
          add new HealthCheckParallel("foo7", cdlFoo3, cdlFoo7, output)
          add new HealthCheckParallel("foo8", cdlFoo2, cdlFoo8, output)
          add new HealthCheckParallel("foo9", null, cdlFoo9, output)
        }
        get("health-checks", new HealthCheckHandler(0))
      }
    }

    then:
    app.test { TestHttpClient httpClient ->
      def result = httpClient.getText("health-checks")
    }
  }
}
