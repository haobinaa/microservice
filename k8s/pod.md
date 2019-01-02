## Pod

Pod是kubernetes项目中最小的API对象。

关于Pod， 其实他只是一个逻辑上的概念。kubernetes真正处理的，还是宿主机操作系统上Linux容器的Namespace和Cgroups。Pod其实是一组共享了某些资源的容器， 比如Pod里面的容器共享了同一个Network 
Namespace， 并且可以声明共享同一个volume。

Pod不是容器， 是kubernetes项目中最小的编排单位， 容器(container)是Pod属性里面的一个普通字段。在一个API对象中， 
Pod就像一个"虚拟机"，提供了运行环境，容器就像运行在这个虚拟机里面的用户程序。所以凡是**调度、网络、存储以及安全相关的属性基本都是Pod级别的** 因为这些属性描述的是"机器"这个整体。比如Pod的网络定义(机器的网卡)
、Pod的存储定义(机器的磁盘)、Pod的安全定义(机器的防火墙)、Pod的调度(虚拟机运行在哪个服务器上)


### Pod中重要的字段

#### NodeSelector(用户将Pod和Node绑定)

``` 
apiVersion: v1
kind: Pod
...
spec:
 nodeSelector:
   disktype: ssd
```
上述配置意味着这个Pod只能运行在携带了`disktype:ssd` 这个`Label`的节点上


#### NodeName

一旦Pod的这个字段被赋值， kubernetes项目就会认为这个Pod已经过了调度， 调度的结果就是NodeName的值。这个值一般由调度器负责设置

#### HostAliases(定义了Pod的hosts文件的内容)
``` 
apiVersion: v1
kind: Pod
...
spec:
  hostAliases:
  - ip: "10.1.2.3"
    hostnames:
    - "foo.remote"
    - "bar.remote"
...
```
这样这个Pod启动后，`/etc/hosts`文件的内容将如下所示:
``` 
cat /etc/hosts
# Kubernetes-managed hosts file.
127.0.0.1 localhost
...
10.244.135.10 hostaliases-pod
10.1.2.3 foo.remote
10.1.2.3 bar.remote
```
如果要设置hosts文件里的内容，一定要通过这种方式，如果直接修改hosts里面的内容，Pod重建之后会被自动覆盖掉。

#### 容器的定义

kubernetes对Container的定义和docker没有太大区别。比如Image(镜像)、Command(启动命令)、workingDir(容器的工作目录)、Ports(容器的要开放的端口)、volumeMounts(容器要挂载的volume)等

有几个属性，需要注意

##### ImagePullPolicy
定义了镜像拉取策略， ImagePullPolicy默认值是`Always`，即每次创建Pod都会重新拉取一次镜像。

当这个值被定义为`Never`或`IfNotPersent`，则意味着Pod永远不会主动拉取镜像，或宿主机不存在这个镜像时才拉取。

#### Lifecycle
用来定义容器状态发生变化时触发的一系列钩子， 例如:
``` 
apiVersion: v1
kind: Pod
metadata:
  name: lifecycle-demo
spec:
  containers:
  - name: lifecycle-demo-container
    image: nginx
    lifecycle:
      postStart:
        exec:
          command: ["/bin/sh", "-c", "echo Hello from the postStart handler > /usr/share/message"]
      preStop:
        exec:
          command: ["/usr/sbin/nginx","-s","quit"]
```
可以看到lifecycle下面有postStart和preStop。

- postStart指的是容器启动后立刻执行一个指定的操作。如果postStart执行超时或者错误，kubernetes会在该Pod的Events中报出该容器启动失败的错误信息。
- preStop是在容器被杀死(收到了SIGKILL信息)之前执行的操作。可以通过这个钩子来实现容器的"优雅退出"。

##### pod对象在kubernetes中的生命周期
1. Pending, 此时Pod的YAML已经提交给了kubernetes， API对象已经被创建并保存在了Etcd中。但是这个Pod里面有些容器因为某种原因不能被顺利创建
2. Running， 这个状态下， Pod已经调度成功，跟一个具体节点绑定。它所包含的容器都已经创建成功，并且至少有一个正在运行
3. Succeded， 这个状态下Pod所有的容器已经正常运行完毕，并且已经退出了。这种情况在运行一次性任务时比较常见

4. Failed，这个状态下Pod至少有一个容器以不正常的状态(非0的返回码)退出。这个状态意味着需要Debug这个容器的应用，decribe一下这个Pod的Event日志

5. Unknown， 这个状态意味着Pod的状态不能持续的被kubelet汇报给kube-apiserver，很可能是主从节点之间的通信出现了问题

