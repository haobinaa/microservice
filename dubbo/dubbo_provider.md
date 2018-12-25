 ## Dubbo 服务提供发启动流程
 
 ### Dubbo 配置解析
 一个Dubbo的服务端配置如下:
 ``` 
 <!-- 提供方应用信息 -->  
 <dubbo:application name="demo-provider"/>  
 <!-- 用dubbo协议在20880端口暴露服务 -->  
 <dubbo:protocol name="dubbo" port="20880"/>  
 <!-- 使用zookeeper注册中心暴露服务地址 -->  
 <dubbo:registry address="zookeeper://127.0.0.1:1234" id="registry"/>  
 <!-- 默认的服务端配置 -->  
 <dubbo:provider registry="registry" retries="0" timeout="5000"/>  
 <!-- 和本地bean一样实现服务 -->  
 <bean id="demoService" class="com.alibaba.dubbo.demo.provider.DemoServiceImpl"/>  
 <!-- 声明需要暴露的服务接口 -->  
 <dubbo:service interface="com.alibaba.dubbo.demo.DemoService" ref="demoService"/>  
 ```
 
 Dubbo通过实现Spring提供的 `NamespaceHandler` 接口，向Spring注册 `BeanDefinition` 解析器，使Spring能识别Dubbo命名空间下的节点，并且通过实现 `BeanDefinitionParser` 接口，使Spring能解析各节点的具体配置。
 
 Dubbo的`DubboNamespaceHandler`：
 ``` 
 public void init() {  
     registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));
     registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));
     registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));
     registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));
     registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));
     registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));
     registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));
     registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));
     registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));
     registerBeanDefinitionParser("annotation", new DubboBeanDefinitionParser(AnnotationBean.class, true));
 }
 ```
 
 这样， 各个节点最终被转化为各种Bean，配置的各种属性也被转化为Bean的属性。
 
 ### ServiceBean
 
 Dubbo服务提供者由 dubbo:service 来定义的， Spring把 dubbo:service 解析成一个ServiceBean:
 ``` 
 
 public class ServiceBean<T> extends ServiceConfig<T> implements InitializingBean, DisposableBean, ApplicationContextAware, ApplicationListener, BeanNameAware {  
      ...
     // 注入各种 ConfigBean，便于服务注册过程中各种参数的获取
     public void afterPropertiesSet() {}
     ...
     public void onApplicationEvent(ApplicationEvent event) {}
     ...
     public void destroy() {}
 }
 ```
 
 ServiceBean 实现了Spring的 InitializingBean、DisposableBean、 ApplicationListener 等接口，实现了 `afterPropertiesSet()`、 `destroy()`、 `onApplicationEvent()`等生命周期方法
 
 
 
 ### ServiceConfig API发布服务
 
 服务提供方需要使用 ServiceConfig API 发布服务，具体是调用代码`export()` 方法来激活发布服务。export 的核心代码如下：
 
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
doExport代码如下:
``` 
  protected synchronized void doExport() {
        ...
        // (1)如果dubbo:servce 标签为空， 则填充默认属性
        checkDefault();
        ...
       // (2) 验证是否是泛化调用
        if (ref instanceof GenericService) { // @ step2
            interfaceClass = GenericService.class;
            if (StringUtils.isEmpty(generic)) {
                generic = Boolean.TRUE.toString();
            }
        } else {
            try {
                interfaceClass = Class.forName(interfaceName, true, Thread.currentThread()
                        .getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            checkInterfaceAndMethods(interfaceClass, methods);
            checkRef();
            generic = Boolean.FALSE.toString();
        }
        // (3)local属性处理
        if (local != null) { 
            if ("true".equals(local)) {
                local = interfaceName + "Local";
            }
            Class<?> localClass;
            try {
                localClass = ClassHelper.forNameWithThreadContextClassLoader(local);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            if (!interfaceClass.isAssignableFrom(localClass)) {
                throw new IllegalStateException("The local implementation class " + localClass.getName() + " not implement interface " + interfaceName);
            }
        }
        // (4)stub属性处理
        if (stub != null) { 
            if ("true".equals(stub)) {
                stub = interfaceName + "Stub";
            }
            Class<?> stubClass;
            try {
                stubClass = ClassHelper.forNameWithThreadContextClassLoader(stub);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            if (!interfaceClass.isAssignableFrom(stubClass)) {
                throw new IllegalStateException("The stub implementation class " + stubClass.getName() + " not implement interface " + interfaceName);
            }
        }
         // (5)校验application、registry、protocol
        checkApplication();
        checkRegistry();
        checkProtocol();
        appendProperties(this);
        
        // (6)校验 stub、mock 类的合理性，是否是 interface 的实现类。
        checkStubAndMock(interfaceClass); 
        if (path == null || path.length() == 0) {
            path = interfaceName;
        }
        // (7)执行 doExportUrls() 方法暴露服务
        doExportUrls(); /
        
        // (8)将服务提供者信息注册到 ApplicationModel 实例中。
        ProviderModel providerModel = new ProviderModel(getUniqueServiceName(), ref, interfaceClass); // 
        ApplicationModel.initProviderModel(getUniqueServiceName(), providerModel);
    }
```

