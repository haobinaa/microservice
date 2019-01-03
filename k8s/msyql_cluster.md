## 部署mysql集群

使用StatefulSet部署mysql集群

### 部署一个mysql的集群步骤
1.安装Master节点， 并使用`XtraBackup`将Master节点的数据备份到指定目录(XtraBackup使用参考资料中有)

这会在目标目录生成一个备份信息文件:xtrabackup_binlog_info，包含两个信息:
``` 
$ cat xtrabackup_binlog_info
TheMaster-bin.000001     481
```

2.配置Slave节点。Salve启动时，需要将Master节点的备份数据连同备份信息文件一起拷贝到自己的数据目录下(/var/lib/mysql)，然后执行sql:
``` 
TheSlave|mysql> CHANGE MASTER TO
                MASTER_HOST='$masterip',
                MASTER_USER='xxx',
                MASTER_PASSWORD='xxx',
                MASTER_LOG_FILE='TheMaster-bin.000001',
                MASTER_LOG_POS=481;

```
MASTER_LOG_FILE和MASTER_LOG_POS就是备份对应的二进制日志(Binary Log)文件的名称，和开始的位置(偏移量)。这两个参数也是xtrabackup_binlog_info文件里面的两部分内容

3.启动Slave节点 
``` 
TheSlave|mysql> START SLAVE;
```

4. 往集群中添加更多的Slave节点，新添加的slave节点里面的数据，来源于已经存在的Slave节点

### kubernetes部署mysql集群

如果要把上述部署的流程迁移到kubernetes上，需要解决以下问题:
1. Master节点和Slave节点需要有不同的配置文件(my.cnf)
2. Master节点和Slave节点能够传输备份信息文件
3. Slave节点第一次启动时，需要执行一些初始化SQL操作

这个MySQL集群同时拥有拓扑状态(主从节点的区别)，存储状态(Mysql保存在本地的数据)，所以需要用StatefulSet来解决这三个问题

#### Master节点和Slave节点需要不同的配置文件

这个问题需要给主从节点分贝准备两份不同的MySQL配置文件，然后根据Pod的序号挂载进去即可。

这样的配置文件信息，应该保存在ConfigMap里面供Pod使用。它定义如下:
``` 
apiVersion: v1
kind: ConfigMap
metadata:
  name: mysql
  labels:
    app: mysql
data:
  master.cnf: |
    # 主节点 MySQL 的配置文件
    [mysqld]
    log-bin
  slave.cnf: |
    # 从节点 MySQL 的配置文件
    [mysqld]
    super-read-only
```
这个ConfigMap定义了master.cnf和slave.cnf两个配置文件:
- master.cnf开启了log-bin，即：使用二进制日志文件的方式进行主从复制
- slave.cnf开启了super-read-only，代表的是从节点会拒绝除了主节点的数据同步操作之外的所有写操作

ConfigMap的data是Key-Value格式，"|"后面的内容代表配置数据的Value，这份配置数据的value将来挂载进对应的Pod后(比如Master)，就会在volume目录下生成一个叫做 master.cnf 的配置文件

#### 创建StatefulSet的Service

创建两个service供StatefulSet使用：
``` 
# mysql.yaml
apiVersion: v1
kind: Service
metadata:
  name: mysql
  labels:
    app: mysql
spec:
  ports:
  - name: mysql
    port: 3306
  clusterIP: None
  selector:
    app: mysql

---

# mysql-read.yaml
apiVersion: v1
kind: Service
metadata:
  name: mysql-read
  labels:
    app: mysql
spec:
  ports:
  - name: mysql
    port: 3306
  selector:
    app: mysql
```
两个service都代理了携带label为app=mysql的Pod，端口映射都是mysql的3306。第一个`mysql.yaml`的service是一个Headless Service，它能够让Pod通过DNS记录来固定拓扑状态，如mysql-0.mysql。  第二个`mysql-read.yaml`是一个常规的Service

我们规定所有用户的读请求，都访问第二个Service自动分配的记录(mysql-read)，这样读请求就可以转发到任意一个Master节点或Slave节点；所有的写请求，都通过DNS记录访问到Mysql的Master节点。


#### Master和Slaver能够传输备份文件
todo ....

### 参考资料
- [XtraBackup备份Mysql数据](https://www.jianshu.com/p/43cd0396d997)
- [MySQL双机数据同步备份](https://blog.csdn.net/ChenShiAi/article/details/53611781)