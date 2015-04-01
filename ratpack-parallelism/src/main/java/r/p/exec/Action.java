package r.p.exec;

import ratpack.api.Nullable;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.health.HealthCheck;

public class Action {
  private final String name;

  public Action(String name) {
    this.name = name;
  }

  public String getName() { return name; }

  public Promise<Result> exec(ExecControl execControl) throws Exception {
    return execControl.promise(fulfiller -> {
      // TODO:
      Thread.sleep(3000);
      fulfiller.success(Result.NO_ERROR);
    });
  }

  public static class Result {
    public static final Result NO_ERROR = new Result("0", null);

    private final String code;
    private final String descr;

    private Result(String code, String descr) {
      this.code = code;
      this.descr = descr;
    }

    public String getCode() { return code; }

    @Nullable
    public String getDescr() { return descr; }

    public static Result error(String code, String descr) { return new Result(code, descr); }

    public static Result error(Throwable ex) { return new Result("100", ex.getMessage()); }
  }
}