对比较重要的步骤进行一个说明：


(1) 如果 dubbo:servce 标签也就是 ServiceBean 的 provider 属性为空，调用 appendProperties 方法，填充默认属性，其具体加载顺序：
 1. 从系统属性加载对应参数值，参数键：dubbo.provider.属性名，System.getProperty。 
 2. 从属性配置文件加载对应参数值，可通过系统属性指定属性配置文件： dubbo.properties.file，如果该值未配置，则

(2) 校验 `ref` 与 `interface` 属性。如果 `ref` 是 `GenericService`，则为Dubbo的泛化实现，然后验证 interface 接口与 ref 引用的类型是否一致。

#### (7)doExportUrl暴露服务

调用链： `ServiceBean#afterPropertiesSet --> ServiceConfig#export --> ServiceConfig#doExport --> ServiceConfig#doExportUrls`


``` 
private void doExportUrls() {  
    // (1) 遍历注册中心的配置信息
    List<URL> registryURLs = loadRegistries(true); 
    // (2) 遍历所有的协议配置， 向注册中心暴露服务
    for (ProtocolConfig protocolConfig : protocols) {
        doExportUrlsFor1Protocol(protocolConfig, registryURLs);
    }
}
```

(1) 首先遍历 `ServiceBean` 的 `List registries`（所有注册中心的配置信息），然后将地址封装成URL对象，关于注册中心的所有配置属性，最终转换成url的属性(?属性名=属性值)
，`loadRegistries(true)`，参数的意思：true，代表服务提供者，false：代表服务消费者。如果是服务提供者，则检测注册中心的配置，如果配置了 ` "register=“false" `则忽略该地址；如果是服务消费者，`并配置了 subscribe="false"` 则表示不从该注册中心订阅服务，故也不返回。

(2) 遍历配置的所有协议，根据每个协议，向注册中心暴露服务, 流程如下：

#### doExportUrlsFor1Protocol分析：

代码太长， 分段分析

#### 组装URL所需要的参数

(1) 用 Map 存储该协议的所有配置参数，包括：协议名称、Dubbo版本、当前系统时间戳、进程ID、application配置、module配置、默认服务提供者参数(ProviderConfig)、协议配置、服务提供 Dubbo:service 的属性
``` 
        String name = protocolConfig.getName();
        if (name == null || name.length() == 0) {
            name = "dubbo";
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put(Constants.SIDE_KEY, Constants.PROVIDER_SIDE);
        map.put(Constants.DUBBO_VERSION_KEY, Version.getProtocolVersion());
        map.put(Constants.TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));
        if (ConfigUtils.getPid() > 0) {
            map.put(Constants.PID_KEY, String.valueOf(ConfigUtils.getPid()));
        }
        appendParameters(map, application);
        appendParameters(map, module);
        appendParameters(map, provider, Constants.DEFAULT_KEY);
        appendParameters(map, protocolConfig);
        appendParameters(map, this);

```



### 参考资料
- [Dubbo核心源码： 服务端启动流程](https://juejin.im/post/5bcee9696fb9a05cda77a3da)