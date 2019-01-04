## StatefulSet

Deployment可以根据Pod模板新建Pod，也可以kill掉任何一个Pod。在分布式场景中，往往应用之间有着依赖关系(主从等)
，或者是数据库存储类应用与磁盘之间有对应关系，这种实例如果被kill后重建出来，数据之间的对应关系也会失去，导致应用会失败，Deployment并不能满足这样的依赖关系。

这种实例之间的依赖，或者实例对外部数据的依赖，这种应用叫做有状态应用(Statefull Application)。

容器用来封装无状态应用(Stateless Application)很方便，但是一旦容器是有状态的，单靠容器很难解决。kubernetes采用一种StatefulSet的控制器思想，在Deployment的基础上可以对有状态应用进行编排。

应用的状态抽象为了两种情况:
- 拓扑状态：应用之间不是完全对等的关系，实例之间必须按照某种顺序启动， 比如主节点A必须先于从节点B启动。如果A和B两个Pod被删除，它们被创建出来的时候也要按照这个顺序。

- 存储状态： 应用绑定了不同的存储数据，对于Pod A来说第一次读取到的数据和隔了一段时间再次读取到的数据应该是同一份。如果新建了这个Pod，它读取到的数据也应该是同一份。


> StatefulSey的核心功能就是通过某种方式记录这些状态，然后Pod被重新创建时，能够为新Pod恢复这些状态。

## 拓扑状态的保障

StatefulSet使用Headless Service机制来保障Pod的拓扑状态

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
备注:busybox这个镜像问题，有些版本的镜像nslookup找不到，直接ping web-0.nginx也能找到ip


## 存储状态的保障

StatefulSet使用 Persistent Volume Claim 来保障存储状态

### Persistent Volume Claim（PVC）

如果要在Pod里面声明Volume，需要在pod中加上spec.volumes，然后在这个字段里面定义具体的volume 类型，如hostPath。

作为开发者，可能对kubernetes的持久化存储项目不了解，不知道有哪些类型的volume， kubernetes引入persistent volume claim(pvc)和persistent volume(pv)的API对象来降低用户声明和使用持久化volume的门槛

#### PVC的定义
1.定义一个PVC，声明想要volume的属性
``` 
kind: PersistentVolumeClaim
apiVersion: v1
metadata:
  name: pv-claim
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```
这个PVC对象中，没有任何volume的细节，只有描述性的属性和定义。比如需要这个volume至少是一个Gib(spec.resources.requests.storage=1Gi), 声明这个volume的挂载方式是可读写(spect.accessModes=ReadWriteOnce)

2.在应用的Pod中，声明使用这个PVC
``` 
apiVersion: v1
kind: Pod
metadata:
  name: pv-pod
spec:
  containers:
    - name: pv-container
      image: nginx
      ports:
        - containerPort: 80
          name: "http-server"
      volumeMounts:
        - mountPath: "/usr/share/nginx/html"
          name: pv-storage
  volumes:
    - name: pv-storage
      persistentVolumeClaim:
        claimName: pv-claim
```
这个Pod的Volumes定义中，只需要声明是persistentVolumeClaim，然后指定PVC的名字，不必关心volume本身的定义。
如果这个时候创建这个PVC对象，kubernetes会为它绑定一个符合条件的volume，这个volume来自于PV(persistent volume)对象

#### PV对象的定义

一个PV对象的定义一般如下:
``` 
kind: PersistentVolume
apiVersion: v1
metadata:
  name: pv-volume
  labels:
    type: local
spec:
  capacity:
    storage: 10Gi
  rbd:
    monitors:
    - '10.16.154.78:6789'
    - '10.16.154.82:6789'
    - '10.16.154.83:6789'
    pool: kube
    image: foo
    fsType: ext4
    readOnly: true
    user: admin
    keyring: /etc/ceph/keyring
    imageformat: "2"
    imagefeatures: "layering"
```
这个pv对象中有个字段spec.rbd，是一种Ceph RBD Volume。

kubernetes会为刚刚创建的PVC绑定这个PV对象，这种设计有点类似于接口和实现的关系。开发者只需要知道接口(PVC),运维人员给出了接口的具体实现(PV)， 这种解耦就避免了向开发者暴露过多的存储系统细节而带来的隐患。

### StatefulSet使用PVC、PV来进行存储状态管理
StatefulSet的YAML(statefulset_pvc.yaml)如下:
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
        volumeMounts:
        - name: www
          mountPath: /usr/share/nginx/html
  volumeClaimTemplates:
  - metadata:
      name: www
    spec:
      accessModes:
      - ReadWriteOnce
      resources:
        requests:
          storage: 1Gi
```
这个StatefulSet额外添加了一个 volumeClaimTemplates 字段，代表凡是被这个StatefulSet管理的Pod都会声明一个PVC，PVC的定义就来源于 volumeClaimTemplates 这个模板的字段。

这个自动创建的PVC与PV绑定成功后，就会进入Bound字段，这就意味着这个Pod可以挂载并使用这个PV了。

### StatefulSet滚动更新

#### 触发滚动更新

StatefulSet可以编排"有状态应用"，如果需要对StatefulSet进行滚动，只需要修改StatefulSet的Pod模板，就会自动触发"滚动更新", 例如:
``` 
$ kubectl patch statefulset mysql --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/image", "value":"mysql:5.7.23"}]'
statefulset.apps/mysql patched
```
上述使用了`kubectl patch`命令，以"补丁"的方式(Json格式)修改了一个API对的指定字段,即:`spec/template/spec/containers/0/image`。

这样StatefulSet Controller会按照Pod编号相反的顺序，逐一更新这个StatefulSet管理的Pod，如果更新发生错误，这次"滚动更新"就会停止。

#### 精准的控制-金丝雀发布

StatefulSet的`spec.updateStrategy.rollingUpdate`的partition字段可以指定多个实例的一部分不会更新到最新版本。这样就可以实现灰度发布或者金丝雀发布。

例如:
``` 
$ kubectl patch statefulset mysql -p '{"spec":{"updateStrategy":{"type":"RollingUpdate","rollingUpdate":{"partition":2}}}}'
statefulset.apps/mysql patched
```
与上述一样也是用patch补丁的方式， (更多patch的用法参考[官网](https://k8smeetup.github.io/docs/tasks/run-application/update-api-object-kubectl-patch))

这种方式类似用kubectl edit打开对象，将partition字段修改为2。当Pod模板发生了变化(比如images做了修改)的时候，这样只有序号大于2的Pod才会被升级，序号小于2的Pod即使被删除了重建也不会升级。