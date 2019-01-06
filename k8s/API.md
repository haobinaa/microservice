## 声明式API编程


### 命令式和声明式

#### 命令式操作

在Docker swamp中，对容器的操作都是基于命令行的，创建和更新操作类似:
``` 
$ docker service create --name nginx --replicas 2  nginx
$ docker service update --image nginx:1.7.9 nginx
```
这种用命令去执行的操作称之为命令式操作


#### 声明式操作

在kubernetes中，创建和更新nginx操作一般如下，首先定义一个Deployment的YAML:
``` 
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
spec:
  selector:
    matchLabels:
      app: nginx
  replicas: 2
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx
        ports:
        - containerPort: 80

```
当我们create这个YAML的时候，两个Nginx的Pod就会运行起来，如果要更新nginx，可以更新这个YAML里的Pod模板就可以了。

类似:
``` 
// 创建
$ kubectl create -f nginx.yaml
// 修改后更新
$ kubectl replace -f nginx.yaml
```
上述用命令的方式，也是命令式编程

如果用如下操作
```
 // 创建
 $ kubectl apply -f nginx.yaml
// 修改后更新
$ kubectl apply -f nginx.yaml
 ```
 这样就是用新的YAML中的API对象替换原来的API对象，kubectl apply实际上是执行了一个"patch"操作(类似的还有kubectl edit)，这样的方式就是声明式

### kubernetes中的API

在kubernetes中，一个API对象在Etcd中完整资源路径是由：Group（API组)、Version(API版本)、Resource(API资源类型)三个部分组成的。那么kubernetes中的所有API对象，应该是一种树状结构，如下：
![](../images/k8s/api.png)
kubernetes中API对象的组织方式是层层递进的，比如一个CronJob对象，YAML的开头应该如下:
``` 
apiVersion: batch/v2alpha1
kind: CronJob
// ...省略
```
这里CronJob就是资源类型(Resource)，batch就是它的组(Group)，v2alpha1就是它的版本(Version)

kubernetes解析并找到CronJob的过程:
1. 匹配API对象的组