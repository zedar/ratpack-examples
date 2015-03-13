package ratpack.health

import ratpack.exec.ExecControl
import ratpack.exec.Promise
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.test.embed.EmbeddedApp
import ratpack.test.http.TestHttpClient
import spock.lang.Specification

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

  def "duplicated health checks renders only once"() {

  }

  def "render health check by token if more health checks in registry"() {

  }

  def "render json health check results for custom renderer"() {

  }

  def "render ordered health checks for parallel executions"() {

  }
}
