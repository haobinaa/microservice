## Kubernetes中的RBAC

kubernetes使用RBAC完成授权的工作机制


### kubernetes RBAC概念
- Role:角色， 在kubernetes中是一组规则， 定义了一组kubernetes API对象的操作权限
- Subject:被作用者
- RoleBinding: 定义了 Subject 和 Role 之间的绑定关系

#### Role

Role本身也是一个kubernetes中的API对象，定义如下:
``` 
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  namespace: mynamespace
  name: example-role
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "watch", "list"]
```
这个Role对象指定了它能产生作用的Namespace是 mynamespace 。 
Namespace是kubernetes中的一个逻辑管理单位，不同Namespace的API对象，在通过kubectl命令操作的时候是互相隔开的，如果没有指定Namespace，那么就使用默认的Namespace:default

Role对象的rules就是它定义的规则权限。比如上述例子的规则含义是: 允许被作用者(Subject)对mynamespace 下面的Pod对象进行 GET, WATCH和LIST操作

verbs字段是权限集合，它的全集(kubernetes中所有能对API对象进行的操作)如下:
``` 
verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
```

Role对象的rules字段可以进一步细化，可以只针对某一个具体的对象进行权限设置，如下:
``` 
rules:
- apiGroups: [""]
  resources: ["configmaps"]
  resourceNames: ["my-config"]
  verbs: ["get"]
```
这里的subject，只对名为"my-config"的"configMap"对象进行GET操作权限

#### RoleBinding

RoleBinding也是一个API对象，它的定义如下:
``` 
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: example-rolebinding
  namespace: mynamespace
subjects:
- kind: User
  name: example-user
  apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: Role
  name: example-role
  apiGroup: rbac.authorization.k8s.io
```
RoleBinding对象中有一个字段是subject，即被作用者。它的类型是User(kubernetes中的用户),这个user的name是 
example-user。其实在kubernetes中是没有一个叫User的对象，所以这里的User是一个授权系统中的逻辑概念。它需要外部认证服务(比如Keystone)来提供，或者也可以直接给APIServer指定一个用户名、密码文件，在大多数情况下只需要使用kubernetes"内置用户"就足够了。

roleRef字段让RoleBinding对象可以直接通过名字来引用之前定义的Role对象，从而定义了被作用者(Subject)和角色(Role)之间的绑定关系。

Role和RoleBinding对象都是Namespaced对象(Namespaced Object)，它们只在自己的Namespace内生效， roleRef也只能引用当前Namespace里的Role对象

#### ClusterRole 和 ClusterRoleBinding

对于非Namespaced对象(比如Node)或者某个Role想作用于所有Namespace的时候，需要使用 `ClusterRole` 和 
`ClusterRolebinding`这两个组合。这两个组合的用法与Role和RoleBinding完全一样，不过它们的定义中没有了Namespace字段。如下所示：
``` 
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: example-clusterrole
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "watch", "list"]
```

``` 
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: example-clusterrolebinding
subjects:
- kind: User
  name: example-user
  apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: ClusterRole
  name: example-clusterrole
  apiGroup: rbac.authorization.k8s.io
```
这个组合定义了名叫 example-user 的用户，拥有对所有Namespace里的Pod进行WATCH,GET和LIST操作的权限。

### 内置用户ServiceAccount

之前在RoleBinding提到过kubernetes没有User对象，kubernetes有内置用户，这个内置用户就是之前说的ServiceAccount。

一个简单的ServiceAccount的定义如下:
``` 
apiVersion: v1
kind: ServiceAccount
metadata:
  namespace: mynamespace
  name: example-sa
```
这个ServiceAccount只包含name和namespace

然后编写一个RoleBinding，为这个ServiceAccount分配权限:
``` 
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: example-rolebinding
  namespace: mynamespace
subjects:
- kind: ServiceAccount
  name: example-sa
  namespace: mynamespace
roleRef:
  kind: Role
  name: example-role
  apiGroup: rbac.authorization.k8s.io
```
这个RoleBinding中，subject的kind字段不在是User，而是name为example-sa的ServiceAccount。roleRef引用的任然是Role对象

现在创建一个命名空间mynamespace:
``` 
apiVersion: v1
kind: Namespace
metadata:
  name: mynamespace
  labels:
    name: mynamespace
```

然后依次create service-account、role-binding、role后查看ServiceAccount的信息(`kubectl get sa -n mynamspace -o yaml`)看到的类似如下:
``` 
kubectl get sa -n mynamespace -o yaml


- apiVersion: v1
  kind: ServiceAccount
  metadata:
    creationTimestamp: 2018-09-08T12:59:17Z
    name: example-sa
    namespace: mynamespace
    resourceVersion: "409327"
    ...
  secrets:
  - name: example-sa-token-vmfg6
```
可以看到最下面kubernetes为ServiceAccount自动创建并分配了一个Secret对象(secrets字段)
，这个Secret就是这个ServiceAccount对应的跟APIServer进行交互的授权文件，或者说是一个token。Token文件的内容一般是证书或密码，它以Secret对象的方式保存在Etcd中。


这是用户就可以声明使用这个ServiceAccount了，如：
``` 
apiVersion: v1
kind: Pod
metadata:
  namespace: mynamespace
  name: sa-token-test
spec:
  containers:
  - name: nginx
    image: nginx:1.7.9
  serviceAccountName: example-sa
```
这个Pod中声明了要使用name为example-sa的ServiceAccount，启动这个Pod后可以看到，该ServiceAccount的token，也就是一个Secret对象被kubernetes自动挂载到了容器的 
/var/run/secrete/kubernetes.io/serviceaccount目录下，进入这个pod可以看到:
``` 
$ kubectl exec -it sa-token-test -n mynamespace -- /bin/bash
root@sa-token-test:/# ls /var/run/secrets/kubernetes.io/serviceaccount
ca.crt namespace  token
```
这样容器里的应用就可以用ca.crt来访问APIServer， 当然这个Pod只有GET、WATCH、LIST操作。


如果Pod没有声明ServiceAccount的话，kubernetes会自动在它的namespace下生成一个叫default的ServiceAccount，然后分配给这个Pod。这种默认的ServiceAccount没有关联任何Role，也就是说他有访问APIServer的绝大部分权限