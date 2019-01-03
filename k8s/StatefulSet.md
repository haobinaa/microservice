## StatefulSet

Deployment可以根据Pod模板新建Pod，也可以kill掉任何一个Pod。在分布式场景中，往往应用之间有着依赖关系(主从等)
，或者是数据库存储类应用与磁盘之间有对应关系，这种实例如果被kill后重建出来，数据之间的对应关系也会失去，导致应用会失败，Deployment并不能满足这样的依赖关系。

这种实例之间的依赖，或者实例对外部数据的依赖，这种应用叫做有状态应用(Statefull Application)。

容器用来封装无状态应用(Stateless Application)很方便，但是一旦容器是有状态的，单靠容器很难解决。kubernetes采用一种StatefulSet的控制器思想，在Deployment的基础上可以对有状态应用进行编排。

应用的状态抽象为了两种情况:
- 拓扑状态：应用之间不是完全对等的关系，实例之间必须按照某种顺序启动， 比如主节点A必须先于从节点B启动。如果A和B两个Pod被删除，它们被创建出来的时候也要按照这个顺序。

- 存储状态： 应用绑定了不同的存储数据，对于Pod A来说第一次读取到的数据和隔了一段时间再次读取到的数据应该是同一份。如果新建了这个Pod，它读取到的数据也应该是同一份。


> StatefulSey的核心功能就是通过某种方式记录这些状态，然后Pod被重新创建时，能够为新Pod恢复这些状态。

### Service访问机制

Service是kubernetes将一组Pod暴露给外界的一种访问机制。如一个Deployment有3个Pod，定义了一个Service之后，用户只要能访问这个Service，它就能访问到某个具体的Pod。

这个Service的访问机制有两种:

#### Service VIP(Virtual IP)
通过一个虚拟IP，就可以访问这个Service下面的Pod，类似于Keepalived


#### Service DNS

当访问`my-svc.my-namespace.cluster.local`这条记录的时候，就可以访问到这个DNS记录的Service代理的某个Pod。

这种机制DNS记录分两种：
- Normal Service， `my-svc.my-namespace.cluster.local`解析出来的是一个VIP，由VIP来代理

- Headless Service， `my-svc.my-namespace.cluster.local`解析到的是service代理的某一个Pod的Ip地址，这种情况下就不需要分配VIP，直接以DNS记录的方式解析出被代理的Pod的IP。

一个Headless Service的YAML文件(svc.yaml)如下:
``` 
apiVersion: v1
kind: Service
metadata:
  name: nginx
  labels:
    app: nginx
spec:
  ports:
  - port: 80
    name: web
  clusterIP: None
  selector:
    app: nginx
```
这个Service的clusterIP的值是 None ，即这个Service没有一个VIP，这就是Service的含义。他也是用Label Selector的机制代理起来的，即所有携带 app=nginx 的标签都会被这个Service代理起来。这样的Headless Service，它所代理的所有Pod的IP地址都会被绑定一个这样的DNS记录:`<pod-name>.<svc-name>.<namespace>.svc
.cluster.local`。这样只要知道了Pod的名字和service的名字就可以通过这条DNS记录访问到Pod的Ip了。

##### StatefulSet使用Headless Service维持拓扑状态

有StatefulSet的YAML(statefulset.yaml)如下:
``` 
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: web
spec:
  serviceName: "nginx"
  replicas: 2
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
        image: nginx:1.9.1
        ports:
        - containerPort: 80
          name: web
```
可以看出StatefulSet与Deployment的区别是，StatefulSet多了一个serviceName=nginx，这个字段告诉StatefulSet控制器，使用nginx这个Headless Service(使用这个StatefulSet之前首先需要把nginx这个HeadlessService创建出来)来保持Pod的解析

创建这个两个yaml，并watch(-w) StatefulSet的创建过程:
``` 
# 创建StatefulSet之前需要先创建Headless Service
kubectl create -f svc.yaml
kubectl create -f statefulset.yaml


$ kubectl get pods -w -l app=nginx
NAME      READY     STATUS    RESTARTS   AGE
web-0     0/1       Pending   0          0s
web-0     0/1       Pending   0         0s
web-0     0/1       ContainerCreating   0         0s
web-0     1/1       Running   0         19s
web-1     0/1       Pending   0         0s
web-1     0/1       Pending   0         0s
web-1     0/1       ContainerCreating   0         0s
web-1     1/1       Running   0         20s
```
可以看到，StatefulSet给每个Pod进行了编号，**而且Pod也是严格按照编号顺序创建**的。web-0进入Running之前，web1会一直处于Pending状态


如果这个时候在，其他容器里面使用 nslookup 解析Pod对应的Headless Service可以看到:
``` 
# --rm 代表退出就删除这个Pod(一次性Pod的启动方案)
$ kubectl run -i --tty --image busybox dns-test --restart=Never --rm /bin/sh

# web-0 这个Pod的Ip就被解析出来了
$ nslookup web-0.nginx
Server:    10.0.0.10
Address 1: 10.0.0.10 kube-dns.kube-system.svc.cluster.local

Name:      web-0.nginx
Address 1: 10.244.1.7

# web-1 这个Pod的IP也被解析出来了
$ nslookup web-1.nginx
Server:    10.0.0.10
Address 1: 10.0.0.10 kube-dns.kube-system.svc.cluster.local

Name:      web-1.nginx
Address 1: 10.244.2.7
```


### 拓扑状态


### 存储状态