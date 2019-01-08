## Kubernetes网络模型

容器间跨主机网络有一个共性，就是用户的容器都连接在docker0网桥上，网络插件则在宿主机上创建了一个特殊的设备(UDP的TUN，VXLAN的VTEP)， docker0 和这个设备之间通过路由表(ip route)进行协作

网络插件的目的就是通过某种方式，把不同宿主机上的特殊设备连通，达到跨主机通信的目的。

kubernetes通过一个CNI的接口，维护了一个单独的网桥来代替docker0。这个网桥叫做：CNI网桥，在宿主机上的默认名称是:cni0。

kubernetes中跨主机通信与Flannel的VXLAN模式几乎一样，只是把docker0网桥换成了CNI网桥。如下:
![](../images/k8s/kubernetes_network.png)

