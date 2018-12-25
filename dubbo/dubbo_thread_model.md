### Dubbo线程池

Dubbo 默认的底层网络通讯使用的是 Netty ，服务提供方 NettyServer 使用两级线程池，其中 EventLoopGroup(boss) 主要用来接受客户端的链接请求，并把接受的请求分发给 EventLoopGroup（worker） 来处理，boss 和 worker 线程组我们称之为 IO 线程。

如果服务提供方的逻辑能迅速完成，并且不会发起新的 IO 请求，那么直接在 IO 线程上处理会更快，因为这减少了线程池调度。但如果处理逻辑较慢，或者需要发起新的 IO 请求，比如需要查询数据库，则 IO 线程必须派发请求到新的线程池进行处理，否则 IO 线程会被阻塞，将导致不能接收其它请求。

### Dubbo的线程模型

根据请求的消息类被 IO 线程处理还是被业务线程池处理，Dubbo 提供了下面几种线程模型：

#### all（AllDispatcher 类）

 所有消息都派发到业务线程池，这些消息包括请求、响应、连接事件，断开事件，心跳等， 如图；
 
![](https://raw.githubusercontent.com/haobinaa/microservice/master/images/dubbo_all.png)

#### direct（DirectDispatcher 类）

所有消息都不派发到业务线程池，全部在 IO 线程上直接执行，模型如下图：

![](https://raw.githubusercontent.com/haobinaa/microservice/master/images/dubbo_direct.png)

#### message（MessageOnlyDispatcher）

只有请求响应消息派发到业务线程池，其它连接断开事件、心跳等消息，直接在 IO 线程上执行，模型图如下：

![](https://raw.githubusercontent.com/haobinaa/microservice/master/images/dubbo_message.png)

#### execution（ExecutionDispatcher 类）

只把请求类消息派发到业务线程池处理，但是响应和其它连接断开事件，心跳等消息直接在 IO 线程上执行，模型如下图：

![](https://raw.githubusercontent.com/haobinaa/microservice/master/images/dubbo_execution.png)

#### connection（ConnectionOrderedDispatcher类）

在 IO 线程上，将连接断开事件放入队列，有序逐个执行，其它消息派发到业务线程池处理，模型如下图：

![](https://raw.githubusercontent.com/haobinaa/microservice/master/images/dubbo_connection.png)

#### 以AllDispatcher为例

``` 
public ChannelHandler dispatch(ChannelHandler handler, URL url) {
        return new AllChannelHandler(handler, url);
}
```
可以看到是AllChannelHandler来处理请求:
``` 
public class AllChannelHandler extends WrappedChannelHandler {

    public AllChannelHandler(ChannelHandler handler, URL url) {
        super(handler, url);
    }

    //链接事件，交给业务线程池处理
    public void connected(Channel channel) throws RemotingException {
        ExecutorService cexecutor = getExecutorService();
        try {
            cexecutor.execute(new ChannelEventRunnable(channel, handler, ChannelState.CONNECTED));
        } catch (Throwable t) {
            throw new ExecutionException("connect event", channel, getClass() + " error when process connected event .", t);
        }
    }
    //链接断开事件，交给业务线程池处理
    public void disconnected(Channel channel) throws RemotingException {
        ExecutorService cexecutor = getExecutorService();
        try {
            cexecutor.execute(new ChannelEventRunnable(channel, handler, ChannelState.DISCONNECTED));
        } catch (Throwable t) {
            throw new ExecutionException("disconnect event", channel, getClass() + " error when process disconnected event .", t);
        }
    }
    //请求响应事件，交给业务线程池处理
    public void received(Channel channel, Object message) throws RemotingException {
        ExecutorService cexecutor = getExecutorService();
        try {
            cexecutor.execute(new ChannelEventRunnable(channel, handler, ChannelState.RECEIVED, message));
        } catch (Throwable t) {
            //TODO 临时解决线程池满后异常信息无法发送到对端的问题。待重构
            //fix 线程池满了拒绝调用不返回，导致消费者一直等待超时
            if(message instanceof Request && t instanceof RejectedExecutionException){
               ...
            }
            throw new ExecutionException(message, channel, getClass() + " error when process received event .", t);
        }
    }
    //异常处理事件，交给业务线程池处理
    public void caught(Channel channel, Throwable exception) throws RemotingException {
        ExecutorService cexecutor = getExecutorService();
        try {
            cexecutor.execute(new ChannelEventRunnable(channel, handler, ChannelState.CAUGHT, exception));
        } catch (Throwable t) {
            throw new ExecutionException("caught event", channel, getClass() + " error when process caught event .", t);
        }
    }
...
}
```

可知所有事件都直接交给业务线程池进行处理了。
