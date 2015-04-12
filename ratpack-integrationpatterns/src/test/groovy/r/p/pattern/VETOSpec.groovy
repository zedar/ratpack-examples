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
import ratpack.exec.ExecControl
import ratpack.exec.ExecResult
import ratpack.exec.Promise
import ratpack.registry.Registries
import ratpack.registry.Registry
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup
import spock.lang.IgnoreRest
import spock.lang.Specification
import sun.invoke.util.VerifyAccess

class VETOSpec extends Specification {

  static class RequestData {
    String value1
    String value2
    String value3
  }

  static class VerifyAction implements Action<RequestData,RequestData> {
    RequestData data

    @Override
    String getName() { return "verify" }

    @Override
    Promise<ActionResult<RequestData>> exec(ExecControl execControl) throws Exception {
      return execControl.promise { fulfiller ->
        if (!data.value1 || !data.value2) {
          fulfiller.error(new MissingFormatArgumentException("alpha or beta"))
        } else {
          fulfiller.success(ActionResult.success(data))
        }
      }
    }
  }

  static class EnrichAction implements Action<RequestData,RequestData> {
    RequestData data
    @Override
    String getName() { return "enrich" }

    @Override
    Promise<ActionResult<RequestData>> exec(ExecControl execControl) throws Exception {
      return execControl.promise{ fulfiller ->
        data.value3 = "value3"
        fulfiller.success(ActionResult.success(data))
      }
    }
  }

  @AutoCleanup
  ExecHarness harness = ExecHarness.harness()
  Registry registry

  def setup() {
    registry = Registries.empty()
  }

  @IgnoreRest
  def "verify, enrich, transform and operate"() {
    given:
    RequestData requestData = [value1: "value1", value2: "value2"]
    InvokeWithRetry pattern = new InvokeWithRetry(0)
    VerifyAction verifyAction = new VerifyAction()
    verifyAction.data = requestData
    EnrichAction enrichAction = new EnrichAction()

    when:
    ExecResult<ActionResults<RequestData>> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, verifyAction)
        .flatMap { actionResults ->
          enrichAction.data = actionResults.results["verify"].data
          pattern.apply(execControl, registry, enrichAction)
        }
      }

    then:
    ActionResults<RequestData> actionResults = result.getValue()
    actionResults.results.size() == 1
    with(actionResults.results["enrich"].data) {
      value1 == "value1"
      value2 == "value2"
      value3 == "value3"
    }
  }
}