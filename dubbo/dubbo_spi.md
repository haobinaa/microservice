

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
         //(1) 获取参数中的URL
         com.alibaba.dubbo.common.URL url = arg0.getUrl();
         String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
         if (extName == null)
             throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url("
                     + url.toString() + ") use keys([protocol])");
         //(2) 根据名称获取Protocol的实现类
         com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader
                 .getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
         //(3) 调用实现类的export(invoker)方法
         return extension.export(arg0);
     }
 }
 ```

所以当我们调用 protocol.export(invoker) 方法的时候实际调用的是动态生成的 Protocol$Adaptive 实例的 export(invoker) 方法，其内部代码（1）首先获取参数里面的 URL 对象，然后从 URL 对象里面获取用户设置的 Protocol 的实现类的名称，然后调用代码（2）根据名称获取具体的 Protocol 协议的实现类（后面我们会知道获取的是实现类被使用 Wrapper 类增强后的类），最后代码（3）具体调用 Protocol 协议的实现类的 export(invoker) 方法。

配器类的存在目的就相当一个分发器，根据不同的参数，委托不同的实现类来做指定的事情，Dubbo 实现上是把所有的参数封装到一 URl 对象里面，包含用户配置的参数，比如设置使用什么协议。另外这里也可以知道 Dubbo 并没有一次性加载所有扩展接口 Protocol 的实现类，而是根据 URL 里面协议类型只加载当前使用的扩展实现类

#### ExtensionLoader.getAdaptiveExtension 动态生成扩展接口的适配器类

![]()

(1)获取当前扩展接口对应的 ExtensionLoader 对象，在 Dubbo 中每个扩展接口对应着自己的 ExtensionLoader 对象，如下代码，内部通过并发 Map 来缓存扩展接口与对应的 
ExtensionLoader 的映射，其中 key 为扩展接口的 Class 对象，value 为对应的 ExtensionLoader 实例, 第一次访问某个扩展接口时候需要 new 一个对应的 ExtensionLoader 放入缓存，后面就直接从缓存获取：
``` 
public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null)
            throw new IllegalArgumentException("Extension type == null");
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type(" + type + ") is not interface!");
        }
        if (!withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type(" + type +
                    ") is not extension, because WITHOUT @" + SPI.class.getSimpleName() + " Annotation!");
        }
        // 如果缓存中有直接从缓存中获取
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
        // 缓存中没有则放入缓存
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }
    
// ==================映射关系
 private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<Class<?>, ExtensionLoader<?>>();    
