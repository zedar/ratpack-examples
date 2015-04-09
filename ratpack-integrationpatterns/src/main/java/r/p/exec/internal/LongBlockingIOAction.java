package r.p.exec.internal;

import r.p.exec.Action;
import r.p.exec.ActionResult;
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
  private final String data;

  public LongBlockingIOAction(String name, String data) {
    this.name = name;
    this.data = data;
  }

  @Override
  public String getName() { return name; }

  @Override
  public Object getData() { return data; }

  @Override
  public Promise<ActionResult> exec(ExecControl execControl) throws Exception {
    return execControl.blocking(() -> {
      Thread.sleep(3000);
      return ActionResult.success(data);
    });
  }
}
