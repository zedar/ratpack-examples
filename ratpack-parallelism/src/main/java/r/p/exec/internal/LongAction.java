package r.p.exec.internal;

import r.p.exec.Action;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;

public class LongAction implements Action {
  private final String name;

  public LongAction(String name) { this.name = name; }

  @Override
  public String getName() { return name; }

  @Override
  public Promise<Result> exec(ExecControl execControl) throws Exception {
    return execControl.promise(fulfiller -> {
      // TODO:
      Thread.sleep(3000);
      fulfiller.success(Result.NO_ERROR);
    });
  }
}