```


(2) 取当前扩展接口对应的适配器对象，内部是首先获取该扩展接口所有实现类的 Class 对象（注意，这里获取的是 Class 对象，并不是对象实例）:
``` 
 @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        Object instance = cachedAdaptiveInstance.get();
        if (instance == null) {
            if (createAdaptiveInstanceError == null) {
                synchronized (cachedAdaptiveInstance) {
                    instance = cachedAdaptiveInstance.get();
                    if (instance == null) {
                        try {
                            instance = createAdaptiveExtension();
                            cachedAdaptiveInstance.set(instance);
                        } catch (Throwable t) {
                            createAdaptiveInstanceError = t;
                            throw new IllegalStateException("fail to create adaptive instance: " + t.toString(), t);
                        }
                    }
                }
            } else {
                throw new IllegalStateException("fail to create adaptive instance: " + createAdaptiveInstanceError.toString(), createAdaptiveInstanceError);
            }
        }

        return (T) instance;
}
```
这里使用使用双重检查创建 cachedAdaptiveInstance 对象，接口对应的适配器对象就保存到了这个对象里面。


(3) `createAdaptiveExtension`方法具体创建适配器对象
``` 
    private T createAdaptiveExtension() {
        try {
            return injectExtension((T) getAdaptiveExtensionClass().newInstance());
        } catch (Exception e) {
            throw new IllegalStateException("Can not create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }
```
可知首先调用了步骤（4）getAdaptiveExtensionClass().newInstance() 获取适配器对象的一个实例，然后调用步骤（7）injectExtension 方法进行扩展点相互依赖注入。

(4)getAdaptiveExtensionClass() 动态生成适配器类的 Class 对象
``` 
    private Class<?> getAdaptiveExtensionClass() {
        getExtensionClasses();
        if (cachedAdaptiveClass != null) {
            return cachedAdaptiveClass;
        }
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }
```

如上代码首先调用了步骤（5）getExtensionClasses 获取了该扩展接口所有实现类的 Class 对象，然后调用了步骤（6）createAdaptiveExtensionClass 创建具体的适配器对象的 Class 对象，createAdaptiveExtensionClass 代码如下：
``` 
 private Class<?> createAdaptiveExtensionClass() {
        String code = createAdaptiveExtensionClassCode();
        ClassLoader classLoader = findClassLoader();
        com.alibaba.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        return compiler.compile(code, classLoader);
    }
```
其中 createAdaptiveExtensionClassCode 方法是关键，该方法根据扩展接口生成其对应的适配器类的字符串代码，这里是根据 protocol 接口的代码生成对应的 Protocol$Adaptive 的字符串代码存放到变量 code 中，然后默认调用 JavassistCompiler 的 compile(code, classLoader) 根据字符串代码生成适配器的 Class 对象并返回，然后通过 getAdaptiveExtensionClass().newInstance() 创建适配器类的一个对象实例。至此扩展接口的适配器对象已经创建完毕。

(5) getExtensionClasses 加载扩展接口的所有实现， 内部最终调用了loadExtensionClasses :
``` 
    private Map<String, Class<?>> loadExtensionClasses() {
       //获取扩展接口上SPI注解
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        //是否存在注解
        if (defaultAnnotation != null) {
            String value = defaultAnnotation.value();
            if (value != null && (value = value.trim()).length() > 0) {
                String[] names = NAME_SEPARATOR.split(value);
                if (names.length > 1) {
                    throw new IllegalStateException("more than 1 default extension name on extension " + type.getName()
                            + ": " + Arrays.toString(names));
                }
                //默认实现类的名称放到cachedDefaultName
                if (names.length == 1) cachedDefaultName = names[0];
            }
        }
        //在指定目录的jar里面查找扩展点
        Map<String, Class<?>> extensionClasses = new HashMap<String, Class<?>>();
        loadFile(extensionClasses, DUBBO_INTERNAL_DIRECTORY);//META-INF/dubbo/internal/
        loadFile(extensionClasses, DUBBO_DIRECTORY);//META-INF/dubbo/
        loadFile(extensionClasses, SERVICES_DIRECTORY);//META-INF/services/
        return extensionClasses;
}
```
拿 Protocol 协议来说，这里 SPI 注解为 @SPI("dubbo")，那么这里 cachedDefaultName 就是 Dubbo。然后 META-INF/dubbo/internal/、META-INF/dubbo/、META-INF/services/ 目录下去加载具体的扩展实现类

（7）injectExtension 方法进行扩展点实现类相互依赖自动注入：
``` 
private T injectExtension(T instance) {
        try {
            if (objectFactory != null) {
                //遍历扩展点实现类所有的方法
                for (Method method : instance.getClass().getMethods()) {
                   //当前方法是public的set方法，并且只有一个入参
                    if (method.getName().startsWith("set")
                            && method.getParameterTypes().length == 1
                            && Modifier.isPublic(method.getModifiers())) {
                        //获取参数类型
                        Class<?> pt = method.getParameterTypes()[0];
                        try {
                            String property = method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
                           //看set方法设置的变量是不是扩展接口
                            Object object = objectFactory.getExtension(pt, property);
                            //如果是则反射调用set方法
                            if (object != null) {
                                method.invoke(instance, object);
                            }
                        } catch (Exception e) {
                            logger.error("fail to inject via method " + method.getName()
                                    + " of interface " + type.getName() + ": " + e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return instance;
    }
```
### 参考资料
- [JDK SPI机制详解](https://juejin.im/post/5af952fdf265da0b9e652de3)