package r.p.handling;

import com.google.common.collect.ImmutableMap;
import r.p.exec.Action;

public class ExecResults {
  private final ImmutableMap<String, Action.Result> results;

  public ExecResults(ImmutableMap<String, Action.Result> results) {
    this.results = results;
  }

  public ImmutableMap<String, Action.Result> getResults() { return results; }
}
