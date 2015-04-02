package r.p.exec.internal;

import r.p.exec.Action;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;

/**
 * Example of long running blocking IO.
 * <p>
 * Because it is blocking so instead of {@code execControl.promise()}, {@code execControl.blocking()} method is used.
 * Blocking operation will be performed on a thread from a special thread pool and not on threads from main
 * compute event loop.
 */
public class LongBlockingIOAction implements Action {
  private final String name;

  public LongBlockingIOAction(String name) { this.name = name; }

  @Override
  public String getName() { return name; }

  @Override
  public Promise<Result> exec(ExecControl execControl) throws Exception {
    return execControl.blocking(() -> {
      Thread.sleep(3000);
      return Result.NO_ERROR;
    });
//    promise(fulfiller -> {
//      // TODO:
//      Thread.sleep(3000);
//      fulfiller.success(Result.NO_ERROR);
//    });
  }
}
