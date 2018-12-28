## 应用容器化

使用kubernetes发布容器化的应用

### 编写配置文件

kubernetes跟docker不同， 不推荐使用命令的方式运行容器， 而是用YAML文件的把：容器的定义、参数、配置， 统统记录在一个YAML文件中， 然后用`kubectl create -f 配置文件` 运行起来。


#### 配置文件例子

一个配置文件(nginx-deployment.yaml)的例子:
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
        image: nginx:1.7.9
        ports:
        - containerPort: 80
       volumeMounts:
       - mountPath: "/usr/share/nginx/html"
        name: nginx-vol
      volumes:
      - name: nginx-vlo
        emptyDir: {}
```

这个YAML文件， 对应到kubernetes中， 是一个API Object(API对象)。 kubernetes会根据这些字段， 创建出对象定义的容器或其他类型的API资源。

- YAML文件中的kind字段， 指定了API对象的type， 这里是一个Deployment。Deployment是一个定义多副本应用(多个副本的Pod)对象， Deployment还负责在Pod定义发生变化时，对每个副本进行滚动更新。这里给它定义的副本数是2(spec.replicas:2)

- Pod模板(spec.template)描述了创建Pod的细节。上述例子中， Pod里面只有一个容器， 容器的镜像(spec.containers.image)是nginx:1.7.9, 这个容器监听的端口(spec
.containers.ports,containerPort)是80

- 每个API对象都有一个叫做metadata的字段，这个字段是对API对象的标识，即元数据。kubernetes是根据这个元数据来找到这个对象，其中最主要的字段是Labels。Labels是一组key-value
格式的标签，Deployment这样的控制器对象，可以根据Labels字段从kubernetes中过滤出它所需要的被控制对象。上述例子中，Deployment会把所有正在运行的、携带`app:nginx`标签的Pod识别为被管理的对象，
 并确保这些Pod的总数严格等于2个。这个过滤规则的定义是在`spec.selector.matchLables`字段中
 
 - volumes(template.spec.volumes)声明了这个Pod所有的Volume， 上述例子的volume的名字叫`nginx-vol`,类型是`emptyDir`。这种方式等于Docker的隐式Volume参数
 (不显示声明宿主机目录的Volume)，所以kubernetes也会在宿主机上创建一个临时目录，这个目录将会被绑定到容器所声明的目录上。Pod容器中， 使用的是`volumeMounts`字段来声明自己要挂载哪个volume， 
 并通过mountPath字段来定义容器内的volume目录。
 
 #### 运行容器
 
 可以通过`kubectl create -f nginx-deployment.yaml`将上述文件运行起来。
 
 
 然后通过 `kubectl get pods -l app=nginx`来检查YAML的运行状态， -l参数的意思是匹配所有 `app=nginx`标签的Pod
 
可以通过`kubectl describe pod pod-name`来查看这个API对象的细节， 其中打印出来的`Events`部分是kubernetes执行中对这个对象的所有重要操作， 这个是调试的重要信息


让API对象做了修改，比如镜像升级， 需要运行`kubectl replace -f nginx-deployment.yaml`来更新

#### 声明式API管理k8s镜像

前面的内容使用 create、replace等操作来创建、更新对象， kubernetes推荐的是用"声明式 API"来管理，  如下:
``` 
kubectl apply -f nginx-deployment.yaml

# 修改 nginx-deployment.yaml 的内容
kubectl apply -f nginx-deployment.yaml
```
作为用户不用关心是创建还是更新， 执行的命令始终是`kubectl apply`， 而kubernetes会根据YAML文件的内容变化自动处理