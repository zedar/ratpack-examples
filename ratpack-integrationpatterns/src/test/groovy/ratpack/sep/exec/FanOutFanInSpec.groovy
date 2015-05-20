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

import com.google.common.collect.ImmutableMap
import ratpack.sep.Action
import ratpack.sep.ActionResult
import ratpack.sep.ActionResults
import ratpack.sep.TypedAction
import ratpack.exec.ExecControl
import ratpack.exec.ExecResult
import ratpack.exec.Promise
import ratpack.registry.Registries
import ratpack.registry.Registry
import ratpack.sep.exec.FanOutFanIn
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.CountDownLatch


class FanOutFanInSpec extends Specification {
  static class BlockingAction implements Action<String,String> {
    private final String name
    private String data
    private CountDownLatch waitingFor
    private CountDownLatch finalizing

    BlockingAction(String name, String data, CountDownLatch waitingFor, CountDownLatch finalizing) {
      this.name = name
      this.data = data
      this.waitingFor = waitingFor
      this.finalizing = finalizing
    }

    @Override
    String getName() { return name }

    @Override
    String getData() { return data }

    @Override
    Promise<ActionResult<String>> exec(ExecControl execControl) throws Exception {
      return execControl.blocking {
        if (waitingFor) {
          waitingFor.await()
        }
        if (finalizing) {
          finalizing.countDown()
        }
        return ActionResult.success(data)
      }
    }
  }

  static class CountedResult {
    int succeded = 0
    int failed = 0
  }

  static interface Request {
  }

  static class Request1 implements Request {
    String value
    Request1(String value) { this.value = value }
  }

  static class Request2 implements Request {
    String value
    Request2(String value) { this.value = value }
  }

  static class Request3 implements Request {
    String value
    Request3(String value) { this.value = value }
  }

  static class Response {
    String value1, value2, value3, value4
  }

  @AutoCleanup
  ExecHarness harness = ExecHarness.harness()
  FanOutFanIn pattern
  Registry registry
  TypedAction<ActionResults<String>, ActionResults<CountedResult>> counterAction

  def setup() {
    pattern = new FanOutFanIn()
    registry = Registries.empty()
    counterAction = TypedAction.of("finalizer") { execControl, actionResults ->
      execControl.promise { fulfiller ->
        CountedResult countedResult = new CountedResult()
        actionResults.results?.each { k, v ->
          if (v.code == "0") {
            countedResult.succeded++
          } else {
            countedResult.failed++
          }
        }
        fulfiller.success(new ActionResults(ImmutableMap.of("counted", ActionResult.success(countedResult))))
      }
    }
  }

  def "pattern name is defined"() {
    when:
    String patternName = pattern.getName()

    then:
    FanOutFanIn.PATTERN_NAME == patternName
    "fanoutfanin" == pattern.getName()
  }

  def "fan-in action has to be defined"() {
    given:
    def actions = [Action.of("foo", { execControl ->
      execControl.promise { fulfiller ->
        fulfiller.success(ActionResult.success())}})]

    when:
    Promise<ActionResults> promise = pattern.apply(harness.control, registry, actions, null)

    then:
    thrown(NullPointerException)
  }

