package r.p.exec;

import ratpack.api.Nullable;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.func.Function;
import ratpack.health.HealthCheck;

public interface Action {
  String getName();

  Promise<Result> exec(ExecControl execControl) throws Exception;

  public static Action of(String name, Function<? super ExecControl, ? extends Promise<Result>> func) {
    return new Action() {
      @Override
      public String getName() { return name; }

      @Override
      public Promise<Result> exec(ExecControl execControl) throws Exception {
        return func.apply(execControl);
      }
    };
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
