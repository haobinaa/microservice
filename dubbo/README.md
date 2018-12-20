### 使用概述
1. dubbo注册中心依赖于zookeeper， 使用前需要安装zookeeper

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