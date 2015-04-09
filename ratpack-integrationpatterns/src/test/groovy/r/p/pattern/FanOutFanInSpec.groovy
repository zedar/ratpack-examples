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
import r.p.exec.TypedAction
import ratpack.exec.ExecControl
import ratpack.exec.ExecResult
import ratpack.exec.Promise
import ratpack.registry.Registries
import ratpack.registry.Registry
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup
import spock.lang.IgnoreRest
import spock.lang.Specification

import java.util.concurrent.CountDownLatch


class FanOutFanInSpec extends Specification {
  static class BlockingAction implements Action {
    private final String name
    private CountDownLatch waitingFor
    private CountDownLatch finalizing

    BlockingAction(String name, CountDownLatch waitingFor, CountDownLatch finalizing) {
      this.name = name
      this.waitingFor = waitingFor
      this.finalizing = finalizing
    }

    @Override
    String getName() { return name }

    @Override
    Object getData() { return null }

    @Override
    Promise<ActionResult> exec(ExecControl execControl) throws Exception {
      return execControl.blocking {
        if (waitingFor) {
          waitingFor.await()
        }
        if (finalizing) {
          finalizing.countDown()
        }
        return ActionResult.success()
      }
    }
  }

  static class CountedResult {
    int succeded = 0
    int failed = 0
  }

  @AutoCleanup
  ExecHarness harness = ExecHarness.harness()
  FanOutFanIn pattern
  Registry registry
  TypedAction<ActionResults, CountedResult> counterAction

  def setup() {
    pattern = new FanOutFanIn()
    registry = Registries.just(new Parallel())
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
        fulfiller.success(countedResult)
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
    Promise<ActionResults> promise = pattern.apply(harness.control, registry, FanOutFanIn.Params.of(actions, null))

    then:
    thrown(NullPointerException)
  }

  def "action has to have a name"() {
    given:
    def actions = [new BlockingAction(null, null, null)]
    TypedAction<ActionResults, ActionResults> finalizer = TypedAction.of("finalizer", { execControl, actionResults ->
      execControl.promise { fulfiller ->
        // does nothing with results
        fulfiller.success(actionResults)
      }
    })

    when:
    ExecResult<ActionResults> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, FanOutFanIn.Params.of(actions, finalizer))}

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
    def actions = [new BlockingAction("foo", null, null)]
    TypedAction<ActionResults, ActionResults> finalizer = TypedAction.of("finalizer", { execControl, actionResults ->
      execControl.promise { fulfiller ->
        // does nothing with results
        fulfiller.success(actionResults)
      }
    })

    when:
    Promise<ActionResults> promise = pattern.apply(harness.control, registry, FanOutFanIn.Params.of(actions, finalizer))
    ExecResult<ActionResults> result = harness.yield { execControl -> promise }

    then:
    ActionResults actionResults = result.getValue()
    actionResults
    actionResults.results
    actionResults.results["foo"] == ActionResult.success()
  }

  def "exception thrown from action is handled and provided to finalizer"() {
    given:
    def actions = [
        Action.of("failure1") { execControl -> execControl.promise { fulfiller -> throw new IOException("failure1 exception")}},
        Action.of("failure2") { execControl -> execControl.blocking { throw new IOException("failure2 exception")}}
    ]

    when:
    ExecResult<CountedResult> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, FanOutFanIn.Params.of(actions, counterAction))
    }

    then:
    CountedResult countedResult = result.getValue()
    countedResult
    countedResult.succeded == 0
    countedResult.failed == 2
  }

  def "exception thrown from creation of promise for result are handled and provided to finalizer"() {
    given:
    def actions = [
        Action.of("failure1") { execControl -> throw new IOException("failure1 exception") },
        Action.of("failure2") { execControl -> throw new IOException("failure2 exception") }
    ]

    when:
    ExecResult<CountedResult> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, FanOutFanIn.Params.of(actions, counterAction))
    }

    then:
    CountedResult countedResult = result.getValue()
    countedResult
    countedResult.succeded == 0
    countedResult.failed == 2
  }

  def "counted succeeded and failed actions"() {
    given:
    def actions = [
      Action.of("foo") { execControl -> execControl.promise { fulfiller -> fulfiller.success(ActionResult.success())}},
      Action.of("bar") { execControl -> execControl.promise { fulfiller -> fulfiller.error(new IOException())}}
    ]

    when:
    ExecResult<CountedResult> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, FanOutFanIn.Params.of(actions, counterAction))}

    then:
    CountedResult countedResult = result.getValue()
    countedResult
    countedResult.succeded == 1
    countedResult.failed == 1
  }

  def "parallel actions finalized and counted"() {
    given:
    def countDownLatches = []
    def actions = []
    for (int i = 0; i < 4 ; i++) {
      countDownLatches.add(new CountDownLatch(1))
    }
    for (int i = 0; i < 4 ; i++) {
      actions.add(new BlockingAction("foo_$i", i >= countDownLatches.size()-1 ? null : countDownLatches[i+1], countDownLatches[i]))
    }

    when:
    ExecResult<CountedResult> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, FanOutFanIn.Params.of(actions, counterAction)) }

    then:
    CountedResult countedResult = result.getValue()
    countedResult
    countedResult.succeded == 4
    countedResult.failed == 0
  }
}