## Service

由于Pod的Ip不是固定的，kubernetes使用Service对外部提供服务


### Service的使用

一个Service的定义如下:
``` 
apiVersion: v1
kind: Service
metadata:
  name: hostnames
spec:
  selector:
    app: hostnames
  ports:
  - name: default
    protocol: TCP
    port: 80
    targetPort: 9376
```
这个Service代理了携带 app=hostname 标签的Pod，并且这个Service的80端口代理了Pod的9376端口。

然后应用Deployment如下:
``` 
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hostnames
spec:
  selector:
    matchLabels:
      app: hostnames
  replicas: 3
  template:
    metadata:
      labels:
        app: hostnames
    spec:
      containers:
      - name: hostnames
        image: k8s.gcr.io/serve_hostname
        ports:
        - containerPort: 9376
          protocol: TCP
```
这个应用的作用就是每次访问9376端口就返回它自己的hostname。

被selector选中的Pod，称为Service的Endpoints， 启动了Pod后，可以通过`kubectl get ep hostnames`查看到:
``` 
$ kubectl get endpoints hostnames
NAME        ENDPOINTS
hostnames   10.244.0.5:9376,10.244.0.6:9376,10.244.0.7:9376
```
此时就可以通过Service的IP访问它代理的Pod了:
``` 
$ kubectl get svc hostnames
NAME        TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)   AGE
hostnames   ClusterIP   10.0.1.175   <none>        80/TCP    5s

$ curl 10.0.1.175:80
hostnames-0uton

$ curl 10.0.1.175:80
hostnames-yp2kp

$ curl 10.0.1.175:80
hostnames-bvc05
```
可以看到Service提供的是Round Robin(轮询)的负载均衡策略

### Service的工作原理

Service是由kube-proxy组件加上iptables来共同实现的.

上述的例子中，创建了名为hostname的service，当他被提交给kubernetes，那么kube-proxy就可以通过service的Informer感知到这个Service对象，它会响应一个事件：在宿主机上创建一条这样的iptables规则:
``` 
-A KUBE-SERVICES -d 10.0.1.175/32 -p tcp -m comment --comment "default/hostnames: cluster IP" -m tcp --dport 80 -j KUBE-SVC-NWV5X2332I4OT4T3
```
这条iptables的含义是，凡是目的地址是10.0.1.175、目的端口是80的IP包，都应该跳转到另一条名叫 KUBE-SVC-NWV5X2332I4OT4T3 的iptables链处理。 

这个KUBE-SVC-NWV5X2332I4OT4T3规则是一组规则的集合，如下:
``` 
-A KUBE-SVC-NWV5X2332I4OT4T3 -m comment --comment "default/hostnames:" -m statistic --mode random --probability 0.33332999982 -j KUBE-SEP-WNBA2IHDGP2BOBGZ
-A KUBE-SVC-NWV5X2332I4OT4T3 -m comment --comment "default/hostnames:" -m statistic --mode random --probability 0.50000000000 -j KUBE-SEP-X3P2623AGDH6CDF3
-A KUBE-SVC-NWV5X2332I4OT4T3 -m comment --comment "default/hostnames:" -j KUBE-SEP-57KPRZ3JQVENLNBR
```
它的意思是，转发到那三个Pod所在的位置，并且由于iptables执行是从上到下的，为了保证三条记录的执行概率相同，需要将它们的probability的概率设置成1/3、1/2和1.这样第一条的概率是1/3，第一条没中那么剩下两条规则，第二条的概率应该是1/2。

上述就是Service的转发原理了


### Service与DNS的关系

kubernetes中Service和Pod都会被分配到对应DNS A记录(域名解析到IP)

对于ClusterIp模式的Service， 它的A记录的格式是: xxsvc.cluster.local。当访问这个A记录，返回的就是Service的VIP

对于ClusterIP=None的Headless Service来说，它的A记录也是: xxsvc.cluster.local。但，访问这个A记录的时候，返回的就是它所代理的Pod的Ip地址的合集

对于ClusterIP模式的Service来说它所代理的Pod的A记录格式是 : xxxpod.cluster.local，这个记录指向Pod的ip地址

如果为Pod指定了Headless Service， 并且Pod声明了 hostname 和 subdomain 字段，那么Pod的A记录就变成了: <pod的hostname>xxx.svc.cluster.local，比如
``` 
apiVersion: v1
kind: Service
metadata:
  name: default-subdomain
spec:
  selector:
    name: busybox
  clusterIP: None
  ports:
  - name: foo
    port: 1234
    targetPort: 1234
---
apiVersion: v1
kind: Pod
metadata:
  name: busybox1
  labels:
    name: busybox
spec:
  hostname: busybox-1
  subdomain: default-subdomain
  containers:
  - image: busybox
    command:
      - sleep
      - "3600"
    name: busybox
```
当上述Service和Pod被创建出来后，就可以通过 busybox-1.default-subdomain.default.svc.cluster.local解析到这个Pod的Ip地址了。




### 附录
- [iptables基本概念和原理](https://www.cnblogs.com/foxgab/p/6896957.html)