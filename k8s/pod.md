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

它的作用是让Pod里的容器能够直接获取这个Pod API对象本身信息。

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














