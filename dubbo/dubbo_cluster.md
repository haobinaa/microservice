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
                //(3.1)
                checkWhetherDestroyed();//如果当前实例已经被销毁，则抛出异常
                //(3.2)重新获取所有服务提供者
                copyinvokers = list(invocation);
                //（3.3）重新检查一下
                checkInvokers(copyinvokers, invocation);
            }
            //(3.4)选择负载均衡策略
            Invoker<T> invoker = select(loadbalance, invocation, copyinvokers, invoked);
            invoked.add(invoker);
            RpcContext.getContext().setInvokers((List) invoked);
            //(3.5)具体发起远程调用
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