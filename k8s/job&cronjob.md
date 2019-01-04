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