# SpringBoot-redis
This is a project show you how to use redis in SpringBoot.

# 一、Redis安装
菜鸟教程上看安装步骤；
```
bind 127.0.0.1
port:6379
password:123456
```
可视化工具使用：Redis Desktop Manager

### 启动方式
1. 在安装目录下的命令行输入：
```
redis-server.exe redis.windows.conf
```
2. 打开桌面可视化工具连接

# 二、集成步骤
### 1. 加依赖
```xml
<!-- 启动依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <version>2.0.3.RELEASE</version>
</dependency>
```

### 2. 配置连接信息
```
#redis的配置信息
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.password=123456
```

### 3. Redis Template
在配置了上面的步骤后，SpringBoot将自动配置Redis Template，在需要操作redis的类中注入redis Template.

##### 具体使用案例
```java
@Service
public class StudentServiceimpl implements StudentService {

    @Autowired
    private StudentMapper studentMapper;

    /**
     * 注入springboot自动配置号的RedisTemplate
     * */
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Override
    public List<Student> getAllStudents() {
        //查缓存
        List<Student> studentList = (List<Student>)redisTemplate.opsForValue().get("allStudents");
        if (studentList == null){
            //查询数据库
            studentList = studentMapper.selectAllStudent();
            //把数据库查询出来的redis放到数据库中
            redisTemplate.opsForValue().set("allStudents", studentList);
        }
        return studentList;
    }
}
```
此时访问会报错，说没有实现Model序列化接口；  
解决: 在需要写入redis的类上添加实现序列化
```
public class Student implements Serializable 
```
现在就可以从redis中进行读取操作。但是，由于是序列化存储，因此可读性不好。

###### 改进
对key的序列化形式进行设置：
```Java
@Override
public List<Student> getAllStudents() {
    //创建一个字符串序列化器
    RedisSerializer redisSerializer = new StringRedisSerializer();
    //设置字符串的序列化方式
    redisTemplate.setKeySerializer(redisSerializer);
    //查缓存
    List<Student> studentList = (List<Student>)redisTemplate.opsForValue().get("allStudents");
    if (studentList == null){
        //查询数据库
        studentList = studentMapper.selectAllStudent();
        //把数据库查询出来的redis放到数据库中
        redisTemplate.opsForValue().set("allStudents", studentList);
    }
    return studentList;
}
```

##### 注意
springboot帮我们注入的redisTemplate类，泛型里面只能写<String, String>或者<Object, Object>;  
<String, Object>是不对的。  

# 三、高并发时缓存穿透问题
### 1. 分析问题
源代码：
```
@Service
public class StudentServiceimpl implements StudentService {

    @Autowired
    private StudentMapper studentMapper;
    /**
     * 注入springboot自动配置号的RedisTemplate
     * */
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Override
    public List<Student> getAllStudents() {
        //创建一个字符串序列化器
        RedisSerializer redisSerializer = new StringRedisSerializer();
        //设置字符串的序列化方式
        redisTemplate.setKeySerializer(redisSerializer);
        //查缓存
        List<Student> studentList = (List<Student>)redisTemplate.opsForValue().get("allStudents");
        if (studentList == null){
            //查询数据库
            studentList = studentMapper.selectAllStudent();
            //把数据库查询出来的redis放到数据库中
            redisTemplate.opsForValue().set("allStudents", studentList);
        }
        return studentList;
    }
}
```
1. 假设有10000个人同时访问；
2. 此时的逻辑时每个人都会去缓存里看看发现没有，然后操作数据库；
3. 我们的要求：一个人写进了缓存，9999个人去缓存里找就行了避免了对mysql数据库的操作；

### 2. 解决办法
##### 1. 同步锁的方式
```
//添加synchronized
@Override
public synchronized List<Student> getAllStudents() {
    //创建一个字符串序列化器
    RedisSerializer redisSerializer = new StringRedisSerializer();
    ...
}
```
此时一次只能有一个人进入这个方法；  
问题：性能会降低。

##### 2. 细化同步锁
```
@Override
public List<Student> getAllStudents() {
    //创建一个字符串序列化器
    RedisSerializer redisSerializer = new StringRedisSerializer();
    //设置字符串的序列化方式
    redisTemplate.setKeySerializer(redisSerializer);
    //查缓存
    List<Student> studentList = (List<Student>)redisTemplate.opsForValue().get("allStudents");
    if (studentList == null){
        synchronized(this){
            //再从redis获取以下
            studentList = (List<Student>)redisTemplate.opsForValue().get("allStudents");
            if (studentList == null){
                //查询数据库
                studentList = studentMapper.selectAllStudent();
                //把数据库查询出来的redis放到数据库中
                redisTemplate.opsForValue().set("allStudents", studentList);
            }
        }
    }
    return studentList;
}
```

# 四、高并发缓存穿透问题测试
### 1. 多线程测试
```
@RestController
@RequestMapping("mybatis")
public class MybatisController {

    @Autowired
    private StudentService studentService;

    @GetMapping("/students")
    public Object student(){

        //线程，该线程调用底层查询所有学生的方法
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                studentService.getAllStudents();
            }
        };
        //多线程测试以下缓存穿透问题
        ExecutorService executorService = Executors.newFixedThreadPool(25);
        for (int i=0; i<10000; i++){
            executorService.submit(runnable);
        }

        return studentService.getAllStudents();
    }

}
```
线程和线程池的关系？

### 2. 打印具体信息
```
@Service
public class StudentServiceimpl implements StudentService {

    @Autowired
    private StudentMapper studentMapper;
    /**
     * 注入springboot自动配置号的RedisTemplate
     * */
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Override
    public List<Student> getAllStudents() {
        //创建一个字符串序列化器
        RedisSerializer redisSerializer = new StringRedisSerializer();
        //设置字符串的序列化方式
        redisTemplate.setKeySerializer(redisSerializer);
        //查缓存
        List<Student> studentList = (List<Student>)redisTemplate.opsForValue().get("allStudents");
        if (studentList == null){
            synchronized(this){
                //再从redis获取以下
                studentList = (List<Student>)redisTemplate.opsForValue().get("allStudents");
                if (studentList == null){
                    System.out.println("查询的数据库！");
                    //查询数据库
                    studentList = studentMapper.selectAllStudent();
                    //把数据库查询出来的redis放到数据库中
                    redisTemplate.opsForValue().set("allStudents", studentList);
                }else {
                    System.out.println("查询的缓存！");
                }
            }
        }else {
            System.out.println("查询的缓存！");
        }
        return studentList;
    }
}
```
