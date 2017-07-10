
package org.isomorphism.limit.limiter.impl;

import com.google.common.collect.ObjectArrays;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.common.util.concurrent.Uninterruptibles;
import org.isomorphism.limit.TimeLimiter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A TimeLimiter that runs method calls in the background using an {@link ExecutorService}. If the
 * time limit expires for a given method call, the thread running the call will be interrupted.
 *
 * @author Kevin Bourrillion
 * @author Jens Nyman
 * @since 1.0
 */
public final class SimpleTimeLimiter implements TimeLimiter {

  private final ExecutorService executor;

  /**
   * Constructs a TimeLimiter instance using the given executor service to execute proxied method
   * calls.
   *
   * <p><b>Warning:</b> using a bounded executor may be counterproductive! If the thread pool fills
   * up, any time callers spend waiting for a thread may count toward their time limit, and in this
   * case the call may even time out before the target method is ever invoked.
   *
   * @param executor the ExecutorService that will execute the method calls on the target objects;
   *     for example, a {@link Executors#newCachedThreadPool()}.
   * @deprecated Use {@link #create(ExecutorService)} instead. This method is scheduled to be
   *     removed in Guava 23.0.
   */
  @Deprecated
  public SimpleTimeLimiter(ExecutorService executor) {
    this.executor = checkNotNull(executor);
  }

  /**
   * Constructs a TimeLimiter instance using a {@link Executors#newCachedThreadPool()} to execute
   * proxied method calls.
   *
   * <p><b>Warning:</b> using a bounded executor may be counterproductive! If the thread pool fills
   * up, any time callers spend waiting for a thread may count toward their time limit, and in this
   * case the call may even time out before the target method is ever invoked.
   *
   * @deprecated Use {@link #create(ExecutorService)} instead with {@code
   *     Executors.newCachedThreadPool()}. This method is scheduled to be removed in Guava 23.0.
   */
  @Deprecated
  public SimpleTimeLimiter() {
    this(Executors.newCachedThreadPool());
  }

  /**
   * Creates a TimeLimiter instance using the given executor service to execute method calls.
   *
   * <p><b>Warning:</b> using a bounded executor may be counterproductive! If the thread pool fills
   * up, any time callers spend waiting for a thread may count toward their time limit, and in this
   * case the call may even time out before the target method is ever invoked.
   *
   * @param executor the ExecutorService that will execute the method calls on the target objects;
   *     for example, a {@link Executors#newCachedThreadPool()}.
   * @since 22.0
   */
  public static com.google.common.util.concurrent.SimpleTimeLimiter create(ExecutorService executor) {
    return new com.google.common.util.concurrent.SimpleTimeLimiter(executor);
  }

  @Override
  public <T> T newProxy(
      final T target,
      Class<T> interfaceType,
      final long timeoutDuration,
      final TimeUnit timeoutUnit) {
    checkNotNull(target);
    checkNotNull(interfaceType);
    checkNotNull(timeoutUnit);
    checkPositiveTimeout(timeoutDuration);
    checkArgument(interfaceType.isInterface(), "interfaceType must be an interface type");

    final Set<Method> interruptibleMethods = findInterruptibleMethods(interfaceType);

    InvocationHandler handler =
        new InvocationHandler() {
          @Override
          public Object invoke(Object obj, final Method method, final Object[] args)
              throws Throwable {
            Callable<Object> callable =
                new Callable<Object>() {
                  @Override
                  public Object call() throws Exception {
                    try {
                      return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                      throw throwCause(e, false /* combineStackTraces */);
                    }
                  }
                };
            return callWithTimeout(
                callable, timeoutDuration, timeoutUnit, interruptibleMethods.contains(method));
          }
        };
    return newProxy(interfaceType, handler);
  }

  // TODO: should this actually throw only ExecutionException?
  @Deprecated
  @Override
  public <T> T callWithTimeout(
      Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit, boolean amInterruptible)
      throws Exception {
    checkNotNull(callable);
    checkNotNull(timeoutUnit);
    checkPositiveTimeout(timeoutDuration);

    Future<T> future = executor.submit(callable);

    try {
      if (amInterruptible) {
        try {
          return future.get(timeoutDuration, timeoutUnit);
        } catch (InterruptedException e) {
          future.cancel(true);
          throw e;
        }
      } else {
        return Uninterruptibles.getUninterruptibly(future, timeoutDuration, timeoutUnit);
      }
    } catch (ExecutionException e) {
      throw throwCause(e, true /* combineStackTraces */);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new UncheckedTimeoutException(e);
    }
  }

