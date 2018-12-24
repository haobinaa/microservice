### Dubbo集群容错

正常情况下，当我们进行系统设计时候，不仅要考虑正常逻辑下代码该如何走，还要考虑异常情况下代码逻辑应该怎么走。当服务消费方调用服务提供方的服务出现错误时候，Dubbo 提供了多种容错方案，缺省模式为 failover，也就是失败重试

#### Dubbo集群容错模式

##### Failover Cluster：失败重试

当服务消费方调用服务提供者失败后自动切换到其他服务提供者服务器进行重试。这通常用于读操作或者具有幂等的写操作，需要注意的是重试会带来更长延迟。可通过 retries="2" 来设置重试次数（不含第一次）。

接口级别配置重试次数方法` <dubbo:reference retries="2" />`，如上配置当服务消费方调用服务失败后，会再重试两次，也就是说最多会做三次调用，这里的配置对该接口的所有方法生效。

正常消费接口配置如下:
``` 
    <dubbo:reference id="userService" interface="com.microservice.service.UserServiceBo" group="dubbo" version="1.0.0" 
    timeout="3000"/>  
```

##### Failfast Cluster：快速失败

当服务消费方调用服务提供者失败后，立即报错，也就是只调用一次。通常这种模式用于非幂等性的写操作。

##### Failsafe Cluster：失败安全

当服务消费者调用服务出现异常时，直接忽略异常。这种模式通常用于写入审计日志等操作。

##### Failback Cluster：失败自动恢复

当服务消费端调用服务出现异常后，在后台记录失败的请求，并按照一定的策略后期再进行重试。这种模式通常用于消息通知操作。

##### Forking Cluster：并行调用

当消费方调用一个接口方法后，Dubbo Client 会并行调用多个服务提供者的服务，只要一个成功即返回。这种模式通常用于实时性要求较高的读操作，但需要浪费更多服务资源。可通过 forks="2" 来设置最大并行数。

##### Broadcast Cluster：广播调用

当消费者调用一个接口方法后，Dubbo Client 会逐个调用所有服务提供者，任意一台调用异常则这次调用就标志失败。这种模式通常用于通知所有提供者更新缓存或日志等本地资源信息。

#### 失败重试策略实现分析

Dubbo 中具体实现失败重试的是 FailoverClusterInvoker 类，这里我们看下具体实现，主要看下 doInvoke 代码：

``` 
 public Result doInvoke(Invocation invocation, final List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {

        //（1）所有服务提供者
        List<Invoker<T>> copyinvokers = invokers;
        checkInvokers(copyinvokers, invocation);
        //(2)获取重试次数
        int len = getUrl().getMethodParameter(invocation.getMethodName(), Constants.RETRIES_KEY, Constants.DEFAULT_RETRIES) + 1;
        if (len <= 0) {
            len = 1;
        }
        //（3）使用循环，失败重试
        RpcException le = null; // last exception.
        List<Invoker<T>> invoked = new ArrayList<Invoker<T>>(copyinvokers.size()); // invoked invokers.
        Set<String> providers = new HashSet<String>(len);
        for (int i = 0; i < len; i++) {
            //重试时，进行重新选择，避免重试时invoker列表已发生变化.
            //注意：如果列表发生了变化，那么invoked判断会失效，因为invoker示例已经改变
            if (i > 0) {
                //如果当前实例已经被销毁，则抛出异常
                checkWhetherDestroyed();
                //(3.2)重新获取所有服务提供者
                copyinvokers = list(invocation);
                // 重新检查一下
                checkInvokers(copyinvokers, invocation);
            }
            //(3.4)选择负载均衡策略
            Invoker<T> invoker = select(loadbalance, invocation, copyinvokers, invoked);
            invoked.add(invoker);
            RpcContext.getContext().setInvokers((List) invoked);
            // 具体发起远程调用
            try {
                Result result = invoker.invoke(invocation);
                if (le != null && logger.isWarnEnabled()) {
                   ...
                }
                return result;
            } catch (RpcException e) {
                if (e.isBiz()) { // biz exception.
                    throw e;
                }
                le = e;
            } catch (Throwable e) {
                le = new RpcException(e.getMessage(), e);
            } finally {
                providers.add(invoker.getUrl().getAddress());
            }
        }
        throw new RpcException("抛出异常...");
    }
```

### Dubbo负载均衡策略

Dubbo 提供了多种均衡策略，默认为 random ，也就是每次随机调用一台服务提供者的机器。

#### Random LoadBalance(随机)

随机策略。按照概率设置权重，比较均匀，并且可以动态调节提供者的权重。

#### RoundRobin LoadBalance(轮询)

轮循策略。轮循，按公约后的权重设置轮循比率。会存在执行比较慢的服务提供者堆积请求的情况，比如一个机器执行的非常慢，但是机器没有挂调用（如果挂了，那么当前机器会从 ZooKeeper 的服务列表删除），当很多新的请求到达该机器后，由于之前的请求还没处理完毕，会导致新的请求被堆积，久而久之，所有消费者调用这台机器上的请求都被阻塞。

#### LeastActive LoadBalance(最少活跃调用数)

如果每个提供者的活跃数相同，则随机选择一个。在每个服务提供者里面维护着一个活跃数计数器，用来记录当前同时处理请求的个数，也就是并发处理任务的个数。所以如果这个值越小说明当前服务提供者处理的速度很快或者当前机器的负载比较低，所以路由选择时候就选择该活跃度最小的机器。如果一个服务提供者处理速度很慢，由于堆积，那么同时处理的请求就比较多，也就是活跃调用数目越大，这使得慢的提供者收到更少请求，因为越慢的提供者的活跃度越来越大。

#### ConsistentHash LoadBalance (一致性hash)

一致性 Hash，可以保证相同参数的请求总是发到同一提供者，当某一台提供者挂了时，原本发往该提供者的请求，基于虚拟节点，平摊到其它提供者，不会引起剧烈变动。

##### 一致性hash特性

- 单调性（Monotonicity)： 单调性是指如果已经有一些请求通过哈希分派到了相应的服务器进行处理，又有新的服务器加入到系统中时，应保证原有的请求可以被映射到原有的或者新的服务器中去，而不会被映射到原来的其它服务器上去。这一点通过上面新增服务器 ip5 可以证明，新增 ip5 后，原来被 ip1 处理的 user6 现在还是被 ip1 处理，原来被 ip1 处理的 user5 现在被新增的 ip5 处理。

- 分散性（Spread）：分布式环境中，客户端请求时候可能不知道所有服务器的存在，可能只知道其中一部分服务器，在客户端看来它看到的部分服务器会形成一个完整的 Hash 环。如果多个客户端都把部分服务器作为一个完整 Hash 环，那么可能会导致，同一个用户的请求被路由到不同的服务器进行处理。这种情况显然是应该避免的，因为它不能保证同一个用户的请求落到同一个服务器。所谓分散性是指上述情况发生的严重程度

- 平衡性（Balance）：平衡性也就是说负载均衡，是指客户端 Hash 后的请求应该能够分散到不同的服务器上去。

##### hash倾斜

##### 虚拟节点