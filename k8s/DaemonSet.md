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

