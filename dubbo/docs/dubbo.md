### 使用概述
1.dubbo注册中心依赖于zookeeper， 使用前需要安装zookeeper

具体zk的安装方式就不详细说， 默认zk端口为2181, 注意需要开启防火墙的2181端口，以免注册不上

2.项目分为三个部分
- interface, 用来存放服务提供者的接口， 约定双方的调用
- provider, 服务提供者
- consumer， 服务消费者， 远程调用provider的接口实现

### dubbo的配置方式

dubbo有几种方式使用：
- 基于Spring配置
- 基于Dubbo API
- 基于dubbo-Springboot-starter

#### 基于spring配置服务方和消费方

服务方xml配置:
``` 
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
    xmlns:dubbo="http://code.alibabatech.com/schema/dubbo"
    xsi:schemaLocation="http://www.springframework.org/schema/beans
    http://www.springframework.org/schema/beans/spring-beans.xsd
    http://www.springframework.org/schema/context
    http://www.springframework.org/schema/context/spring-context-4.0.xsd 
    http://code.alibabatech.com/schema/dubbo
    http://code.alibabatech.com/schema/dubbo/dubbo.xsd">

    <!-- 提供方应用信息，用于计算依赖关系 -->
    <dubbo:application name="dubboProvider" />

    <!-- 使用zookeeper注册中心暴露服务地址 -->
    <dubbo:registry address="zookeeper://127.0.0.1:2181" />

    <!-- 用dubbo协议在20880端口暴露服务 -->
    <dubbo:protocol name="dubbo" port="20880" />
    <!-- 启用monitor模块 -->
    <dubbo:monitor protocol="registry" />

    <bean id="userService" class="com.microservice.service.impl.UserServiceImpl" />

    <!-- 声明需要暴露的服务接口 -->
    <dubbo:service interface="com.microservice.service.UserServiceBo" ref="userService"
        group="dubbo"  version="1.0.0" timeout="3000"/>

</beans>
```

消费方xml配置:
``` 
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"    
     xmlns:context="http://www.springframework.org/schema/context"
     xmlns:dubbo="http://code.alibabatech.com/schema/dubbo"
    xsi:schemaLocation="http://www.springframework.org/schema/beans
    http://www.springframework.org/schema/beans/spring-beans.xsd
    http://www.springframework.org/schema/context
    http://www.springframework.org/schema/context/spring-context-4.0.xsd 
    http://code.alibabatech.com/schema/dubbo
    http://code.alibabatech.com/schema/dubbo/dubbo.xsd">

  <!-- 消费方应用名，用于计算依赖关系，不是匹配条件，不要与提供方一样 -->    
    <dubbo:application name="dubboConsumer" />  

      <!-- 使用multicast广播注册中心暴露发现服务地址 -->    
    <dubbo:registry  protocol="zookeeper" address="zookeeper://127.0.0.1:2181" />  
       <!-- 启动monitor-->
    <dubbo:monitor protocol="registry" />        
      <!-- 生成远程服务代理，可以和本地bean一样使用demoService -->    
    <dubbo:reference id="userService" interface="com.microservice.service.UserServiceBo" group="dubbo" version="1.0.0" 
    timeout="3000"/>    

</beans>
```

#### 基于Dubbo API配置

