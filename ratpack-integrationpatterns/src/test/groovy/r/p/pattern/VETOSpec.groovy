/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package r.p.pattern

import r.p.exec.Action
import r.p.exec.ActionResult
import r.p.exec.ActionResults
import ratpack.exec.ExecResult
import ratpack.registry.Registries
import ratpack.registry.Registry
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup
import spock.lang.IgnoreRest
import spock.lang.Specification

class VETOSpec extends Specification {

  static class RequestData {
    String value1
    String value2
    String value3
  }

  @AutoCleanup
  ExecHarness harness = ExecHarness.harness()
  Registry registry

  def setup() {
    registry = Registries.just(new InvokeWithRetry(0))
  }

  @IgnoreRest
  def "verify, enrich, transform and operate"() {
    given:
    RequestData requestData = [value1: "value1", value2: "value2"]
    InvokeWithRetry pattern = registry.get(InvokeWithRetry.class)
    Action verify = Action.of("verify", requestData) { execControl -> execControl.promise { fulfiller ->
      if (!requestData.value1 || !requestData.value2) {
        fulfiller.error(new MissingFormatArgumentException("alpha or beta"))
      } else {
        fulfiller.success(ActionResult.success())
      }
    }}
    Action enrich = Action.of("enrich", requestData) { execControl -> execControl.promise { fulfiller ->
      requestData.value3 = "value3"
      fulfiller.success(ActionResult.success())
    }}

    when:
    ExecResult<ActionResults> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, InvokeWithRetry.Params.of(verify))
        .flatMap { actionResults ->
          pattern.apply(execControl, registry, InvokeWithRetry.Params.of(enrich))
        }
      }

    then:
    ActionResults actionResults = result.getValue()
    actionResults.results.size() == 1
    with(requestData) {
      value1 == "value1"
      value2 == "value2"
      value3 == "value3"
    }
  }
}