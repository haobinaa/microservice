package com.cloud.hystrix.test;

import com.cloud.hystrix.SimpleHystrixCommand;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import rx.Observable;
import rx.Observer;
import rx.functions.Action1;

/**
 * @Author HaoBin
 * @Create 2020/3/18 16:52
 * @Description: 命令执行方法
 **/
public class HystrixExecution {


    /**
     * 同步执行
     */
    @Test
    public void testExecute() {
        String executeResult = new SimpleHystrixCommand("hello").execute();
        System.out.println("同步执行结果:"+executeResult);
    }

    /**
     * 异步执行
     */
    @Test
    public void testQueue() throws Exception{
        // queue()是异步非堵塞性执行：直接返回，同时创建一个线程运行HelloWorldHystrixCommand.run()
        // 一个对象只能queue()一次
        // queue()事实上是toObservable().toBlocking().toFuture()
        Future<String> future = new SimpleHystrixCommand("Hlx").queue();
        // 使用future时会堵塞，必须等待HelloWorldHystrixCommand.run()执行完返回
        String queueResult = future.get(10000, TimeUnit.MILLISECONDS);
        // String queueResult = future.get();
        System.out.println("queue异步结果：" + queueResult);
    }

    @Test
    public void testObserve() throws Exception {

        // observe()是异步非堵塞性执行，同queue
        Observable<String> hotObservable = new SimpleHystrixCommand("Hlx").observe();

        // single()是堵塞的
        System.out.println("hotObservable single结果：" + hotObservable.toBlocking().single());

        // 注册观察者事件
        // subscribe()是非堵塞的
        hotObservable.subscribe(new Observer<String>() {

            // 先执行onNext再执行onCompleted
             @Override
            public void onCompleted() {
                System.out.println("hotObservable completed");
            }

             @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

             @Override
            public void onNext(String v) {
                System.out.println("hotObservable onNext: " + v);
            }

        });

        // 非堵塞
        hotObservable.subscribe(new Action1<String>() {
            // 相当于上面的onNext()
            // @Override
            public void call(String v) {
                System.out.println("hotObservable call: " + v);
            }

        });

        // 等待一下子线程的执行
        Thread.sleep(1000);
    }



    @Test
    public void testObservable() throws Exception {
        // toObservable()是异步非堵塞性执行，同queue
        Observable<String> coldObservable = new SimpleHystrixCommand("Hlx").toObservable();

        // 注册观察者事件
        // subscribe()是非堵塞的
        coldObservable.subscribe(new Observer<String>() {
            // 先执行onNext再执行onCompleted
             @Override
            public void onCompleted() {
                System.out.println("coldObservable completed");
            }
             @Override
            public void onError(Throwable e) {
                System.out.println("coldObservable error");
                e.printStackTrace();
            }
             @Override
            public void onNext(String v) {
                System.out.println("coldObservable onNext: " + v);
            }
        });
        Thread.sleep(5000);
    }
}
