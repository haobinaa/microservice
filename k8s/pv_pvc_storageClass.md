### PV

pv描述的是持久化存储数据卷。这个API对象主要定义的是一个持久化存储在宿主机上的目录，如NFS挂载目录(不用机器间可以访问的共享目录, 附录中有说明连接)

通常情况PV对象是由运维人员实现创建的，比如一个NFS类型的PV如下:
``` 
apiVersion: v1
kind: PersistentVolume
metadata:
  name: nfs
spec:
  storageClassName: manual
  capacity:
    storage: 1Gi
  accessModes:
    - ReadWriteMany
  nfs:
    server: 10.244.1.4
    path: "/"
```



### PVC 

pvc描述的是Pod所希望使用的持久化存储的属性，比如Volume存储的大小，可读写权限等。

pvc通常由开发人员创建，或以pvc模板的方式成为StatefulSet的一部分，然后由StatefulSet控制器负责创建，这个在[StatefulSet](./StatefulSet.md)中描述过。一个pvc的声明如下:
``` 
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: nfs
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: manual
  resources:
    requests:
      storage: 1Gi
```
PVC必须要跟某个符合条件的PV进行绑定，这里检查的条件包括两部分:
1. PV和PVC的spec字段，如PV的存储(storage)大小需要满足PVC的条件
2. PV和PVC的storageClass字段必须一样



#### 使用PVC
将PVC和PV绑定后，Pod就可以像使用hostPath等常规类型的Volume一样，在自己的YAML里面声明使用这个PVC了。比如:
``` 
apiVersion: v1
kind: Pod
metadata:
  labels:
    role: web-frontend
spec:
  containers:
  - name: web
    image: nginx
    ports:
      - name: web
        containerPort: 80
    volumeMounts:
        - name: nfs
          mountPath: "/usr/share/nginx/html"
  volumes:
  - name: nfs
    persistentVolumeClaim:
      claimName: nfs
```
Pod只需要在volume字段声明需要使用的PVC的名字，这样创建Pod之后，kubelet就会把这个PVC所对应的PV，也就是一个NFS类型的Volume挂载在这个Pod容器内的目录

##### PVC和PV绑定失败

但是当创建Pod的时候，kubernetes中没有合适的PV跟Pod声明要使用的PVC绑定，这个时候容器想要使用的Volume就不存在，Pod的启动就会报错

在kubernetes中有一个专门处理持久化存储器的控制器，叫做 `VolumeController`， 它专门维护着多个循环控制，其中有一个循环就负责处理PV和PVC的绑定，叫做`PersistentVolumeController`。

PersistentVolumeController会不断检查当前每一个PVC是不是已经处于Bound状态，如果不是，它会遍历所有的可用的PV，并尝试与这个PVC绑定。kubernetes用这个机制保证用户提交的PVC，只要有合适的PV出现，就能很快进行绑定。

PV和PVC的绑定，实际上是将PV对象的名字，填在了PVC对象的 `spec.volumeName`上， 这样只要kubernetes获取了这个PVC对象，就一定能找到它所绑定的PV

### PV对象变为容器持久化的过程

Volume的挂载机制，是将宿主机上的目录跟容器的目录绑定挂载到了一起。

持久化Volume指的是这个宿主机上的目录，具备持久性，即：这个目录里面的内容不会因为容器的删除而被清理；也不会跟当前宿主机绑定，这样当容器重启或者在其他节点创建出来，仍然可以通过挂载这个Volume来访问里面的内容。

emptyDir和hostPath这样的Volume并不具有持久化的特性，它们会被kubelet清理掉， 也无法迁移到其他节点上。

持久化Volume的实现往往依赖于一个远程存储服务，例如远程文件存储(NFS,GlusterFS)、远程块存储(公有云的远程磁盘)
等。kubernetes可以使用这些存储服务，为容器准备一个持久化的存储目录，当容器往这个目录中写入的文件，都会被保存在远程存储中，从而让这个目录有了"持久性"

#### 两段式处理创建持久化宿主机目录

kubernetes用两段操作准备持久化目录。

1. 第一段操作是为虚拟机挂载远程磁盘的操作，这个阶段称为Attach。这个阶段kubernetes将远程的磁盘挂载在Pod所在的宿主机上

2. 第二个阶段将这个磁盘格式化并挂载到Volume宿主机目录， 这个阶段称为Mount

这两个阶段的区别是:
- 对于Attach阶段，kubernetes提供的可用参数是nodeName，即宿主机名称
- 对于Mount阶段，kubernetes提供的可用参数是dir，即Volume的宿主机目录

