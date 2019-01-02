## 容器编排之控制器模型

docker容器好像一个个集装箱，把应用装入其中，kubernetes的Pod对象就是容器的升级版，好比在集装箱周围装上了吊环，好让kubernetes这个吊车更好的操作它。

kubernetes操作这个Pod"集装箱"的逻辑，都由控制器(Controller)来完成。

### Deployment与控制器

Deployment是一个简单的控制器， 例如:
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
        image: nginx:1.7.9
        ports:
        - containerPort: 80
```
这个Deployment的编排动作很简单：确保携带app=nginx标签的Pod数永远等于`spec.replicas`个数。即集群中携带app=nginx的Pod大于2，就会有旧的Pod被删除，反之，就有新的Pod被创建。

kubernetes中，kube-controller-manager组件就是来执行这些操作的，这个组件就是一系列控制器的集合，deployment正是其中一种。

对于待编排的对象X，它遵循kubernetes的通用编排模式:控制循环(control loop),用GO代码描述如下:
``` 
for {
  实际状态 := 获取集群中对象 X 的实际状态（Actual State）
  期望状态 := 获取集群中对象 X 的期望状态（Desired State）
  if 实际状态 == 期望状态{
    什么都不做
  } else {
    执行编排动作，将实际状态调整为期望状态
  }
}
```
其中实际状态来自于kubernetes集群本身，期望状态一般是用户提交的YAML，当期望状态不等于实际状态时，控制器就会调整。这个调整的操作称为调谐(Reconcile)，调整的过程则被称作"Reconcile Loop"(调谐循环)
或"Sync Loop"(同步循环)

### 面向API 对象编程

像上述的Deployment这种控制器，使用的就是用一种对象来管理另一种对象的设计。

控制对象本身，负责定义管理对象的期望状态，如Deployment里的replicas=2.

被控制对象的定义，来自于一个模板。比如Deployment里的template。Deployment的template字段里面的内容，跟一个标准的Pod对象的API定义几乎没有差别，而所有被Deployment管理的Pod
实例，其实都是根据这个template字段的内容创建的。这个Deployment里面的template字段有个专有名词叫做:PodTemplate。


对于控制器来说，应该是如下的:
![](../images/k8s/controller_model.png)
Deployment等控制器。它的组成部分=上半部分的控制器定义(包括期望状态)+下半部分的被控制对象模板


### Deployment控制器模式完整实现

Deployment看似功能很简单，其实它实现了kubernetes中一个很重要的功能:Pod的水平扩展/收缩(horizontal scaling out/in)

比如更新了Deployment Pod模板，那么Deployment就需要遵循一种叫"滚动更新"的方式来升级现有容器。实现这个需要依靠一个API对象:`ReplicaSet`。例如yaml文件:
``` 
apiVersion: apps/v1
kind: ReplicaSet
metadata:
  name: nginx-set
  labels:
    app: nginx
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.7.9
```
从YAML文件可以看出，replicatSet对象是由副本数目定义和Pod模板组成，是一个Deployment的子集。其实Deployment控制器实际操作的正是ReplicaSet对象，而不是Pod对象。

### ReplicaSet与Deployment 之间的关系

假如yaml如下:
``` 
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
  labels:
    app: nginx
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.7.9
        ports:
        - containerPort: 80
```
这是一个Pod副本个数为3的Deployment

具体的实现上Deployment、ReplicaSet、Pod之间的关系如下:
![](../images/k8s/replicaSet_deployment.png)

可以看出一个replicas=3的deployment与replicaSet、Pod之间是一种层层控制的关系。ReplicaSet保证系统中Pod的个数永远等于指定的数目。在此基础上，Deployment可以通过控制ReplicaSet的个数和属性，进而实现"水平扩展/收缩"和"滚动更新"这个两个编排操作。

#### 水平扩展/收缩

水平扩展收缩可以通过修改ReplicaSet的Pod副本个数，增大是扩展，减小则是收缩， 操作指令是`kubectl scale`,如下:
``` 
kubectl scale deployment nginx-deployment --replicas=4
```

####  滚动更新

1.创建一个deployment(--record用来记录每次操作所执行的命令):
```
kubectl create -f nginx-deployment.yaml --record 
```

2.查看nginx-dement创建后的状态信息
``` 
$ kubectl get deployments
NAME               DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
nginx-deployment   3         0         0            0           1s
```
- DESIRED:用户期望的Pod副本数(spec.replicas)
- CURRENT:当前处于Running状态的Pod个数
- UP-TODATE:当前处于最新版本的Pod个数


