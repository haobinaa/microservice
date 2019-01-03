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
- UP-TO-DATE:当前处于最新版本的Pod个数
- AVAILABLE:当前已经可用的Pod个数，可用的意思是：即是Running状态又是最新版本，并且处于ready状态。

3.查看这个Deployment控制的ReplicaSet:
``` 
$ kubectl get rs
NAME                          DESIRED   CURRENT   READY   AGE
nginx-deployment-3167673210   3         3         3       20s
```
可以看到，当提交了一个Deployment对象后，Deployment Controller就会立即创建一个Pod副本个数为3的ReplicaSet。
这个ReplicaSet中DESIRED、CURRENT、READY字段的含义与Deployment一致，Deployment只是多了一个up-to-date这个跟版本有关的字段。

4.修改Pod模板，触发滚动更新(此处直接使用`kubectl edit`编辑Etcd里面的API对象)
``` 
$ kubectl edit deployment/nginx-deployment
... 
    spec:
      containers:
      - name: nginx
        image: nginx:1.9.1 # 1.7.9 -> 1.9.1
        ports:
        - containerPort: 80
...
deployment.extensions/nginx-deployment edited
```
kubectl edit会将API对象下载到本地，然后修改完了在提交上去。当edit完毕后会立即触发滚动更新。可以通过`kubectl rollout status`查看:
``` 
$ kubectl rollout status deployment/nginx-deployment
Waiting for rollout to finish: 2 out of 3 new replicas have been updated...
deployment.extensions/nginx-deployment successfully rolled out
```
或者通过describe这个deployment，events中可以看到滚动更新的流程:
``` 
$ kubectl describe deployment nginx-deployment
...
Events:
  Type    Reason             Age   From                   Message
  ----    ------             ----  ----                   -------
...
  Normal  ScalingReplicaSet  24s   deployment-controller  Scaled up replica set nginx-deployment-1764197365 to 1
  Normal  ScalingReplicaSet  22s   deployment-controller  Scaled down replica set nginx-deployment-3167673210 to 2
  Normal  ScalingReplicaSet  22s   deployment-controller  Scaled up replica set nginx-deployment-1764197365 to 2
  Normal  ScalingReplicaSet  19s   deployment-controller  Scaled down replica set nginx-deployment-3167673210 to 1
  Normal  ScalingReplicaSet  19s   deployment-controller  Scaled up replica set nginx-deployment-1764197365 to 3
  Normal  ScalingReplicaSet  14s   deployment-controller  Scaled down replica set nginx-deployment-3167673210 to 0
```
修改Deployment里的Pod之后，Deployment Controller会使用这个修改后的Pod模板，创建一个新的ReplicaSet(hash=1764197365)，这个新的ReplicaSet初始Pod副本数是0。

在24s时，Deployment Controller将这个新的ReplicaSet控制的Pod副本数从0变成了1，即：水平扩展出了一个副本。

在22s时，Deployment Controller又将旧的ReplicaSet(hash=3167673210)所控制的Pod副本数减少为一个

如此交替执行，直到旧的ReplicaSet控制Pod副本数为0，新的ReplicaSet控制的Pod数为3，就完成了这一组Pod的版本升级。

##### 滚动更新保证服务的连续性(RollingUpdateStrategy)

Deployment Controller还可以保证服务的连续性:可以确保在任何时间窗口内，只有指定比例的Pod处于离线状态。同时也会确保，在任何时间窗口内，只有指定比例的新Pod被创建出来。这两个比例可以配置，默认都是DESIRED
值的25%(即DESIRED*0.25)

所以在上述例子中，它有三个Pod，那么在滚动更新的过程中，永远会确保有至少两个(3-3*0.25)
Pod处于可用状态，至多有4(3+3\*.25)个Pod同时存在于集群中，这个策略是Deployment对象的一个字段，叫做`RollingUpdateStrategy`，例如:
``` 
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
  labels:
    app: nginx
spec:
...
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 1
```
`rollingUpdate.maxSurge`指定除了DESIRED数量外，在一次"滚动"中，Deployment控制器还可以创建多少个新的Pod；`rollingUpdate.maxUnavailable`指的是，在一次"滚动"中，Deployment控制器可以删除多少个旧的Pod

所以在升级过程中,Pod、Deployment、ReplicaSet的关系如下:
![](../images/k8s/pod_replicaSet.png)

Deployment控制器控制ReplicaSet数目;一个ReplicaSet对应一个应用版本，并且控制这个版本Pod数量

##### 滚动更新之回滚
当滚动更新过程中，如果新的Pod有问题，创建出了问题。执行`kubectl rollout undo`可以把这个Deployment回滚到上一个版本:
``` 
$ kubectl rollout undo deployment/nginx-deployment
```

如果要回滚到更早的版本，则需要查看每次Deployment变更对应的版本， 执行`kubectl rollout histoyr`即可，然后在回滚的时候指定版本号:`kubectl rollout undo deployment/nginx-deployment --to-revsison=2`