package com.bjpowernode.controller;

import com.bjpowernode.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author zxf
 * @date 2018/7/31 10:08
 */
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