  def "action has to have a name"() {
    given:
    def actions = [new BlockingAction(null, null, null, null)]
    TypedAction<ActionResults, ActionResults> finalizer = TypedAction.of("finalizer", { execControl, actionResults ->
      execControl.promise { fulfiller ->
        // does nothing with results
        fulfiller.success(actionResults)
      }
    })

    when:
    ExecResult<ActionResults> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, actions, finalizer)}

    then:
    ActionResults actionResults = result.getValue()
    actionResults
    actionResults.results
    def nullPointerError = ActionResult.error(new NullPointerException())
    actionResults.results["ACTION_NULL_IDX_0"].code == nullPointerError.code
    actionResults.results["ACTION_NULL_IDX_0"].message == nullPointerError.message
  }

  def "an action provides data to fan-in finalizer"() {
    given:
    def actions = [new BlockingAction("foo", "foodata", null, null)]
    TypedAction<ActionResults<String>, ActionResults<String>> finalizer = TypedAction.of("finalizer", { execControl, actionResults ->
      execControl.promise { fulfiller ->
        // does nothing with results
        fulfiller.success(actionResults)
      }
    })

    when:
    Promise<ActionResults<String>> promise = pattern.apply(harness.control, registry, actions, finalizer)
    ExecResult<ActionResults<String>> result = harness.yield { execControl -> promise }

    then:
    ActionResults<String> actionResults = result.getValue()
    actionResults
    actionResults.results
    with(actionResults.results["foo"]) {
      code == "0"
    }
  }

  def "exception thrown from action is handled and provided to finalizer"() {
    given:
    def actions = [
        Action.of("failure1", "data") { execControl -> execControl.promise { fulfiller -> throw new IOException("failure1 exception")}},
        Action.of("failure2", "data") { execControl -> execControl.blocking { throw new IOException("failure2 exception")}}
    ]

    when:
    ExecResult<ActionResults<CountedResult>> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, actions, counterAction)
    }

    then:
    ActionResults<CountedResult> countedResult = result.getValue()
    countedResult
    countedResult.results["counted"].getData().succeded == 0
    countedResult.results["counted"].getData().failed == 2
  }

  def "exception thrown from creation of promise for result are handled and provided to finalizer"() {
    given:
    def actions = [
        Action.of("failure1") { execControl -> throw new IOException("failure1 exception") },
        Action.of("failure2") { execControl -> throw new IOException("failure2 exception") }
    ]

    when:
    ExecResult<ActionResults<CountedResult>> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, actions, counterAction)
    }

    then:
    ActionResults<CountedResult> countedResult = result.getValue()
    countedResult
    countedResult.results["counted"].getData().succeded == 0
    countedResult.results["counted"].getData().failed == 2
  }

  def "counted succeeded and failed actions"() {
    given:
    def actions = [
      Action.of("foo") { execControl -> execControl.promise { fulfiller -> fulfiller.success(ActionResult.success())}},
      Action.of("bar") { execControl -> execControl.promise { fulfiller -> fulfiller.error(new IOException())}}
    ]

    when:
    ExecResult<ActionResults<CountedResult>> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, actions, counterAction)}

    then:
    ActionResults<CountedResult> countedResult = result.getValue()
    countedResult
    countedResult.results["counted"].getData().succeded == 1
    countedResult.results["counted"].getData().failed == 1
  }

  def "parallel actions finalized and counted"() {
    given:
    def countDownLatches = []
    def actions = []
    for (int i = 0; i < 4 ; i++) {
      countDownLatches.add(new CountDownLatch(1))
    }
    for (int i = 0; i < 4 ; i++) {
      actions.add(new BlockingAction("foo_$i",
        "data",
        i >= countDownLatches.size()-1 ? null : countDownLatches[i+1],
        countDownLatches[i]))
    }

    when:
    ExecResult<ActionResults<CountedResult>> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, actions, counterAction) }

    then:
    ActionResults<CountedResult> countedResult = result.getValue()
    countedResult
    countedResult.results["counted"].getData().succeded == 4
    countedResult.results["counted"].getData().failed == 0
  }

  def "fan out requests and collect one response"() {
    given:
    def actions = [
      Action.of("req1") { ec -> ec.promise { f -> f.success(ActionResult.success(new Request1("value1")))}},
      Action.of("req2") { ec -> ec.promise { f -> f.success(ActionResult.success(new Request2("value2")))}},
      Action.of("req3") { ec -> ec.promise { f -> f.success(ActionResult.success(new Request3("value3")))}}
    ]
    TypedAction<ActionResults<Request>, ActionResults<Response>> finalizer = TypedAction.of("finalizer") { ec, actionResults ->
      ec.promise { f ->
        Response resp = new Response()
        resp.value1 = ((Request1)actionResults.results["req1"].data).value
        resp.value2 = ((Request2)actionResults.results["req2"].data).value
        resp.value3 = ((Request3)actionResults.results["req3"].data).value
        f.success(new ActionResults(ImmutableMap.of("finalizer", ActionResult.success(resp))))
      }
    }
    FanOutFanIn<Request,Request,Response> pattern = new FanOutFanIn<>()

    when:
    ExecResult<ActionResults<Response>> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, actions, finalizer) }

    then:
    ActionResults<Response> actionResults = result.getValue()
    actionResults
    with(actionResults.results["finalizer"]) {
      code == "0"
      data.value1 == "value1"
      data.value2 == "value2"
      data.value3 == "value3"
    }
  }
}