package org.isomorphism.limit.timelimiter;

/**
 * DemoClass
 *
 * @author Michael.Wang
 * @date 2017/7/11
 */
public class DemoClass implements DemoInterface {
    @Override
    public int execute() {
        try {
            for (int i = 0; i < 100; i++) {
                System.out.println(i);
                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }
        return 0;
    }
}