provider代码:
``` 
  //等价于  <bean id="userService" class="com.microsercice.service.impl.UserServiceImpl" />
  UserServiceBo userService = new UserServiceImpl();
  
  //等价于  <dubbo:application name="dubboProvider" />
  ApplicationConfig application = new ApplicationConfig();
  application.setName("dubboProvider");

  // 等价于  <dubbo:registry address="zookeeper://127.0.0.1:2181" />
  RegistryConfig registry = new RegistryConfig();
  registry.setAddress("127.0.0.1:2181");
  registry.setProtocol("zookeeper");

  // 等价于 <dubbo:protocol name="dubbo" port="20880" />
  ProtocolConfig protocol = new ProtocolConfig();
  protocol.setName("dubbo");
  protocol.setPort(20880);

  //等价于  <dubbo:monitor protocol="registry" />
  MonitorConfig monitorConfig = new MonitorConfig();
  monitorConfig.setProtocol("registry");

  //等价于 <dubbo:service interface="com.microsercice.service.UserServiceBo" ref="userService"group="dubbo"  version="1.0.0" timeout="3000"/>
  // 此实例很重，封装了与注册中心的连接，请自行缓存，否则可能造成内存和连接泄漏
  ServiceConfig<UserServiceBo> service = new ServiceConfig<UserServiceBo>(); 
  service.setApplication(application);
  service.setMonitor(monitorConfig);
  service.setRegistry(registry); // 多个注册中心可以用setRegistries()
  service.setProtocol(protocol); // 多个协议可以用setProtocols()
  service.setInterface(UserServiceBo.class);
  service.setRef(userService);
  service.setVersion("1.0.0");
  service.setGroup("dubbo");
  service.setTimeout(3000);
  service.export();
```

consumer代码:
``` 
// 等价于  <dubbo:application name="dubboConsumer" />  
  ApplicationConfig application = new ApplicationConfig();
  application.setName("dubboConsumer");

  // 等价于<dubbo:registry  protocol="zookeeper" address="zookeeper://127.0.0.1:2181" />  
  RegistryConfig registry = new RegistryConfig();
  registry.setAddress("127.0.0.1:2181");
  registry.setProtocol("zookeeper");

  //等价于   <dubbo:monitor protocol="registry" />
  MonitorConfig monitorConfig = new MonitorConfig();
  monitorConfig.setProtocol("registry");

  //等价于<dubbo:reference id="userService" interface="com.microsercice.service.UserServiceBo"group="dubbo" version="1.0.0" timeout="3000" />
  // 此实例很重，封装了与注册中心的连接以及与提供者的连接，请自行缓存，否则可能造成内存和连接泄漏
  ReferenceConfig<UserServiceBo> reference = new ReferenceConfig<UserServiceBo>(); 
  reference.setApplication(application);
  reference.setRegistry(registry); // 多个注册中心可以用setRegistries()
  reference.setInterface(UserServiceBo.class);
  reference.setVersion("1.0.0");
  reference.setGroup("dubbo");
  reference.setTimeout(3000);
  reference.setInjvm(false);
  reference.setMonitor(monitorConfig);

  UserServiceBo userService = reference.get(); 
  System.out.println(userService.sayHello("哈哈哈"));
```

#### 基于dubbo-springboot-starter配置

这种方式与springboot集成， 简化了很多配置， 代码例子中就是使用的这种方式


### 泛化接口调用
在上面的例子中， 我们引入了`dubbo-interface`来契约服务端提供的所有接口类， 因为消费方一般是基于接口使用JDK代理实现远程调用的。

泛化接口调用方式主要在服务消费端没有 API 接口类及模型类元（比如入参和出参的 POJO 类）的情况下使用。其参数及返回值中没有对应的 POJO 类，所以所有 POJO 均转换为 Map 
表示。使用泛化调用时候服务消费模块不再需要引入 `契约接口interface` 二方包。

### 异步消费调用

### Dubbo的整体分层

