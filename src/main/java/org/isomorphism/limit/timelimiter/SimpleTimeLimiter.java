package org.isomorphism.limit.timelimiter;

import com.google.common.collect.ObjectArrays;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.common.util.concurrent.Uninterruptibles;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * TimeLimiter使用{@link ExecutorService}在后台运行方法调用。 如果给定的方法调用的时间限制到期，则运行该调用的线程将被中断。
 *
 * @author Kevin Bourrillion
 * @author Jens Nyman
 * @since 1.0
 */
public final class SimpleTimeLimiter implements TimeLimiter {

    private final ExecutorService executor;

    /**
     * 使用给定的ExecutorService服务构造一个TimeLimiter实例来执行代理方法调用。
     *       
     * <b>警告：使用有限的执行者可能会适得其反！ 如果线程池满，任何时间呼叫者花费等待线程可能会计入其时间限制，在这种情况下，在调用目标方法之前，调用甚至可能会超时。
     *
     * @param执行器将执行对目标对象的方法调用的ExecutorService; 例如，{@link Executors＃newCachedThreadPool（）}。
     * @deprecated替代使用{@link #create（ExecutorService）}。
     */
    @Deprecated
    public SimpleTimeLimiter(ExecutorService executor) {
        this.executor = checkNotNull(executor);
    }

    /**
     * 使用{@link Executors＃newCachedThreadPool（）}构造一个TimeLimiter实例来执行代理方法调用。
     * <p>
     * <p>警告：使用有限的执行者可能会适得其反！ 如果线程池充满，任何时间呼叫者花费等待线程可能会计入其时间限制，在这种情况下，在调用目标方法之前，调用甚至可能会超时。
     *
     * @deprecated 使用{@code Executors.newCachedThreadPool（）}替代{@link #create（ExecutorService）}。
     */
    @Deprecated
    public SimpleTimeLimiter() {
        this(Executors.newCachedThreadPool());
    }

    /**
     * 使用给定的执行程序服务创建一个TimeLimiter实例来执行方法调用。
     * <b>Warning:</b> 使用有限的执行者可能会适得其反！ 如果线程池充满，任何时间呼叫者花费等待线程可能会计入其时间限制，在这种情况下，在调用目标方法之前，调用甚至可能会超时。
     *
     * @param executor 将执行对目标对象的方法调用的ExecutorService; 例如{@link Executors＃newCachedThreadPool（）}。
     */
    public static SimpleTimeLimiter create(ExecutorService executor) {
        return new SimpleTimeLimiter(executor);
    }

    @Override
    public <T> T newProxy(final T target, Class<T> interfaceType, final long timeoutDuration, final TimeUnit timeoutUnit) {
        checkNotNull(target);
        checkNotNull(interfaceType);
        checkNotNull(timeoutUnit);
        checkPositiveTimeout(timeoutDuration);
        checkArgument(interfaceType.isInterface(), "interfaceType must be an interface type");

        final Set<Method> interruptibleMethods = findInterruptibleMethods(interfaceType);

        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object obj, final Method method, final Object[] args) throws Throwable {
                Callable<Object> callable = new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        try {
                            return method.invoke(target, args);
                        } catch (InvocationTargetException e) {
                            throw throwCause(e, false /* combineStackTraces */);
                        }
                    }
                };
                return callWithTimeout(callable, timeoutDuration, timeoutUnit, interruptibleMethods.contains(method));
            }
        };
        return newProxy(interfaceType, handler);
    }

    @Deprecated
    @Override
    public <T> T callWithTimeout(Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit, boolean amInterruptible) throws Exception {
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
    public <T> T callWithTimeout(Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException, InterruptedException,
            ExecutionException {
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
    public <T> T callUninterruptiblyWithTimeout(Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException, ExecutionException {
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
    public void runWithTimeout(Runnable runnable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException, InterruptedException {
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
    public void runUninterruptiblyWithTimeout(Runnable runnable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException {
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
            StackTraceElement[] combined = ObjectArrays.concat(cause.getStackTrace(), e.getStackTrace(), StackTraceElement.class);
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

    // TODO: 替换为common.reflect的版本，如果到时是开源的
    private static <T> T newProxy(Class<T> interfaceType, InvocationHandler handler) {
        Object object = Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[]{interfaceType}, handler);
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
