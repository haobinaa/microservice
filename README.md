### 概述

从SOA到微服务， 对系统架构的改造一直在演进中， 现在微服务的相关技术也越来越多，ServiceMesh、ServiceLess等概念也在逐渐落地

本项目用来调研主流微服务体系具体的使用区别，以及对代码的入侵程度， 评估改造方案的简易程度。

有关微服务的介绍， 在我的一篇博客中写过: [微服务介绍](https://blog.haobin95.club/2018/02/04/%E6%9E%B6%E6%9E%84/%E5%BE%AE%E6%9C%8D%E5%8A%A1%E4%BB%8B%E7%BB%8D/)




目前评估的体系有:

### Dubbo

阿里的RPC框架， 提供了灵活扩展的机制， 现在已经是Apache顶级项目

- [dubbo使用介绍](./dubbo/docs/dubbo.md)
- [dubbo 增强SPI机制的实现过程](./dubbo/docs/dubbo_spi.md)
- [dubbo服务提供过程](./dubbo/docs/dubbo_provider.md)
- [dubbo消费服务过程](./dubbo/docs/dubbo_consumer.md)
- [dubbo线程模型](./dubbo/docs/dubbo_thread_model.md)
- [dubbo容错和集群策略](./dubbo/docs/dubbo_cluster.md)



### Istio

Istio是ServiceMesh的一种落地方案， 依赖于Kubernetes

#### kubernetes

- [容器基础原理](https://github.com/haobinaa/microservice/blob/master/k8s/container.md)
- [k8s基础原理和集群部署(单机集群国内可用)](https://github.com/haobinaa/microservice/blob/master/k8s/k8s.md)
- [应用容器化](./k8s/app_to_container.md)
- [pod组件](./k8s/pod.md)

- 容器编排：
  - [控制器模型-Deployment](./k8s/controller_model.md)
  - [StatefulSet保障拓扑状态和存储状态](./k8s/StatefulSet.md)
    - [使用StatefulSet部署mysql集群](./k8s/msyql_cluster.md)
  - [容器化守护进程DaemonSet](./k8s/DaemonSet.md)
  - [离线编排-Job/CronJob](./k8s/job&cronjob.md)
- [声明式API](./k8s/API.md)
- [RBAC控制](./k8s/rbac.md)
- [Operator-自定义有状态应用](./k8s/operator.md)
- 存储机制
  - [PV、PVC、StorageClass和本地化持久卷](./k8s/pv_pvc_storageClass.md)
  - [存储插件]
- 网络
  - [容器网络](./k8s/container_network.md)
  - [kubernetes中网络模型](./k8s/kubernetes_network.md)
  - [kubernetes中三层网络方案]
  - [DNS服务发现(Service原理)](./k8s/service&DNS.md)
  - [外界连通Service](./k8s/debug_service.md)
  - [Ingress-代理和负载均衡](./k8s/ingress.md)
- [容器日志管理]

#### Istio使用

- [Istio简介](./istio/istio_intruduce.md)

### springcloud

SpringCloud是Spring生态提供的一系列微服务组件, [简介](./springcloud/springcloud.md)

#### hystrix

- [使用入门 demo](https://github.com/star2478/java-hystrix/wiki/Hystrix%E4%BD%BF%E7%94%A8%E5%85%A5%E9%97%A8%E6%89%8B%E5%86%8C%EF%BC%88%E4%B8%AD%E6%96%87%EF%BC%89)
- [原理简介](https://my.oschina.net/7001/blog/1619842)
- [官网文档翻译](https://segmentfault.com/a/1190000012439580)