### Pod和container的关系

#### 数据卷投影(project volume)

这是一种特殊的volume， 它们存在意义不是为了存放容器里面的数据，也不是用来进行容器和宿主机之间的数据交换。这些volume的作用是为容器提供预先定义好的数据，仿佛是被kubernetes投影(project)
进容器的。所以叫做project volume， 目前一共有四种project volume:
1. Secret
2. ConfigMap
3. Downward API
4. ServiceAccountToken


##### Secret
以Secret为例， 它的作用是把Pod想要访问的加密数据，存放到Etcd中，然后就可以通过在Pod容器里挂载volume的方式访问secret里保存的信息了。

secret的典型场景就是保存数据的credential信息, pod(`test-project-volume.yaml`)定义如下：
``` 
apiVersion: v1
kind: Pod
metadata:
  name: test-projected-volume 
spec:
  containers:
  - name: test-secret-volume
    image: busybox
    args:
    - sleep
    - "86400"
    volumeMounts:
    - name: mysql-cred
      mountPath: "/projected-volume"
      readOnly: true
  volumes:
  - name: mysql-cred
    projected:
      sources:
      - secret:
          name: user
      - secret:
          name: pass
```
上述的pod声明的volume并不是emptyDir或hostPath，而是projected类型。这个volume的数据来源(sources)是名为user和pass的secret对象，分别对应数据库的用户名和密码。

(1) 创建secret对象
`kubectl create secret`创建：
``` 
有如下文件:
$ cat ./username.txt
admin
$ cat ./password.txt
c1oudc0w!

$ kubectl create secret generic user --from-file=./username.txt
$ kubectl create secret generic pass --from-file=./password.txt

```
username和password存放了用户名和密码，user和pass是为secret对象指定的名字。现在可以通过`kubect get secrete`查看secret对象:
``` 
$ kubectl get secrets
NAME           TYPE                                DATA      AGE
user          Opaque                                1         51s
pass          Opaque                                1         51s
```


通过YAML文件创建Secrete对象:
```
apiVersion: v1
kind: Secret
metadata:
  name: mysecret
type: Opaque
data:
  user: YWRtaW4=
  pass: MWYyZDFlMmU2N2Rm 
```



(2) 创建Pod
`kubectl create -f test-project-volume.yaml`

Pod状态变为Running后，进入pod可以看到(secret要求对象必须经过Base64转码):
``` 
$ kubectl exec -it test-projected-volume -- /bin/sh
$ ls /projected-volume/
user
pass
$ cat /projected-volume/user
root
$ cat /projected-volume/pass
1f2d1e2e67df
```
可以看到Etcd中保存的密码信息已经以文件的形式出现在了容器的volume目录里，文件的名字就是`kubectl create secret`指定的key

通过这种方式挂载到容器里的secret，一旦对应的Etcd里面的数据被更新，volume里面的内容也会被更新

##### Service Account

当有了一个Pod后， 可以在kubernetes里面装一个kubernetes的client， 这样可以从容器里面直接访问并且操作这个kubernetes的API了。kubernetes提供了一种机制解决API Server的授权问题。

service account是kubernetes系统内置的一种"服务账号",它是kubernetes进行权限分配的对象, 解决API 授权问题。

Service Account的授权信息和文件保存了它绑定的一种特殊的Secret对象里。这种特殊的secret对象叫做ServiceAccountToken。任何运行在kubernetes集群上的应用，都必须使用这个ServiceAccessToken里保存的授权信息才可以合法的访问API Server。

kubernetes提供了一个默认的服务账户(default service account)，任何一个运行在kubernetes里的pod都可以直接使用这个default service account，而无需显示的挂载它。因为kubernetes会为每一个Pod自动的声明一个类型是Secrete，名为default-token-xxx的volume，自动挂载在每个容器的固定目录上：
``` 
$ kubectl describe pod nginx-deployment-5c678cfb6d-lg9lw
Containers:
...
  Mounts:
    /var/run/secrets/kubernetes.io/serviceaccount from default-token-s8rbq (ro)
Volumes:
  default-token-s8rbq:
  Type:       Secret (a volume populated by a Secret)
  SecretName:  default-token-s8rbq
  Optional:    false
```
这个Pod创建完成后，容器就可以从默认的ServiceAccountToken的挂载目录里访问到授权信息和文件，比如这里是`/var/run/secrets/kubernetes.io/serviceaccount from 
default-token-s8rbq`,应用程序如果需要访问kubernetes API，只需要加载这些授权文件就可以了。


