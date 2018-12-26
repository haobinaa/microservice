## kubernetes简介


![](https://raw.githubusercontent.com/haobinaa/microservice/master/images/k8s/k8s_architechture.png)


kubernetes项目有两种节点组成：Master和Node。Master作为控制节点， Node作为计算节点。

(1) 其中Master节点由三个紧密协作的独立组件组合而成， 它们分别是负责API服务的kube-apiserver、负责调度的kube-scheduler、 负责容器编排的kube-controller-manager。

(2) 计算节点上最核心的部分是一个叫做kubelet的组件。

- kubelet 主要负责同容器运行时(如docker)打交道， kubelet主要依赖CRI(container runtime interface)的远程接口调用，这个接口定义了容器运行时的各项核心操作。

- kubelet另一个功能是调用网络插件和存储插件为容器配置网络和持久化存储。这两个插件也有与kubelet进行交互的接口， 分别是CNI(container networking interface)和CSI(container storage interface)。

- kubelet还通过`gRPC`协议同一个叫做Device Plugin的插件进行交互， 这个插件是kubernetes用来管理GPU等宿主物理设备的组件


### 设计理念

kubernetes项目最主要的设计思想是， 从更宏观的角度， 以统一任务的方式来定义任务之间的各种关系， 并为将来支持更多种类的关系留有余地。

在kubernetes中， 对容器的访问进行了分类，例如：

(1) 首先一类是非常常见的"紧密交互"的关系， 应用之间需要非常频繁的交互和访问。 在常规环境中， 这些应用会直接部署在一台机器上， 通过Localhost通信； 而在kubernetes中， 
这些容器被划分为一个`Pod`，Pod里面的容器共享同一个Network Namespace、同一组数据卷从而达到高效交换信息的目的。

(2) 另一类常见的需求， 比如Web和数据库之间的访问关系， Kubernetes提供了一种叫做“service"的服务。像这样的两个应用， 往往故意不部署在一台机器上， 这样即使web应用所在的机器宕机了， 数据库也不受影响。 
因为容器的ip地址等信息不是固定的， 所以kubernetes给Pod绑定了一个Service服务， 而Service服务申明的IP地址等信息是一直不变的。 这个Service服务的主要作用， 就是作为Pod的代理入口， 
从而代替Pod对外暴露一个固定的网络地址。这样Web应用就能找到数据库容器的Pod了





### kubeadm

[kubeadmn](https://github.com/kubernetes/kubeadm)是一个独立部署kubernetes集群的工具。

kubeadmn的部署思路是： 把kubelet直接安装在宿主机上， 然后使用容器部署其他的kubernetes组件。

利用kubeadm， 我们能够很简单的完成一个kubernetes集群的部署:
``` 
# 创建一个节点
kubeadm init

#  将一个Node 节点 加入到当前集群中
kubeadm join <Master 节点的 IP和端口>
```

#### kubeadm init 的工作流程

当执行`kubeadm init`， kubeadmn将执行一下操作:

(1) 首先是检查这台机器是否可以用来部署kubernetes

(2) 在通过了检查后， kubeadm会生成kubernetes对外提供服务所需的各种证书和对应的目录。kubernetes对外提供服务的时候，除非是专门开启不安全模式，否则都要通过HTTPS才能访问kube-apiserver。这就需要为kubernetes集群配置好证书文件。
- kubeadm为集群生成的证书文件都放在Master节点的/etc/kubernetes/pki目录下， 主要是ca.crt和私钥ca.key

(3) 证书生成后， kubeadm会为其他组件生成访问kube-apiserver所需的配置文件。这些文件的路径是:/etc/kubernetes/xx.conf, 这些配置文件记录的是当前这个Master节点的服务器地址、监听端口、证书目录等信息。这样对应的客户端(如scheduler, kubelet等)可以直接加载相应的文件，使用里面的信息与kube-apiserver建立安全连接

(4) 接下来， kubeadm会为Master组件生成Pod配置文件。 Master的三个组件kube-apiserver、kube-controller、kube-scheduler都会被使用Pod的方式部署起来。 

这个时候kubernetes集群尚未启动起来， 所以Master组件并没有真正的启动起来， kubernetes中有一种特殊的容器启动方式叫做 Static Pod， 它允许把要部署的Pod的YAML文件放在一个指定的目录， 这样， 
当这台机器上的kubelet启动时， 它会自动检查这个目录， 加载所有的Pod YAML文件， 然后在这台机器上启动它们。

(5) 然后kubeadm就会为集群生成一个bootstrap token， 后面持有这个token的节点可以通过kubeadm join加入这个集群


(6) token生成之后， kubeadm会将ca.crt等master节点的信息，通过ConfigMap的方式保存在Etcd中， 供后续部署的Node节点使用。这个ConfigMap的名字是cluster-info。

(7) 最后一步就是安装默认插件， 默认安装kube-proxy和DNS两个插件， kubeadm 会 为这个两个镜像创建两个Pod


#### kubeadm join的工作流程

kubeadm生成bootstrap token之后， 就可以在任意一台安装了kubelet和kubeadm的机器上执行kube join了。













