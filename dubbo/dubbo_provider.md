 ## Dubbo 服务提供发启动流程
 
 Dubbo 服务提供方启动流程时序图如下：
 
 
 ![](https://raw.githubusercontent.com/haobinaa/microservice/master/images/doLocalExport_netty.jpg)
 
 服务提供方需要使用 ServiceConfig API 发布服务，具体是调用代码（1）export() 方法来激活发布服务。export 的核心代码如下：
 
 ``` 
  private static final ScheduledExecutorService delayExportExecutor = Executors.newSingleThreadScheduledExecutor(new 
  NamedThreadFactory("DubboServiceDelayExporter", true));
  
  
  
 public synchronized void export() {
     ...
     //这里是延迟发布
     if (delay != null && delay > 0) {
         delayExportExecutor.schedule(new Runnable() {
             public void run() {
                 doExport();
             }
         }, delay, TimeUnit.MILLISECONDS);
     } else {
         //直接发布
         doExport();
     }
 }
 ```
 
 
 ### 延迟发布
 
 可以看到Dubbo的延迟发布是通过`ScheduledExecutorService`实现的，，可以通过调用 `ServiceConfig` 的 `setDelay(Integer delay) `来设置延迟发布时间。 如果没有设置延迟时间则直接调用 `doExport()` 发布服务，延迟发布最后也是调用的该方法
 
 
 ### 直接发布 （doExport)
 
 