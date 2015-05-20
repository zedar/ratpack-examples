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

package ratpack.sep.exec

import ratpack.sep.Action
import ratpack.sep.ActionResult
import ratpack.sep.ActionResults
import ratpack.exec.ExecControl
import ratpack.exec.ExecResult
import ratpack.exec.Promise
import ratpack.registry.Registries
import ratpack.registry.Registry
import ratpack.sep.exec.InvokeWithRetry
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

  static class ResponseData {
    String value
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

  def "verify combined with enrich return combined result"() {
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

  @IgnoreRest
  def "verify combined with operate defined with factory methods"() {
    given:
    RequestData reqData = [value1: "val1"]
    InvokeWithRetry<RequestData,RequestData> verify = new InvokeWithRetry<>(0)

    when:
    ExecResult<ActionResults<ResponseData>> result = harness.yield { ec ->
      verify.apply(ec, registry, Action.<RequestData,RequestData>of("verify") { ec2 -> ec2.promise { f ->
        if (!reqData.value1 || !reqData.value2) {
          f.error(new MissingFormatArgumentException("value1 or value2 is required"))
        } else {
          f.success(ActionResult.success(reqData))
        }
      }})
      .flatMap { actionResults ->
        InvokeWithRetry<RequestData,ResponseData> operate = new InvokeWithRetry<>(3)
        operate.apply(ec, registry, Action.<RequestData,ResponseData>of("operate") { ec2 -> ec2.promise { f->
          ResponseData resData = [value: "operate_value"]
          f.success(ActionResult.success(resData))
        }})
      }
    }

    then:
    ActionResults<ResponseData> actionResults = result.getValue()
    actionResults.results.size() == 1
    with (actionResults.results["operate"].data) {
      value == "operate_value"
    }
  }
}