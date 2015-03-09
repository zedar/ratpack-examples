package m4.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ratpack.exec.Promise;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.rx.RxRatpack;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.schedulers.Schedulers;

import java.util.List;

class AsyncRx implements Handler {
  private static final Logger log = LoggerFactory.getLogger(AsyncRx.class);

  private static class ValidationException extends Error {

  }

  @Override
  public void handle(Context context) throws Exception {
    MDC.put("clientId", "AsyncRx");
    log.debug("Handle");
    context.promise(f -> {
      // verify -> enrich -> transform -> operate
      Observable.<String>create(s -> s.onNext("te"))
              .map(str -> {
                if (str.length() < 3) {
                  throw new ValidationException();
                }
                return str;
              })
              .subscribe(str -> {
                log.debug("RETURN: " + str);
                f.success("str");
              },
              ex -> {
                log.error(ex.toString());
                f.error(ex);
              });
    }).onError(ex -> {
      log.error("VETO ERROR");
    }).then(str -> {
      log.debug("VETO SUCCESS: " + str);
    });

    context.promise(f -> {
      Observable<List<String>> lst = Observable.merge(oper1(context), oper2(context)).toList();
      lst.subscribe((List<String> list) -> {
        StringBuilder strb = new StringBuilder();
        list.forEach(s -> strb.append(s).append(" "));
        log.debug(" OUTPUT: " + list);
        f.success(strb.toString());
      });
    }).then(s -> {
      log.debug("RENDER: " + s);
      context.render(s);
    });
  }

  private Observable<String> oper1(Context context) {
    // blocking with return value throws exception
    // promise with fullfiler.success just works
    Promise<String> promise = context.promise(f -> {
      log.debug("oper1");
      Thread.sleep(7000);
      f.success("oper11");
    });
    return RxRatpack.observe(promise).subscribeOn(Schedulers.computation());

//    return Observable.<String>create(s -> {
//      promise
//        .onError(throwable -> s.onError(throwable))
//        .then(str -> {
//          s.onNext(str);
//          s.onCompleted();
//        });
//    }).subscribeOn(Schedulers.computation());

//    return Observable.<String>create(s -> {
//      log.debug("oper1");
//      try {
//        Thread.sleep(7000);
//      }
//      catch(InterruptedException ex) {
//        throw new RuntimeException(ex);
//      }
//      s.onNext("oper1");
//      s.onCompleted();
//    }).subscribeOn(Schedulers.computation());
  }

  private Observable<String> oper2(Context context) {
//    return RxRatpack.observe(context.blocking(() -> {
//      log.debug("oper2");
//      Thread.sleep(7000);
//      return "oper2";
//    })).subscribeOn(Schedulers.computation());
    return Observable.<String>create(s -> {
      log.debug("oper2");
      try {
        Thread.sleep(7000);
      } catch (InterruptedException ex) {
        throw new RuntimeException(ex);
      }
      s.onNext("oper2");
      s.onCompleted();
    }).subscribeOn(Schedulers.computation());
  }
}
