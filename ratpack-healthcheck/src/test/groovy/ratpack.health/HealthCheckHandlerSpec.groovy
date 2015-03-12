package ratpack.health

import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.test.embed.EmbeddedApp
import spock.lang.Specification

class HealthCheckHandlerSpec extends Specification {
  def "First test"() {
    given:

    when:
    EmbeddedApp app = GroovyEmbeddedApp.build {
      hand
    }
  }
}
