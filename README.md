### 概述

从SOA到微服务， 对系统架构的改造一直在演进中， 现在微服务的相关技术也越来越多，ServiceMesh、ServiceLess等概念也在逐渐落地

本项目用来调研主流微服务体系具体的使用区别，以及对代码的入侵程度， 评估改造方案的简易程度。

有关微服务的介绍， 在我的一篇博客中写过: [微服务介绍](https://blog.haobin95.club/2018/02/04/%E6%9E%B6%E6%9E%84/%E5%BE%AE%E6%9C%8D%E5%8A%A1%E4%BB%8B%E7%BB%8D/)




目前评估的体系有:

### Dubbo

阿里的RPC框架， 提供了灵活扩展的机制， 现在已经是Apache顶级项目

- [dubbo使用介绍](https://github.com/haobinaa/microservice/blob/master/dubbo/README.md)
- [dubbo 增强SPI机制的实现过程](https://github.com/haobinaa/microservice/blob/master/dubbo/dubbo_spi.md)
### springcloud
### istio