这种把kubernetes客户端以容器的方式运行在集群里，然后使用default service token自动授权的方法，称为"InclusterConfig"。



##### ConfigMap

ConfigMap与secret类似，但是它不保存的是不需要加密的、应用所需的配置信息。ConfigMap与secret用法几乎相同:
`kubectl create configmap`或者编写ConfigMap对象的YAML文件

比如一个Java应用所需要的配置文件保存在ConfigMap里面：
``` 
# .properties 文件的内容
$ cat example/ui.properties
color.good=purple
color.bad=yellow
allow.textmode=true
how.nice.to.look=fairlyNice

# 从.properties 文件创建 ConfigMap
$ kubectl create configmap ui-config --from-file=example/ui.properties

# 查看这个 ConfigMap 里保存的信息 (data)
$ kubectl get configmaps ui-config -o yaml
apiVersion: v1
data:
  ui.properties: |
    color.good=purple
    color.bad=yellow
    allow.textmode=true
    how.nice.to.look=fairlyNice
kind: ConfigMap
metadata:
  name: ui-config
  ...
```
上述用了`kubectl get -o yaml`，这样会将指定Pod API对象以YAML方式展示出来

##### Downward API

它的作用是让Pod里的容器能够直接获取这个Pod API对象本身信息。Downward API里面的信息，一定是Pod里面的容器启动前就能确定下来的信息。

例如:
``` 
apiVersion: v1
kind: Pod
metadata:
  name: test-downwardapi-volume
  labels:
    zone: us-est-coast
    cluster: test-cluster1
    rack: rack-22
spec:
  containers:
    - name: client-container
      image: k8s.gcr.io/busybox
      command: ["sh", "-c"]
      args:
      - while true; do
          if [[ -e /etc/podinfo/labels ]]; then
            echo -en '\n\n'; cat /etc/podinfo/labels; fi;
          sleep 5;
        done;
      volumeMounts:
        - name: podinfo
          mountPath: /etc/podinfo
          readOnly: false
  volumes:
    - name: podinfo
      projected:
        sources:
        - downwardAPI:
            items:
              - path: "labels"
                fieldRef:
                  fieldPath: metadata.labels
```
上述Pod中声明了一个projected类型volume，volume的数据来源是downward api，这个`downward api volume`声明了要暴露Pod的metadata
.labels信息给容器。通过这种声明方式，当前Pod的Labels字段的值就会被kubernetes自动挂载成为容器里的`/etc/podinfo/labels`文件。而容器的启动命令是一直打印/etc/podinfo/labels
的内容，所以启动了这个Pod后可以通过`kubectl logs pod-name`的方式查看到这些Labels字段被打印出来，如:
``` 
$ kubectl create -f dapi-volume.yaml
$ kubectl logs test-downwardapi-volume
cluster="test-cluster1"
rack="rack-22"
zone="us-est-coast"
```


Downward API支持的字段：
``` 
1. 使用 fieldRef 可以声明使用:
spec.nodeName - 宿主机名字
status.hostIP - 宿主机 IP
metadata.name - Pod 的名字
metadata.namespace - Pod 的 Namespace
status.podIP - Pod 的 IP
spec.serviceAccountName - Pod 的 Service Account 的名字
metadata.uid - Pod 的 UID
metadata.labels['<KEY>'] - 指定 <KEY> 的 Label 值
metadata.annotations['<KEY>'] - 指定 <KEY> 的 Annotation 值
metadata.labels - Pod 的所有 Label
metadata.annotations - Pod 的所有 Annotation

2. 使用 resourceFieldRef 可以声明使用:
容器的 CPU limit
容器的 CPU request
容器的 memory limit
容器的 memory request
```

这里可以看到，正如开始说的那样，downward api获取的信息都是Pod里面容器启动前就能确定下来的信息， 如果想要获取Pod容器启动后才能获取的信息， 就应该考虑在Pod里面定义一个sidecar容器(辅助功能)

### 容器健康检查与恢复机制


#### 健康检查

kubernetes中可以为Pod里的容器定义一个健康检查的探针(Probe)，然后kubelet就会根据这个probe的返回值决定这个容器的状态，而不是直接以容器是否运行作为依据，例如:
``` 
apiVersion: v1
kind: Pod
metadata:
  labels:
    test: liveness
  name: test-liveness-exec
spec:
  containers:
  - name: liveness
    image: busybox
    args:
    - /bin/sh
    - -c
    - touch /tmp/healthy; sleep 30; rm -rf /tmp/healthy; sleep 600
    livenessProbe:
      exec:
        command:
        - cat
        - /tmp/healthy
      initialDelaySeconds: 5
      periodSeconds: 5
```

