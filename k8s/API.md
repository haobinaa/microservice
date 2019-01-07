## 声明式API编程


### 命令式和声明式

#### 命令式操作

在Docker swamp中，对容器的操作都是基于命令行的，创建和更新操作类似:
``` 
$ docker service create --name nginx --replicas 2  nginx
$ docker service update --image nginx:1.7.9 nginx
```
这种用命令去执行的操作称之为命令式操作


#### 声明式操作

在kubernetes中，创建和更新nginx操作一般如下，首先定义一个Deployment的YAML:
``` 
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
spec:
  selector:
    matchLabels:
      app: nginx
  replicas: 2
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx
        ports:
        - containerPort: 80

```
当我们create这个YAML的时候，两个Nginx的Pod就会运行起来，如果要更新nginx，可以更新这个YAML里的Pod模板就可以了。

类似:
``` 
// 创建
$ kubectl create -f nginx.yaml
// 修改后更新
$ kubectl replace -f nginx.yaml
```
上述用命令的方式，也是命令式编程

如果用如下操作
```
 // 创建
 $ kubectl apply -f nginx.yaml
// 修改后更新
$ kubectl apply -f nginx.yaml
 ```
 这样就是用新的YAML中的API对象替换原来的API对象，kubectl apply实际上是执行了一个"patch"操作(类似的还有kubectl edit)，这样的方式就是声明式

### kubernetes中的API

在kubernetes中，一个API对象在Etcd中完整资源路径是由：Group（API组)、Version(API版本)、Resource(API资源类型)三个部分组成的。那么kubernetes中的所有API对象，应该是一种树状结构，如下：
![](../images/k8s/api.png)
kubernetes中API对象的组织方式是层层递进的，比如一个CronJob对象，YAML的开头应该如下:
``` 
apiVersion: batch/v2alpha1
kind: CronJob
// ...省略
```
这里CronJob就是资源类型(Resource)，batch就是它的组(Group)，v2alpha1就是它的版本(Version)

#### kubernetes解析并找到CronJob的过程
1.匹配API对象的组

kubernetes中的核心对象比如Node、Pod等是不需要Group的(它们的Group为""),对于这些API对象，kubernetes会直接在 /api 这个层级进行下一步匹配。

对于CronJob等非核心API对象，就必须在 /apis 这个层级查找对应的Group， 这里应该是找到 /apis/batch

2.找到版本号

紧接着就在batch下面找到版本号 v2alpha1 。同一个API对象可以有多个版本，这样kubernetes升级的过程中， 用户可以升级版本来保持兼容

3.匹配资源类型

在找到版本号之后， kubernetes就会指定需要创建一个 /apis/batch/v2aplpha1 下的 CronJob 对象。这时候API Server就可以创建这个 CronJob对象 了，流程图如下:
![](../images/k8s/create_api.png)

(1) 首先发起创建 CronJob 的Post请求，这时候用户编写的YAML就被提交到了APIServer。APIServer会过滤这个请求，完成一些前置性的工作，比如授权、超时处理等

(2) 然后请求进入MUX和Routes流程，MUX和Routes是APIServer完成URL和Handler绑定的流程(和Spring mvc类似)，这个Handler的过程就是上述匹配到 CronJob 的过程

(3) 接着根据这个CronJob的定义，使用用户提交的YAML创建一个CronJob对象。这个过程中，APIServer会进行一个Convert过程:把用户提交的YAML转换成一个叫 Super Version 的对象， 它是一个API资源类型所有版本的字段全集。这样用户提交的不同版本的YAML，都可以通过这个Super Version来处理

(4) 然后APIServer会进行 Admission和Validation操作，

(5) 最后APIServer把验证过的API对象转换成用户最初提交的版本，并调用Etcd的API保存起来


#### CRD(Custom Resource Definition)

CRD允许用户在kubernetes中添加一个Pod、Node类似的、新的API资源类型。

例如为kubernetes添加一个Network的API资源类型，它的作用是一旦用户创建一个Network对象，那么kubernetes
应该使用这个对象定义的网络参数，调用真实的网络插件，为当前用户创建一个网络环境。这样，如果用户创建Pod就可以声明使用这个网络了
 
比如定义一个network.yaml 如下:
``` 
apiVersion: samplecrd.k8s.io/v1
kind: Network
metadata:
  name: example-network
spec:
  cidr: "192.168.0.0/16"
  gateway: "192.168.0.1"
```
它的group是 samplecrd.k8s.io , version 是 v1。这个YAML定义了一个API资源，称作 CR(Custom Resource)，kubernetes需要指定这个CR的宏观定义是什么才能使用， 
宏观定义即CRD(Custom Resource Definition)，编写CRD文件如下(network-crd.yaml):
``` 
apiVersion: apiextensions.k8s.io/v1beta1
kind: CustomResourceDefinition
metadata:
  name: networks.samplecrd.k8s.io
spec:
  group: samplecrd.k8s.io
  version: v1
  names:
    kind: Network
    plural: networks
  scope: Namespaced
```
这个crd指定了group是 samplecrd.k8s.io ，version是v1， 类型(kind)是Network, 还定义了scope是Namespace(声明这个Network是一个属于Namespace的对象)

这样一个宏观定义让kubernetes能够识别和处理所有声明了API类型是 samplecrd.k8s.io 的YAML

接下来需要让kubernetes知道CR里面描述的网络部分，比如cidr和gateway， 这就需要代码处理了(go语言)， kubernetes有一套机制来编写自定义的代码

### 自定义控制器

基于声明式API的实现，往往需要通过控制器模式来"监控"API对象的变化(创建、删除等)，然后以此来决定实际要执行的具体工作。

CRD可以让用户自定义资源类型，调度这个资源需要定义控制器。自定义控制器的代码过程包括：编写main函数、编写自定义控制器的定义、编写控制器里的业务逻辑(这里同样需要用Go语言来实现)

#### 自定义控制器工作原理

![](../images/k8s/customer_controller.png)

1. 控制器首先从APIServer中获取它所关心的对象，比如之前定义的Network对象。这个操作需要依靠移交叫做Informer的代码库完成，Informer和API对象是一一对应的，传递给自定义控制器的应该是一个Network
对象的Informer(Network Informer)。
