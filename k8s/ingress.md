## Ingress

ingress是一种全局的、为了代理不同后端Service而设置的负载均衡服务。可以把ingress理解成 Service的 "Service"

Ingress是两部分组成： Ingress Controller + Ingress对象


### 使用示例

假如有如下站点 https://cafe.example.com。其中 https://cafe.example.com/coffee 对应的服务Pod是 coffee， https://tea.example.com/tea 对应的服务Pod是tea

Ingress可以提供一个统一的负载均衡，从而实现访问不同的域名时，访问用户不同的Deployment， 如下:
```
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: cafe-ingress
spec:
  tls:
  - hosts:
    - cafe.example.com
    secretName: cafe-secret
  rules:
  - host: cafe.example.com
    http:
      paths:
      - path: /tea
        backend:
          serviceName: tea-svc
          servicePort: 80
      - path: /coffee
        backend:
          serviceName: coffee-svc
          servicePort: 80
```
上述yaml中，有个rules字段，在kubernetes中叫做: IngressRule。

IngressRule的key就叫做 host， 它必须是一个标准的域名格式，不能是ip。当用户访问cafe.example
.com的时候，就访问到了这个Ingress对象，然后根据IngressRule来进一步转发请求。IngressRule的规则定义中有一个path字段，这里一个path都对应一个后端的service。

### Nginx Ingress Controller

可以看出来Ingress对象，是一个反向代理的服务，这个代理服务的转发规则就是 IngressRule，配置方式与Nginx类似

在实际的使用中，需要在kubernetes集群中部署一个Ingress Controller， Ingress Controller会根据用户定义的Ingress对象，提供对应的代理能力。一般使用 Nginx Ingress 
Controller


#### 部署 Nginx Ingress Controller

这里用Helm来部署，关于Helm的使用在附录中贴上了。

``` 
$ kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/mandatory.yaml
```
`mandatory.yaml`是Nginx官方维护的Ingress Controller，内容如下:
``` 
kind: ConfigMap
apiVersion: v1
metadata:
  name: nginx-configuration
  namespace: ingress-nginx
  labels:
    app.kubernetes.io/name: ingress-nginx
    app.kubernetes.io/part-of: ingress-nginx
---
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: nginx-ingress-controller
  namespace: ingress-nginx
  labels:
    app.kubernetes.io/name: ingress-nginx
    app.kubernetes.io/part-of: ingress-nginx
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: ingress-nginx
      app.kubernetes.io/part-of: ingress-nginx
  template:
    metadata:
      labels:
        app.kubernetes.io/name: ingress-nginx
        app.kubernetes.io/part-of: ingress-nginx
      annotations:
        ...
    spec:
      serviceAccountName: nginx-ingress-serviceaccount
      containers:
        - name: nginx-ingress-controller
          image: quay.io/kubernetes-ingress-controller/nginx-ingress-controller:0.20.0
          args:
            - /nginx-ingress-controller
            - --configmap=$(POD_NAMESPACE)/nginx-configuration
            - --publish-service=$(POD_NAMESPACE)/ingress-nginx
            - --annotations-prefix=nginx.ingress.kubernetes.io
          securityContext:
            capabilities:
              drop:
                - ALL
              add:
                - NET_BIND_SERVICE
            # www-data -> 33
            runAsUser: 33
          env:
            - name: POD_NAME
              valueFrom:
                fieldRef:
                  fieldPath: metadata.name
            - name: POD_NAMESPACE
            - name: http
              valueFrom:
                fieldRef:
                  fieldPath: metadata.namespace
          ports:
            - name: http
              containerPort: 80
            - name: https
              containerPort: 443
```
这个YAML中使用的镜像是`nginx-ingress-controller`，这个Pod本身就是一个监听Ingress对象以及它所代理后端Service变化的控制器，当一个新的Ingress对象由用户创建后，Nginx Ingress Controller 就会根据Ingress对象里定义的内容在生成一份对应的Nginx配置文件(/etc/nginx/nginx.conf),并使用这个配置文件启动一个Nginx服务

一旦Ingress对象被更新，Nginx-Ingress-Controller就会更新这个配置文件

> Nginx Ingress Controller提供的服务是一个可以根据 Ingress 对象和被代理后端Service的变化，来自动进行更新的Nginx负载均衡器

然后创建一个Service把Nginx Ingress Controller管理的Nginx服务暴露出去。
```
apiVersion: v1
kind: Service
metadata:
  name: ingress-nginx
  namespace: ingress-nginx
  labels:
    app.kubernetes.io/name: ingress-nginx
    app.kubernetes.io/part-of: ingress-nginx
spec:
  type: NodePort
  ports:
    - name: http
      port: 80
      targetPort: 80
      protocol: TCP
    - name: https
      port: 443
      targetPort: 443
      protocol: TCP
  selector:
    app.kubernetes.io/name: ingress-nginx
    app.kubernetes.io/part-of: ingress-nginx
```
这个Service将携带 ingress-nginx 标签的Pod的80和443端口暴露出去


### 附录
- [Helm入门指南](https://mp.weixin.qq.com/s?__biz=MzI3MTI2NzkxMA==&mid=2247486154&idx=1&sn=becd5dd0fadfe0b6072f5dfdc6fdf786&chksm=eac52be3ddb2a2f555b8b1028db97aa3e92d0a4880b56f361e4b11cd252771147c44c08c8913#rd)
- [通过Helm部署Nginx Ingress Controller](https://www.hi-linux.com/posts/35116.html)