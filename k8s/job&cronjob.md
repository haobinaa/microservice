## Job和CronJob调度离线业务

有些离线业务，比如离线计算等业务，与在线任务调度不同，kubernetes提供了一种描述离线业务的API对象:Job

### Job API对象

一个Job API对象的定义如下:
``` 
apiVersion: batch/v1
kind: Job
metadata:
  name: pi
spec:
  template:
    spec:
      containers:
      - name: pi
        image: resouer/ubuntu-bc 
        command: ["sh", "-c", "echo 'scale=10000; 4*a(1)' | bc -l "]
      restartPolicy: Never
  backoffLimit: 4
```
这个Job里面的Pod，执行了一个任务，大概意思就是计算π的值，scale是之小数点后10000位。这里的Pod模板中定义了 
restartPolicy=Never，因为离线计算永远不应该被重启，重启就会在计算一遍。restartPolicy在Job中只允许被设置为Never或OnFailure，而在Deployment中则只被允许设置为Always。

执行这个Job,在查看:
``` 
$ kubectl create -f job.yaml
$ kubectl describe jobs/pi
Name:             pi
Namespace:        default
Selector:         controller-uid=c2db599a-2c9d-11e6-b324-0209dc45a495
Labels:           controller-uid=c2db599a-2c9d-11e6-b324-0209dc45a495
                  job-name=pi
Annotations:      <none>
Parallelism:      1
Completions:      1
..
Pods Statuses:    0 Running / 1 Succeeded / 0 Failed
Pod Template:
  Labels:       controller-uid=c2db599a-2c9d-11e6-b324-0209dc45a495
                job-name=pi
  Containers:
   ...
  Volumes:              <none>
Events:
  FirstSeen    LastSeen    Count    From            SubobjectPath    Type        Reason            Message
  ---------    --------    -----    ----            -------------    --------    ------            -------
  1m           1m          1        {job-controller }                Normal      SuccessfulCreate  Created pod: pi-rq5rl
```
可以看到Job对象被创建后，它的Pod模板(Pod Template)会自动加上 controller-uid=<随机字符串>这样的Label。


#### Job执行失败
上述说了Job对象的restartPolicy是Never，那么这个Job任务失败了怎么办。Job Controller就会不断的创建新的Pod， 但是并不是无限制的创建，上述YAML中定义了spec.backoffLimit=4，则代表重试次数为4

如果定义restartPolicy=OnFailure，离线作业失败后，Job Controller则会不断的尝试重启Pod。

#### Job执行完成

Job执行完成后，这个Pod的状态就会变成Completed。

如果这个Job因为某种原因一直不结束， Job API对象的 spec.activeDeadlineSeconds 可以设置最长运行时间，如下:
``` 
spec:
 backoffLimit: 5
 activeDeadlineSeconds: 100
```
这样运行超过100s后，这个Job所有的Pod都会被终止， Pod状态的终止原因 reason：DeadLineExceeded


### Job Controller对并行作业的控制

有些业务会并行运行(Batch Job)，Job对象中有两个控制并行的参数:
1. spec.parallelism, 定义一个Job在任意时间最多可以启动多少Pod同时运行
2. spec.completions, 定义Job至少要完成的Pod数目，即Job的最小完成数

例如，改变之前的Job,添加这两个参数:
``` 
apiVersion: batch/v1
kind: Job
metadata:
  name: pi
spec:
  parallelism: 2
  completions: 4
  template:
    spec:
      containers:
      - name: pi
        image: resouer/ubuntu-bc
        command: ["sh", "-c", "echo 'scale=5000; 4*a(1)' | bc -l "]
      restartPolicy: Never
  backoffLimit: 4
```
指定并行数为2，最小完成数为4

执行这个新的Job，并查看job:
``` 
$ kubectl get job
NAME      DESIRED   SUCCESSFUL   AGE
pi        4         0            3s
```
Job维护了两个字段，DESIRED是completes定义的最小完成数

然后可以看到Job首先启动了2个Pod来计算(parallelism定义的最大并行数)

每当一个Pod完成计算进入Completed，就会有一个新的Pod被创建出来，直到最小完成数达成，整个Job也就执行完了，它的SUCCESSFUL字段就变成了4:
``` 
$ kubectl get pods 
NAME       READY     STATUS      RESTARTS   AGE
pi-5mt88   0/1       Completed   0          5m
pi-62rbt   0/1       Completed   0          4m
pi-84ww8   0/1       Completed   0          4m
pi-gmcq5   0/1       Completed   0          5m

$ kubectl get job
NAME      DESIRED   SUCCESSFUL   AGE
pi        4         4            5m
```


### 使用Job的常见方法

#### 1.外部管理器+Job模板

把Job的YAML定义为一个模板，用外部工具来生成这些Job，例如

