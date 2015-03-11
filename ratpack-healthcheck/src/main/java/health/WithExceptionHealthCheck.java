package health;

import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.health.HealthCheck;

import java.io.IOException;

public class WithExceptionHealthCheck implements HealthCheck {
  @Override
  public String getName() {
    return "WithExceptionHealthCheck";
  }

  @Override
  public Promise<Result> check(ExecControl execControl) throws Exception {
    return execControl.promise(f -> {
      throw new IOException("Promise did not return value. Just crashed");
    });
  }
}
