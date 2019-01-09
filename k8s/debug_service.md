## Service调试与外界连通

### 外部访问kubernetes创建的service

Service的访问入口，其实是kube-proxy生成的iptables规则，以及kube-dns生成的DNS记录，一旦离开了这个集群，就无法使用这个信息了。

从外部访问Service，有如下几种方法

#### NodePort

一个Service的定义如下:
``` 
apiVersion: v1
kind: Service
metadata:
  name: my-nginx
  labels:
    run: my-nginx
spec:
  type: NodePort
  ports:
  - nodePort: 8080
    targetPort: 80
    protocol: TCP
    name: http
  - nodePort: 443
    protocol: TCP
    name: https
  selector:
    run: my-nginx
```
这个Service声明了type=NodePort。 然后在ports字段申明了service的8080端口代理Pod的80端口，Service的443端口代理Pod的443端口。如果不显示的申明ports下的nodePort字段，kubernetes会随机分配可用端口，默认范围是30000-32767.

这个时候要访问service只需要:`<宿主机ip>:8080`就可以代理到容器的80端口了。

NodePort模式同样也是借助kube-proxy实现的。比如kube-proxy在每台宿主机上生成一条这样的iptables规则:
```
-A KUBE-NODEPORTS -p tcp -m comment --comment "default/my-nginx: nodePort" -m tcp --dport 8080 -j KUBE-SVC-67RL4FN6JRUPOJYM
```
KUBE-SVC-67RL4FN6JRUPOJYM也是一组路由规则，所以接下来就跟ClusterIp的模式一样了

在NodePort模式下，kubernetes会在IP包离开宿主机发往目的Pod的时候，对IP包进行一次SNAT操作(SNAT含义见附录)，如下:
``` 
-A KUBE-POSTROUTING -m comment --comment "kubernetes service traffic requiring SNAT" -m mark --mark 0x4000/0x4000 -j MASQUERADE
```
这条规则的意思是给即将离开这台主机的IP包进行一次SNAT操作，将这个IP包的源地址替换成这台宿主上CNI网桥地址，或者宿主机本身的IP地址(如果CNI不存在), 这次SNAT操作只针对Service转发出来的IP包(校验IP包是否有0x400标志)

#### 指定一个LoadBlancer类型的Service(适用于公有云上的kubernetes)

``` 
kind: Service
apiVersion: v1
metadata:
  name: example-service
spec:
  ports:
  - port: 8765
    targetPort: 9376
  selector:
    app: example
  type: LoadBalancer
```
公有云上的kubernetes服务，都提供一个叫做CloudProvider的转接层，来跟公有云本身的API进行对接，当上述的 LoadBalancer 提交后， kubernetes 会把Pod的IP地址配置给CloudProvider创建的负载均衡服务。


#### ExternalName
``` 
kind: Service
apiVersion: v1
metadata:
  name: my-service
spec:
  type: ExternalName
  externalName: my.database.example.com
```
YAML中指定 externalName=my.database.example.com ， 这个时候通过Service的DNS访问: my-service.default.svc.cluster.local 那么kubernetes返回的是 my.database.example.com

可以看出来ExternalName类似给Service的DNS加了一条CNAME记录，将my-service.default.svc.cluster.local转向了my.database.example.com了


kubernetes还支持配置公有IP地址，比如:
``` 
kind: Service
apiVersion: v1
metadata:
  name: my-service
spec:
  selector:
    app: MyApp
  ports:
  - name: http
    protocol: TCP
    port: 80
    targetPort: 9376
  externalIPs:
  - 80.11.12.10
```
这个Service为它指定了 externalIPs=80.11.12.10，这样就可以通过公网ip 80.11.12.10访问 Service 代理的Pod了。这里kubernetes要求externalIps必须是至少能路由到一个kubernetes的节点


### 附录
- [linux中的SNAT和DNAT](https://andyyoung01.github.io/2017/03/29/Linux%E7%BD%91%E7%BB%9C%E7%9A%84SNAT%E5%92%8CDNAT/)