Job定义如下:
``` 
apiVersion: batch/v1
kind: Job
metadata:
  name: process-item-$ITEM
  labels:
    jobgroup: jobexample
spec:
  template:
    metadata:
      name: jobexample
      labels:
        jobgroup: jobexample
    spec:
      containers:
      - name: c
        image: busybox
        command: ["sh", "-c", "echo Processing item $ITEM && sleep 5"]
      restartPolicy: Never
```
这个Job中有$ITEM这样的变量，在创建Job的时候:
1. 替换$ITEM
2. 使用的同一个模板，都有 jobgroup:jobexample 这个Label， 这一组Job都使用这个标识

第一步的shell类似如下,这样就可以把所有的job文件创建好:
``` 
mkdir ./jobs
for i in apple banana cherry
do
  cat job-tmpl.yaml | sed "s/\$ITEM/$i/" > ./jobs/job-$i.yaml
done
```
然后通过kubectl创建这些job
``` 
$ kubectl create -f ./jobs
$ kubectl get pods -l jobgroup=jobexample
NAME                        READY     STATUS      RESTARTS   AGE
process-item-apple-kixwv    0/1       Completed   0          4m
process-item-banana-wrsf7   0/1       Completed   0          4m
process-item-cherry-dnfu9   0/1       Completed   0          4m
```
这样这一组Job就创建起来了，这种情况下Job对象的completions和parallelism两个字段都默认为1，作业Pod的控制，由外部工具来管理(TensorFlow社区的KubeFlow就是这种模式)

#### 2.拥有固定任务数的Job

这种情况下，只关系是否有指定数目(spec.completions)个任务成功，执行的并发度不必关心

除了之前的并行例子，还看可以使用工作队列(Worker Queue)进行任务分发，Job的YAML如下:
``` 
apiVersion: batch/v1
kind: Job
metadata:
  name: job-wq-1
spec:
  completions: 8
  parallelism: 2
  template:
    metadata:
      name: job-wq-1
    spec:
      containers:
      - name: c
        image: myrepo/job-wq-1
        env:
        - name: BROKER_URL
          value: amqp://guest:guest@rabbitmq-service:5672
        - name: QUEUE
          value: job1
      restartPolicy: OnFailure
```
这个Job的completions是8，意味着需要处理8个任务，就是工作队列将会放入8个任务。

这里需要一个工作队列，例如选择RabbitMQ，所以在Pod模板里面定义了BROKER_URL，作为消费者。这里的RabbitMQ已经运行起来了

一旦kubectl创建了这个Job，那么将以并发度为2，创建出8个pod。每个Pod都会去连BROKER_URL,从RabbitMQ里面读取任务，然后处理，每个Pod的逻辑，以伪代码表示:
``` 
/* job-wq-1 的伪代码 */
queue := newQueue($BROKER_URL, $QUEUE) //连接queue
task := queue.Pop() // 获取task
process(task) // 执行任务
exit // 完成后退出
```

#### 3. 指定并行数，但不设定completion值

这种情况下，必须得解决，什么时候启动新的Pod，什么时候Job才算执行完成。 因为这个时候Job的总数是未知的， 所以不仅需要一个工作队列来分发任务，还需要能够判断工作队列为空(所有工作已经完成)

Job的定义没有变化，只是不需要定义completion的值了:
``` 
apiVersion: batch/v1
kind: Job
metadata:
  name: job-wq-2
spec:
  parallelism: 2
  template:
    metadata:
      name: job-wq-2
    spec:
      containers:
      - name: c
        image: gcr.io/myproject/job-wq-2
        env:
        - name: BROKER_URL
          value: amqp://guest:guest@rabbitmq-service:5672
        - name: QUEUE
          value: job2
      restartPolicy: OnFailure
```

对应Pod的逻辑如下:
``` 
/* job-wq-2 的伪代码 */
// 如果队列不为空，就从队列中读取任务执行
for !queue.IsEmpty($BROKER_URL, $QUEUE) {
  task := queue.Pop()
  process(task)
}
print("Queue empty, exiting")
exit

```


### CronJob

CronJob描述的是定时任务，API对象定义类似:

``` 
apiVersion: batch/v1beta1
kind: CronJob
metadata:
  name: hello
spec:
  schedule: "*/1 * * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: hello
            image: busybox
            args:
            - /bin/sh
            - -c
            - date; echo Hello from the Kubernetes cluster
          restartPolicy: OnFailure

```
这个YAML中定义了一个jobTemplate, 意味着cronJob只是一个Job的控制器，控制Job什么时候执行，shedule定义的是一个Unix Cron格式的表达式

需要注意的时，定时任务的特殊性，有些任务没有执行完，新的Job就产生了。这里可以使用spec.concurrencyPolicy来指定具体策略:
1. concurrencyPolicy=Allow,默认值， Job可以同时存在
2. concurrencyPolicy=Forbid， 不会创建新的Pod
3. concurrencyPolicy=Replace， 新的Job会替换掉没有执行完的、旧的Pod