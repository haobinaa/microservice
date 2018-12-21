

在[项目介绍中](https://github.com/haobinaa/microservice/blob/master/dubbo/README.md)说了，Dubbo 中除了 Service 和 Config 层为 API 
外，其他各层均为 SPI，为 SPI 意味着下面各层都是组件化可以被替换的。

Dubbo 增强的 SPI 功能是从 JDK 标准 SPI 演化而来的。

### JDK中的SPI标准

JDK 中的 SPI（Service Provider Interface）是面向接口编程的，服务规则提供者会在 JRE 的核心 API 里面提供服务访问接口，而具体实现则由其他开发商提供, 是JDK内置的一种服务提供发现机制。

当服务的提供者提供了一种接口的实现之后，需要在classpath下的META-INF/services/目录里创建一个以服务接口命名的文件，这个文件里的内容就是这个接口的具体的实现类。当其他的程序需要这个服务的时候，就可以通过查找这个jar包（一般都是以jar包做依赖）的META-INF/services/中的配置文件，配置文件中有接口的具体实现类名，可以根据这个类名进行加载实例化，就可以使用该服务了。

例如规范制定者在 rt.jar 包里面定义了数据库的驱动接口` java.sql.Driver`。MySQL 实现的开发商则会在 MySQL 的驱动包的` META-INF/services` 文件夹下建立名称为 java.sql
.Driver 的文件，文件内容就是 MySQL 对 java.sql.Driver 接口的实现类:`com.mysql.jdbc.Driver`

在java的类加载当中， 如果一个类由类加载器 A 加载，那么这个类依赖的类也是由相同的类加载器加载。用来搜索开发商提供的 SPI 扩展实现类的 API 类（ServiceLoader）是使用 Bootstrap 
ClassLoader 加载的，那么 ServiceLoader 里面依赖的类应该也是由 Bootstrap CalssLoader 来加载。 但是用户提供的包含SPI实现类的Jar包是由Appclassloader记载， 所以需要一种违反双亲委派模型的方法，线程上下文类加载器 ContextClassLoader 就是为了解决这个问题。

在`ServiceLoader`的`load()`方法中:
``` 
public final class ServiceLoader<S> implements Iterable<S> {
    public static <S> ServiceLoader<S> load(Class<S> service) {
        // （5）获取当前线程上下文加载器
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return ServiceLoader.load(service, cl);
    }

    public static <S> ServiceLoader<S> load(Class<S> service, ClassLoader loader) {
        return new ServiceLoader<>(service, loader);
    }
       //(6) 传递该类加载器到新构造的 ServiceLoader 的成员变量 loader。
    private ServiceLoader(Class<S> svc, ClassLoader cl) {
        service = svc;
        loader = cl;
        reload();
    }
```
可以看到代码（6）传递该类加载器到新构造的 ServiceLoader 的成员变量 loader。 loader将在`ServiceLoader`的内部类`LazyIterator`的`next()`方法中加载类

``` 
public S next() {
            if (acc == null) {
                return nextService();
            } else {
                PrivilegedAction<S> action = new PrivilegedAction<S>() {
                    public S run() { return nextService(); }
                };
                return AccessController.doPrivileged(action, acc);
            }
}

private S nextService() {
 ...
    try {
      // 使用loader记载
        c = Class.forName(cn, false, loader);
    } catch (ClassNotFoundException x) {
        fail(service,
             "Provider " + cn + " not found");
    }
...        
}
```

### Dubbo中SPI的增强实现

Dubbo 的扩展点加载机制是基于 JDK 标准的 SPI 扩展点发现机制增强而来的，Dubbo 解决了 JDK 标准的 SPI 的以下问题：

- JDK 标准的 SPI 会一次性实例化扩展点所有实现，如果有扩展实现初始化很耗时，但如果没用上也加载，会很浪费资源

- 如果扩展点加载失败，就失败了，给用户没有任何通知。比如：JDK 标准的 ScriptEngine，如果 Ruby ScriptEngine 因为所依赖的 jruby.jar 不存在，导致 Ruby ScriptEngine 类加载失败，这个失败原因被吃掉了，当用户执行 ruby 脚本时，会报空指针异常，而不是报 Ruby ScriptEngine 不存在

- 增加了对扩展点 IoC 和 AOP 的支持，一个扩展点可以直接 setter 注入其它扩展点，也可以对扩展点使用 wrapper 类进行增强


以服务提供者`ServiceConfig`来理解如何使用增强SPI来加载扩展接口`Protocol`的实现类：

#### 加载Protocol的实现类
ServiceConfig中:
``` 
public class ServiceConfig<T> extends AbstractServiceConfig {

    private static final long serialVersionUID = 3033787999037024738L;
    // 加载protocol
    private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
...
}
```
SPI扩展接口protocol接口定义:
``` 
@SPI("dubbo")
public interface Protocol {

    int getDefaultPort();

    @Adaptive
    <T> Exporter<T> export(Invoker<T> invoker) throws RpcException;

    @Adaptive
    <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException;


    void destroy();

}
```

ExtensionLoader 类似 JDK 标准 SPI 里面的 ServiceLoader 类，代码 `ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension()` 的作用是获取Protocol 接口的适配器类，在 Dubbo 中每个扩展接口都有一个对应的适配器类，这个适配器类是动态生成的一个类

 Protocol 扩展接口对应的适配器类的代码（动态生成的），如下：
 ``` 
 public class Protocol$Adaptive implements com.alibaba.dubbo.rpc.Protocol {
     public void destroy() {
         throw new UnsupportedOperationException(
                 "method public abstract void com.alibaba.dubbo.rpc.Protocol.destroy() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
     }
 
     public int getDefaultPort() {
         throw new UnsupportedOperationException(
                 "method public abstract int com.alibaba.dubbo.rpc.Protocol.getDefaultPort() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
     }
 
     public com.alibaba.dubbo.rpc.Invoker refer(java.lang.Class arg0, com.alibaba.dubbo.common.URL arg1)
             throws com.alibaba.dubbo.rpc.RpcException {
         if (arg1 == null)
             throw new IllegalArgumentException("url == null");
         com.alibaba.dubbo.common.URL url = arg1;
         String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
         if (extName == null)
             throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url("
                     + url.toString() + ") use keys([protocol])");
         com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader
                 .getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
         return extension.refer(arg0, arg1);
     }
 
     public com.alibaba.dubbo.rpc.Exporter export(com.alibaba.dubbo.rpc.Invoker arg0)
             throws com.alibaba.dubbo.rpc.RpcException {
         ...
         //(1)
         com.alibaba.dubbo.common.URL url = arg0.getUrl();
         String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
         if (extName == null)
             throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url("
                     + url.toString() + ") use keys([protocol])");
         //(2)
         com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader
                 .getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
         //(3)
         return extension.export(arg0);
     }
 }
 ```

所以当我们调用 protocol.export(invoker) 方法的时候实际调用的是动态生成的 Protocol$Adaptive 实例的 export(invoker) 方法，其内部代码（1）首先获取参数里面的 URL 对象，然后从 URL 对象里面获取用户设置的 Protocol 的实现类的名称，然后调用代码（2）根据名称获取具体的 Protocol 协议的实现类（后面我们会知道获取的是实现类被使用 Wrapper 类增强后的类），最后代码（3）具体调用 Protocol 协议的实现类的 export(invoker) 方法。


### 参考资料
- [JDK SPI机制详解](https://juejin.im/post/5af952fdf265da0b9e652de3)