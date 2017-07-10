package org.isomorphism.limit.ratelimiter;

import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 对方法调用加上时间限制
 *
 * @author Kevin Bourrillion
 * @author Jens Nyman
 * @since 1.0
 */
public interface TimeLimiter {

    /**
     * 返回{@code interfaceType}的一个实例，它将所有方法调用委托给{@code target}对象，对每个调用执行指定的时间限制。
     * 对于{@link Object＃equals}，{@link Object＃hashCode}和{@link Object＃toString}的调用，也会执行此时间有限的委托。
     * 如果目标方法调用在达到限制之前完成，返回值或异常将按原样传播到调用者。如果达到时间限制，代理将尝试中止对目标的调用，并将向调用者发出一个{@link UncheckedTimeoutException}。
     * <p>
     * 注意:代理对象的主要目的是在超时过程时将控制权返回给调用者; 中止目标方法调用是次要考虑。
     * 由代理作出的保证的特殊性质和强度是与实现相关的。 但是，当线程中断时，目标对象上的每个方法都能很好工作(behaves appropriately)。
     * 例如，要返回{@code target.someMethod（）}的值，但如果此方法调用占用超过50 ms，则替换{@code DEFAULT_VALUE}，您可以使用以下代码：
     * <p>
     * <pre>
     *   TimeLimiter limiter = . . .;
     *   TargetType proxy = limiter.newProxy(target, TargetType.class, 50, TimeUnit.MILLISECONDS);
     *   try {
     *     return proxy.someMethod();
     *   } catch (UncheckedTimeoutException e) {
     *     return DEFAULT_VALUE;
     *   }
     * </pre>
     *
     * @param target          对象代理
     * @param interfaceType   希望返回的代理实现的接口
     * @param timeoutDuration 调用者愿意等待代理的最大时间
     * @param timeoutUnit     调用者愿意在每次方法调用代理时等待的最长时间
     * @return 代理
     * @throws IllegalArgumentException 如果{@code interfaceType}是常规类，枚举或注释类型，而不是接口
     */
    <T> T newProxy(T target, Class<T> interfaceType, long timeoutDuration, TimeUnit timeoutUnit);

    /**
     * 调用指定的Callable，在指定的时间限制后超时。 如果目标方法调用在达到限制之前完成，则返回值或异常按照原样传播到*调用者。
     * 如果达到时间限制，我们尝试中止对目标的*调用，并向调用者发出一个{@link UncheckedTimeoutException}。
     *
     * @param callable        待执行的Callable
     * @param timeoutDuration 调用者愿意等待代理的最大时间
     * @param timeoutUnit    调用者愿意等待代理的最大时间
     * @param interruptible  是否通过中止操作来响应线程中断并抛出InterruptedException; 如果为false，则允许操作完成或超时，并重新确认当前线程的中断状态。
     * @return Callable返回的结果
     * @throws InterruptedException      如果{@code interruptible}为真，并且我们的线程在执行期间中断
     * @throws UncheckedTimeoutException 如果达到时间限制
     * @deprecated 使用其他{@code调用[不间断] WithTimeout（）}或{@code运行[不间断] WithTimeout（）}方法。 该方法计划删除
     */
    @Deprecated
    <T> T callWithTimeout(Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit, boolean interruptible) throws Exception;

    /**
     调用指定的Callable，在指定的时间限制后超时。 如果目标方法调用在达到限制之前完成，则返回值或包装的异常被传播。
     如果达到时间限制，我们尝试中止对目标的调用，并向呼叫者发出{@link TimeoutException}。
     *
     * @param callable         待执行的Callable
     * @param timeoutDuration 调用者愿意等待代理的最大时间
     * @param timeoutUnit     调用者愿意等待代理的最大时间
     * @return Callable返回的结果
     * @throws TimeoutException            如果达到时间限制
     * @throws InterruptedException        如果当前线程在执行期间中断
     * @throws ExecutionException          if {@code callable} throws a checked exception
     */
    <T> T callWithTimeout(Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException, InterruptedException, ExecutionException;

    /**
     调用指定的Callable，在指定的时间限制后超时。 如果目标方法调用在达到限制之前完成，则返回值或包装的异常被传播。
     如果达到时间限制，尝试中止对目标的调用，并向调用者发出{@link TimeoutException}。
     与{@link #callWithTimeout（Callable，long，TimeUnit）}的区别在于这种方法将忽略当前线程的中断。
     *
     * @param callable        待执行的Callable
     * @param timeoutDuration 调用者愿意等待代理的最大时间
     * @param timeoutUnit     调用者愿意等待代理的最大时间
     * @return Callable返回的结果
     * @throws TimeoutException             如果达到时间限制
     */
    <T> T callUninterruptiblyWithTimeout(Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException, ExecutionException;

    /**
     调用指定的Runnable，在指定的时间限制后超时。 如果目标方法运行在达到限制之前运行，则此方法返回或传播包装的异常。
     如果达到时间限制，我们尝试中止运行，并向呼叫者发送{@link TimeoutException}。
     *
     * @param runnable       待执行的Runnable
     * @param timeoutDuration 调用者愿意等待代理的最大时间
     * @param timeoutUnit     调用者愿意等待代理的最大时间
     * @throws TimeoutException            如果达到时间限制
     * @throws InterruptedException        如果当前线程在执行期间中断
     */
    void runWithTimeout(Runnable runnable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException, InterruptedException;

    /**
     * 调用指定的Runnable，在指定的时间限制后超时。 如果目标方法运行在达到限制之前运行，则此方法返回或传播包装的异常。
     * 如果达到时间限制，我们尝试中止运行，并向呼叫者发送{@link TimeoutException}。
     *
     * @param runnable        待执行的Runnable
     * @param timeoutDuration 调用者愿意等待代理的最大时间
     * @param timeoutUnit     调用者愿意等待代理的最大时间
     * @throws TimeoutException             如果达到时间限制
     */
    void runUninterruptiblyWithTimeout(Runnable runnable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException;
}