![](https://raw.githubusercontent.com/haobinaa/microservice/master/images/dubbo.png)

- 其中 Service 和 Config 层为 API，对于服务提供方来说，使用 ServiceConfig API 来代表一个要发布的服务配置对象，对于服务消费方来说，ReferenceConfig 代表了一个要消费的服务的配置对象。可以直接初始化配置类，也可以通过 Spring 解析配置自动生成配置类。

- 其它各层均为 SPI层，SPI 意味着下面各层都是组件化可以被替换的，这也是 Dubbo 设计的比较好的一点。Dubbo 增强了 JDK 中提供的标准 SPI 功能，在 Dubbo 中除了 Service 和 Config 层外，其它各层都是通过实现扩展点接口来提供服务的，Dubbo 增强的 SPI 增加了对扩展点 IoC 和 AOP 的支持，一个扩展点可以直接 setter 注入其它扩展点，并且不会一次性实例化扩展点的所有实现类，这避免了当扩展点实现类初始化很耗时，但当前还没用上它的功能时仍进行加载，浪费资源的情况；增强的 SPI 是在具体用某一个实现类的时候才对具体实现类进行实例化。后续会具体讲解 Dubbo 增强的 SPI 的实现原理

- proxy 服务代理层：扩展接口为 ProxyFactory，Dubbo 提供的实现主要有 JavassistProxyFactory（默认使用）和 JdkProxyFactory，用来对服务提供方和服务消费方的服务进行代理

- registry 注册中心层：封装服务地址的注册与发现，扩展接口 Registry 对应的扩展接口实现为 ZookeeperRegistry、RedisRegistry、MulticastRegistry、DubboRegistry等。扩展接口 RegistryFactory 对应的扩展接口实现为 DubboRegistryFactory、DubboRegistryFactory、RedisRegistryFactory、ZookeeperRegistryFactory

- cluster 路由层：封装多个提供者的路由及负载均衡，并桥接注册中心， 集群容错扩展接口 Cluster 对应的实现类有 FailoverCluster(失败重试)、FailbackCluster（失败自动恢复）、FailfastCluster（快速失败）、FailsafeCluster（失败安全）、ForkingCluster（并行调用）等，均衡扩展接口 LoadBalance 对应的实现类为 RandomLoadBalance（随机）、RoundRobinLoadBalance（轮询）、LeastActiveLoadBalance（最小活跃数）、ConsistentHashLoadBalance（一致性hash)等

- monitor 监控层：RPC 调用次数和调用时间监控，扩展接口为 MonitorFactory，对应的实现类为 DubboMonitorFactroy

- protocol 远程调用层：封装 RPC 调用，扩展接口为 Protocol， 对应实现有 RegistryProtocol、DubboProtocol、InjvmProtocol 等

- exchange 信息交换层：封装请求响应模式，同步转异步，扩展接口 Exchanger，对应扩展实现有 HeaderExchanger 等

- transport 网络传输层：抽象 mina 和 netty 为统一接口，扩展接口为 Channel，对应实现有 NettyChannel、MinaChannel 等

- serialize 数据序列化层：可复用的一些工具，扩展接口为 Serialization，对应扩展实现有 DubboSerialization、FastJsonSerialization、Hessian2Serialization、JavaSerialization等，扩展接口ThreadPool对应扩展实现有 FixedThreadPool、CachedThreadPool、LimitedThreadPool 等

### 远程调用概述

#### 暴露一个服务过程

![](../../images/dubbo/dubbo_export_service.png)

- 首先 ServiceConfig 类拿到对外提供服务的实际类 ref（如：UserServiceImpl），然后通过 ProxyFactory 类的 getInvoker 方法使用 ref 生成一个 AbstractProxyInvoker 实例，到这一步就完成了具体服务到 Invoker 的转化。接下来就是 Invoker 转换到 Exporter 的过程。Dubbo 处理服务暴露的关键就在 Invoker 转换到 Exporter 的过程，上图中的红色部分

- Dubbo 协议的 Invoker 转为 Exporter 发生在 DubboProtocol 类的 export 方法中，它主要是创建一个 Netty Server 侦听服务，并接收客户端发来的各种请求，通讯细节由 Dubbo 自己实现，然后注册服务到服务注册中心

#### 消费一个服务

![](https://raw.githubusercontent.com/haobinaa/microservice/master/images/dubbo_rpc_consumer.png)

- 首先 ReferenceConfig 类的 init 方法调用 Protocol 的 refer 方法生 成 Invoker 实例（如上图中的红色部分），这是服务消费的关键。接下来把 Invoker 转换为客户端需要的接口（如：UserServiceBo）

- Dubbo 协议的 invoker 转换为客户端需要的接口，发生在 DubboProtocol 的 refer 方法中，它主要创建一个 netty client 链接服务提供者，通讯细节由 Dubbo 自己实现