经过了两阶段处理，就可以得到一个"持久化"的Volume宿主机目录。接下来kubelet只要把这个Volume目录通过CRI里的Mount参数传递给Docker，就可以为Pod里面的容器挂载这个"持久化"的Volume
了，相当于执行了命令个:
``` 
docker run -v /var/lib/kubelet/pods/<Pod 的 ID>/volumes/kubernetes.io~<Volume 类型 >/<Volume 名字 >:/< 容器内的目标目录 > 我的镜像 ...
```
kubernetes处理PV大概就是以上这些流程，两段式处理+CRI请求Docker

### StorageClass

PVC需要运维人员事先创建PV，但是一个大规模的kubernetes集群可能成千上万个PV，随着新的PVC的提交，必须不断的创建PV，这种情况下人工操作基本不可能做到。

kubernetes提供了一套自动创建PV的机制： Dynamic Provisioning。 Dynamic Provisioning机制依靠的就是StorageClass API对象。


StorageClass对象的作用就是创建PV模板。StorageClass对象定义如下两部分:
1. PV的属性。如存储类型、Volume的大小
2. 创建这个PV需要用到的存储插件，如Ceph等

#### GCE StorageClass
如定义一个共有磁盘的云存储(比如Google的GCE)作为持久化磁盘，运维人员可以定义StorageClass如下:
``` 
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: block-service
provisioner: kubernetes.io/gce-pd
parameters:
  type: pd-ssd
```
这个StorageClass的 provisioner 字段的值是: kubernetes.io/gce-pd , 这是kubernetes中内置的GCE PD(GCE Persistent Disk)存储插件的名字

parameters字段是PV的参数，这里type=pd-ssd， 代表这个PV的类型是SSD格式的GCE远程磁盘


#### Rook StorageClass

如果不是GCE，定义一个Rook存储服务的StorageClass如下:
```` 
# Rook集群
apiVersion: ceph.rook.io/v1beta1
kind: Pool
metadata:
  name: replicapool
  namespace: rook-ceph
spec:
  replicated:
    size: 3

# StorageClass使用存储插件是Rook
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: block-service
provisioner: ceph.rook.io/block
parameters:
  pool: replicapool
  # 必须和Rook集群的名字一样
  clusterNamespace: rook-ceph
````

创建这个名为book-service的StorageClass之后，在PVC中指定使用的StorageClass就可以用了，如下:
``` 
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: claim1
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: block-service
  resources:
    requests:
      storage: 30Gi
```
这个PVC里指定使用 storageClassName 为 book-service， 就可以使用Rook创建出来的PV了。

这个PVC会绑定一个kubernetes自动创建出来的PV，如下:
``` 
$ kubectl describe pvc claim1
Name:           claim1
Namespace:      default
StorageClass:   block-service
Status:         Bound
Volume:         pvc-e5578707-c626-11e6-baf6-08002729a32b
Labels:         <none>
Capacity:       30Gi
Access Modes:   RWO
No Events.
```
查看这个自动创建出来的PV的属性，可以看到与PVC一样的StorageClass:
```
$ kubectl describe pv pvc-e5578707-c626-11e6-baf6-08002729a32b
Name:            pvc-e5578707-c626-11e6-baf6-08002729a32b
Labels:          <none>
StorageClass:    block-service
Status:          Bound
Claim:           default/claim1
Reclaim Policy:  Delete
Access Modes:    RWO
Capacity:        30Gi
...
No events.
```
这个PV和PVC有着相同的StorageClass， 因为kubernetes只会将StorageClass相同的PV和PVC绑定起来

#### StorageClass中PV和PVC的绑定

在最开始声明的PV和PVC中都声明了 storageClassName=manual，但是集群中并没有名为manual的StorageClass， 这样kubernetes进行的就是Static Provisioning(静态绑定，PVC和PV的绑定在自己的定义当中)


### 本地化持久卷

之前讲述的都是用的远程磁盘(NFS,公有云)，如果不依赖远程存储服务，如果直接使用本地磁盘，它的读写性能相比大多数远程存储要好得多。

kubernetes提供了这样的特性，称为Local Persistent Volume。但是 Local Persistent Volume的 适用范围并不广，它使用于: 高优先级系统应用，需要在多个不同节点上存储数据， 对IO敏感。典型的应用包括:分布式存储MongoDB，分布式文件系统GlusterFS、Geph等

todo.......

### 附录

- [nfs原理详解](http://blog.51cto.com/atong/1343950)
- [Ceph-一个开源的分布式存储平台](http://dockone.io/article/307)