上述Pod，会在启动后在/tmp目录下创建一个healthy文件，30s后它会删除这个文件，同时还定义了一个livenessProbe(监控检查)，它会在容器启动后执行指令(exec)`cat 
/tmp/healthy`。如果这个文件存在，这条指令的返回值就是0，Pod就会认为这个容器不仅启动，而且是健康的，并且这个健康检查在容器启动5s后开始执行(initialDelaySeconds:5),每5s执行一次
(periodSeconds:5)



#### 恢复机制

当启动上述Pod后，通过describe可以看到Pod的Events中会报出异常(`kubectl describe pod xxx`), 查看Pod状态(`kubectl get pod xxx`)，可以看到类似如下:
``` 
$ kubectl get pod test-liveness-exec
NAME           READY     STATUS    RESTARTS   AGE
liveness-exec   1/1       Running   1          1m
```
状态仍然是Running，并不是failed。但是RESTART字段从0变成了1：说明了异常的容器已经被kubernetes重启了，这个过程中Runing的状态保持不变。

这个就是kubernetes的恢复机制(restartPolicy)，它是Pod的Spec部分的一个字段(pod.spec.restartPolicy)默认值是always。可以是如下值:
- Always:任何情况下，只要容器不在运行状态，就自动重启
- OnFailure: 只在容器异常时才自动重启
- Never:从不自动重启容器


一般以如下原则来使用这些策略：
1. 只要Pod的restartPolicy指定的策略允许重启异常容器，那么这个Pod就会一直保持Running状态，并进行容器重启
2. 对于包含多个容器的Pod，只有它里面的所有容器都进入异常状态， Pod才会进入Failed状态，再此之前都是Running状态，Pod的Ready状态会显示正常容器的个数：
``` 
$ kubectl get pod test-liveness-exec
NAME           READY     STATUS    RESTARTS   AGE
liveness-exec   0/1       Running   1          1m
```

##### LiveProbe
liveProbe除了在容器中执行命令外，也可以发起HTTP或TCP请求，如:
``` 
#### HTTP liveProbe
...
livenessProbe:
     httpGet:
       path: /healthz
       port: 8080
       httpHeaders:
       - name: X-Custom-Header
         value: Awesome
       initialDelaySeconds: 3
       periodSeconds: 3
#### TCP liveProbe
...
livenessProbe:
  tcpSocket:
    port: 8080
  initialDelaySeconds: 15
  periodSeconds: 20
```
可以在Pod中暴露一个健康检查的Url，或者直接让健康检查去检测应用监听端口。

### Pod模板

kubernetes可以自动给Pod填充某些字段，比如开发人员只需要提交一个基本的Pod YAML,kubernetes可以自动给对应的Pod对象加上其他必要的信息，如Labels，volumes等，这些信息可以是运维人员预先定义好的。这个功能叫做PodPreset(Pod预设置)


例如运维人员预先定义好一个PodPreset对象，这个对象中定义了想在开发人员的Pod中追加的字段,叫做PodPreset.yml:
``` 
apiVersion: settings.k8s.io/v1alpha1
kind: PodPreset
metadata:
  name: allow-database
spec:
  selector:
    matchLabels:
      role: frontend
  env:
    - name: DB_PORT
      value: "6379"
  volumeMounts:
    - mountPath: /cache
      name: cache-volume
  volumes:
    - name: cache-volume
      emptyDir: {}
```
这个PodPreset中，定义了一个selector。意味着后面追加的定义只会作用于带有label`role:fronted`标签的Pod对象。然后定义了一组spec中的标准字段。比如env定义了一个DB_PORT这个环境变量，volumeMounts定义了volume的挂载目录，volumes定义了一个emptyDir的volume。

然后开发人员定义了一个Pod的YAML：
``` 
apiVersion: v1
kind: Pod
metadata:
  name: website
  labels:
    app: website
    role: frontend
spec:
  containers:
    - name: website
      image: nginx
      ports:
        - containerPort: 80
```
这个Pod中定义了labels为`role:fronted`，代表着会被上述PodPreset选中。


运维人员首先创建了PodPreset对象，开发人员才创建Pod:
``` 
$ kubectl create -f preset.yaml
$ kubectl create -f pod.yaml
```


这个Pod运行起来之后，可以看到这个Pod中多了PodPreset定义的追加内容，另外还会自动加上一个annotation，代表这个Pod对象被PodPreset改动过。