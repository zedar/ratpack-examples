package r.p.handling;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import r.p.exec.Action;
import r.p.exec.internal.LongAction;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecHandler implements Handler {
  @Override
  public void handle(Context ctx) throws Exception {
    ctx.getResponse().getHeaders()
      .add("Cache-Control", "no-cache, no-store, must-revalidate")
      .add("Pragma", "no-cache")
      .add("Expires", "0");

    try {
      Iterable<Action> actions = new LinkedList<>(Arrays.asList(
        new LongAction("foo"),
        new LongAction("bar"),
        Action.of("buzz", execControl -> execControl
          .promise(fulfiller -> {
            throw new IOException("CONTROLLED EXCEPTION");
          })),
        new LongAction("quzz"),
        new LongAction("foo_1"),
        new LongAction("foo_2"),
        new LongAction("foo_3"),
        new LongAction("foo_4"),
        new LongAction("foo_5"),
        new LongAction("foo_6")
      ));
      ctx.render(execute(ctx, actions));
    } catch (Exception ex) {
      ctx.error(ex);
    }
  }

  private Promise<ExecResults> execute(ExecControl execControl, Iterable<? extends Action> actions) {
    Iterator<? extends Action> iterator = actions.iterator();
    if (!iterator.hasNext()) {
      return execControl.promiseOf(new ExecResults(ImmutableMap.<String, Action.Result>of()));
    }

    return execControl.<Map<String, Action.Result>>promise(f -> {
      AtomicInteger counter = new AtomicInteger();
      Map<String, Action.Result> results = Maps.newConcurrentMap();
      while (iterator.hasNext()) {
        counter.incrementAndGet();
        Action action = iterator.next();
        execControl.exec().start(execution ->
          execute(execution, action)
            .defer(r -> {
              System.out.println("DEFER " + action.getName());
              r.run();
            })
            .wiretap(r -> {
              System.out.println("WIRETAP " + action.getName());
            })
            .then(r -> {
              System.out.println("THEN " + action.getName());
              results.put(action.getName(), r);
              if (counter.decrementAndGet() == 0 && !iterator.hasNext()) {
                f.success(results);
              }
            })
        );
      }
    })
      .map(ImmutableMap::copyOf)
      .map(ExecResults::new);
  }

  private Promise<Action.Result> execute(ExecControl execControl, Action action) {
    try {
      return action.exec(execControl).mapError(Action.Result::error);
    } catch (Exception ex) {
      return execControl.promiseOf(Action.Result.error(ex));
    }
  }
}
