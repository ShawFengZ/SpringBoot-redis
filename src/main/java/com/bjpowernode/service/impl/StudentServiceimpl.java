package com.bjpowernode.service.impl;

import com.bjpowernode.mapper.StudentMapper;
import com.bjpowernode.model.Student;
import com.bjpowernode.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author zxf
 * @date 2018/7/31 9:51
 */
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