  @Override
  public <T> T callWithTimeout(Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit)
      throws TimeoutException, InterruptedException, ExecutionException {
    checkNotNull(callable);
    checkNotNull(timeoutUnit);
    checkPositiveTimeout(timeoutDuration);

    Future<T> future = executor.submit(callable);

    try {
      return future.get(timeoutDuration, timeoutUnit);
    } catch (InterruptedException | TimeoutException e) {
      future.cancel(true /* mayInterruptIfRunning */);
      throw e;
    } catch (ExecutionException e) {
      wrapAndThrowExecutionExceptionOrError(e.getCause());
      throw new AssertionError();
    }
  }

  @Override
  public <T> T callUninterruptiblyWithTimeout(
      Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit)
      throws TimeoutException, ExecutionException {
    checkNotNull(callable);
    checkNotNull(timeoutUnit);
    checkPositiveTimeout(timeoutDuration);

    Future<T> future = executor.submit(callable);

    try {
      return Uninterruptibles.getUninterruptibly(future, timeoutDuration, timeoutUnit);
    } catch (TimeoutException e) {
      future.cancel(true /* mayInterruptIfRunning */);
      throw e;
    } catch (ExecutionException e) {
      wrapAndThrowExecutionExceptionOrError(e.getCause());
      throw new AssertionError();
    }
  }

  @Override
  public void runWithTimeout(Runnable runnable, long timeoutDuration, TimeUnit timeoutUnit)
      throws TimeoutException, InterruptedException {
    checkNotNull(runnable);
    checkNotNull(timeoutUnit);
    checkPositiveTimeout(timeoutDuration);

    Future<?> future = executor.submit(runnable);

    try {
      future.get(timeoutDuration, timeoutUnit);
    } catch (InterruptedException | TimeoutException e) {
      future.cancel(true /* mayInterruptIfRunning */);
      throw e;
    } catch (ExecutionException e) {
      wrapAndThrowRuntimeExecutionExceptionOrError(e.getCause());
      throw new AssertionError();
    }
  }

  @Override
  public void runUninterruptiblyWithTimeout(
      Runnable runnable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException {
    checkNotNull(runnable);
    checkNotNull(timeoutUnit);
    checkPositiveTimeout(timeoutDuration);

    Future<?> future = executor.submit(runnable);

    try {
      Uninterruptibles.getUninterruptibly(future, timeoutDuration, timeoutUnit);
    } catch (TimeoutException e) {
      future.cancel(true /* mayInterruptIfRunning */);
      throw e;
    } catch (ExecutionException e) {
      wrapAndThrowRuntimeExecutionExceptionOrError(e.getCause());
      throw new AssertionError();
    }
  }

  private static Exception throwCause(Exception e, boolean combineStackTraces) throws Exception {
    Throwable cause = e.getCause();
    if (cause == null) {
      throw e;
    }
    if (combineStackTraces) {
      StackTraceElement[] combined =
          ObjectArrays.concat(cause.getStackTrace(), e.getStackTrace(), StackTraceElement.class);
      cause.setStackTrace(combined);
    }
    if (cause instanceof Exception) {
      throw (Exception) cause;
    }
    if (cause instanceof Error) {
      throw (Error) cause;
    }
    // The cause is a weird kind of Throwable, so throw the outer exception.
    throw e;
  }

  private static Set<Method> findInterruptibleMethods(Class<?> interfaceType) {
    Set<Method> set = Sets.newHashSet();
    for (Method m : interfaceType.getMethods()) {
      if (declaresInterruptedEx(m)) {
        set.add(m);
      }
    }
    return set;
  }

  private static boolean declaresInterruptedEx(Method method) {
    for (Class<?> exType : method.getExceptionTypes()) {
      // debate: == or isAssignableFrom?
      if (exType == InterruptedException.class) {
        return true;
      }
    }
    return false;
  }

  // TODO: replace with version in common.reflect if and when it's open-sourced
  private static <T> T newProxy(Class<T> interfaceType, InvocationHandler handler) {
    Object object =
        Proxy.newProxyInstance(
            interfaceType.getClassLoader(), new Class<?>[] {interfaceType}, handler);
    return interfaceType.cast(object);
  }

  private void wrapAndThrowExecutionExceptionOrError(Throwable cause) throws ExecutionException {
    if (cause instanceof Error) {
      throw new ExecutionError((Error) cause);
    } else if (cause instanceof RuntimeException) {
      throw new UncheckedExecutionException(cause);
    } else {
      throw new ExecutionException(cause);
    }
  }

  private void wrapAndThrowRuntimeExecutionExceptionOrError(Throwable cause) {
    if (cause instanceof Error) {
      throw new ExecutionError((Error) cause);
    } else {
      throw new UncheckedExecutionException(cause);
    }
  }

  private static void checkPositiveTimeout(long timeoutDuration) {
    checkArgument(timeoutDuration > 0, "timeout must be positive: %s", timeoutDuration);
  }
}
