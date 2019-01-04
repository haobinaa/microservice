## DaemonSet容器守护进程

DaemonSet可以在kubernetes集群中运行一个Daemon Pod。这种Pod有如下特征:
1. 这个Pod运行在kubernetes每一个Node上
2. 每个Node上只有一个这样的Pod实例
3. 当有新的节点加入kubernetes集群后，该Pod会自动在新节点上被创建出来，旧节点删除后，它上面的Pod也会自动删除


这种Pod的应用场景:
1. 各种网络插件的Agent组件，这种必须运行在每个节点上用来处理节点上的网路容器
2. 各种存储插件的Agent组件，也必须运行在每个节点上，用来在这个节点上挂载远程存储目录
3. 各种监控和日志组件，也必须运行在每一个节点上


### DaemonSet使用

一个DaemonSet的定义如下:
``` 
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluentd-elasticsearch
  namespace: kube-system
  labels:
    k8s-app: fluentd-logging
spec:
  selector:
    matchLabels:
      name: fluentd-elasticsearch
  template:
    metadata:
      labels:
        name: fluentd-elasticsearch
    spec:
      tolerations:
      - key: node-role.kubernetes.io/master
        effect: NoSchedule
      containers:
      - name: fluentd-elasticsearch
        image: k8s.gcr.io/fluentd-elasticsearch:1.20
        resources:
          limits:
            memory: 200Mi
          requests:
            cpu: 100m
            memory: 200Mi
        volumeMounts:
        - name: varlog
          mountPath: /var/log
        - name: varlibdockercontainers
          mountPath: /var/lib/docker/containers
          readOnly: true
      terminationGracePeriodSeconds: 30
      volumes:
      - name: varlog
        hostPath:
          path: /var/log
      - name: varlibdockercontainers
        hostPath:
          path: /var/lib/docker/containers
```
这个DaemonSet管理着一个 fluentd-elasticsearch 镜像的Pod，这个镜像的功能是通过fluentd将Docker容器里的日志转发到ElasticSearch中。

DaemonSet跟Deployment很相似，也是利用select选择了name=fluentd-elasticsearch标签的Pod，Pod模板的定义也是在template中，并且挂载了两个目录在宿主机上。这样fluentd启动之后就会从这两个目录里面收集日志信息

容器里的日志信息，默认保存在宿主机的`/var/lib/docker/containers/{{.容器ID}}/{{.容器ID}}-json.log`文件中，这个目录正是fluentd收集的目标

#### DaemonSet保持每个Node有且只有一个被管理的Pod

DaemonSet也是一种控制器模型。

DaemonSet Controller首先从Etcd中获取所有的Node列表，然后遍历这些Node。它会去检查Node上是否携带了有name=fluentd-elasticsearch标签的Pod在运行。可能出现三种情况:
1. 没有这种Pod，那么就在这个Node上新建一个这样的Pod
2. 有这种Pod，但数量大于1，那么就删除多余的Pod
3. 正好有一个这样的pod,这就是正常情况，什么都不操作

其中删除Pod直接用kubernetes API就行，在指定Node上新建Pod的话，就需要使用`nodeSelector`了,选择node名字即可。类似:
``` 
nodeSelector:
    name: <Node 名字 >
```

##### nodeAffinity让Pod与Node绑定

但是kubernetes即将弃用nodeSelector，并使用`nodeAffinity`代替它，类似:
``` 
apiVersion: v1
kind: Pod
metadata:
  name: with-node-affinity
spec:
  affinity:
    nodeAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: metadata.name
            operator: In
            values:
            - node-bbd
```
上述pod中，申明了一个spect.affinity，然后定义了一个nodeAffinity。这个nodeAffinity的含义是:
1. requiredDuringSchedulingIgnoredDuringExecution：这个nodeAffinity必须在每次调度时予以考虑
2. 这个Pod将来只允许运行在"metadata.name"是"node-bbd"这个节点上


当DaemonSet Controller创建Pod的时候，自动在Pod的API对象里面，添加一个这样的`nodeAffinity`定义，其中需要绑定节点的名字就是当前节点。DaemonSet并不会修改用户提交的YAML模板，而是像kubernetes发起请求之前，直接修改模板生成的Pod对象。

##### toleration机制-DaemonSet的调度机制

另外，DaemonSet还会给这个Pod添加一些另外与调度相关的字段，叫做toleration，容忍机制，声明这个Pod会容忍某些Node的污点(Taint)。(在搭建k8s集群的时候介绍过toleration/taint机制)

DaemonSet自动加的toleration字段格式如下:
``` 
apiVersion: v1
kind: Pod
metadata:
  name: with-toleration
spec:
  tolerations:
  - key: node.kubernetes.io/unschedulable
    operator: Exists
    effect: NoSchedule
```
这个toleration的含义是，容忍所有被标记为 unschedulable 污点的Node；容忍的效果是允许调度。正常情况下被标记了 unshedulable 污点的node是不会有任何Pod调度上去的，DaemonSet自动给被管理的Node加上这个toleration，就保证每个节点上都被调度一个这样的Pod


##### DaemonSet运行可能出现在kubernetes集群之前

DaemonSet运行的时候，可能比整个kubernetes集群要早。假如DaemonSet是一个网络插件的Agent组件，整个kubernetes集群还没有可用的容器网络，所有的Worker节点都是NotReady
(NetworkReady=false)。这个时候普通的Pod不能运行在集群上，所以DaemonSet保障了这种机制：

当前DaemonSet管理的是一个网络插件的Agent Pod,这就必须在这个pod加上能够容忍`node.kubernetes.io/network-unavailable`这个污点，如下：
``` 
...
template:
    metadata:
      labels:
        name: network-plugin-agent
    spec:
      tolerations:
      - key: node.kubernetes.io/network-unavailable
        operator: Exists
        effect: NoSchedule
```
在kubernetes中，如果一个节点的网络插件尚未安装，这个节点就会被自动加上`node.kubernetes
.io/network-unavailable`这样的污点，通过声明这个toleration，就会忽略当前节点上这个污点，从而将网络插件的Agent组件在节点上启动起来。


在部署kubernetes集群的时候，可以先部署kubernetes在部署网络插件，事实上Weave这个YAML是一个DaemonSet，可以容忍`node.kubernetes.io/network-unavailable`这个污点。

##### 更多的toleration

可以给DaemonSet加上更多的toleration，从而利用DaemonSet达到自己的目的。